package com.ourindia.app.data.civic

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Signed-in NagrikVoice user. Browsing needs no account; reporting and supporting do. */
@Singleton
class CivicSession @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
) {
    private val prefs = context.getSharedPreferences("nagrikvoice_session", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow(
        prefs.getString(KEY_USER, null)?.let { runCatching { json.decodeFromString<CivicUser>(it) }.getOrNull() }
    )
    val user: StateFlow<CivicUser?> = _user.asStateFlow()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    fun save(auth: AuthResponse) {
        prefs.edit()
            .putString(KEY_TOKEN, auth.token)
            .putString(KEY_USER, json.encodeToString(CivicUser.serializer(), auth.user))
            .apply()
        _user.value = auth.user
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
        _user.value = null
        // Keep municipal corp across sign-out — it's about location, not account
    }

    /** User's municipal corporation city name (from registration). Null if not set. */
    private val _municipalCity = MutableStateFlow(prefs.getString(KEY_MUNICIPAL_CITY, null))
    val municipalCity: StateFlow<String?> = _municipalCity.asStateFlow()

    fun setMunicipalCity(city: String?) {
        prefs.edit().apply {
            if (city == null) remove(KEY_MUNICIPAL_CITY) else putString(KEY_MUNICIPAL_CITY, city)
        }.apply()
        _municipalCity.value = city
    }

    /** City whose reports the Issues feed shows; null = every city. Kept across sign-outs. */
    private val _city = MutableStateFlow(
        prefs.getString(KEY_CITY, null)?.let { runCatching { json.decodeFromString<Authority>(it) }.getOrNull() }
    )
    val city: StateFlow<Authority?> = _city.asStateFlow()

    fun setCity(a: Authority?) {
        prefs.edit().apply {
            if (a == null) remove(KEY_CITY) else putString(KEY_CITY, json.encodeToString(Authority.serializer(), a))
        }.apply()
        _city.value = a
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
        const val KEY_CITY = "city"
        const val KEY_MUNICIPAL_CITY = "municipal_city"
    }
}

@Singleton
class CivicRepository @Inject constructor(
    private val api: CivicApi,
    private val session: CivicSession,
    private val json: Json
) {
    val user: StateFlow<CivicUser?> get() = session.user

    suspend fun signIn(email: String, password: String) = call {
        api.login(LoginRequest(email.trim(), password)).also(session::save)
    }

    suspend fun register(name: String, email: String, password: String, phone: String?, municipalCity: String? = null) = call {
        api.register(RegisterRequest(name.trim(), email.trim(), password, phone?.trim()?.ifBlank { null }))
            .also { auth ->
                session.save(auth)
                if (!municipalCity.isNullOrBlank()) session.setMunicipalCity(municipalCity)
            }
    }

    fun signOut() = session.signOut()

    val city: StateFlow<Authority?> get() = session.city
    fun setCity(a: Authority?) = session.setCity(a)

    val municipalCity: StateFlow<String?> get() = session.municipalCity

    suspend fun feed(filter: String?, category: String?, lat: Double?, lng: Double?, authorityId: String? = null, offset: Int = 0) =
        call { api.feed(filter = filter, category = category, authorityId = authorityId, latitude = lat, longitude = lng, offset = offset).complaints }

    suspend fun reportAbuse(id: String, reason: String) = call { api.reportAbuse(id, mapOf("reason" to reason)) }

    suspend fun detail(id: String) = call { api.detail(id) }

    suspend fun duplicates(lat: Double, lng: Double, category: String, subtype: String?) =
        call { api.checkDuplicate(lat, lng, category, subtype).candidates }

    suspend fun create(request: CreateComplaintRequest) = call { api.create(request).complaint }

    suspend fun toggleSupport(id: String) = call { api.toggleSupport(id) }

    suspend fun claimResolution(id: String, evidence: String) = call { api.claimResolution(id, ClaimResolutionRequest(evidence)) }

    suspend fun retryDelivery(id: String) = call { api.retryDelivery(id) }

    suspend fun rankings() = call { api.rankings().let { r -> r.copy(rankings = dedupe(r.rankings)) } }

    // Home, Report and Issues all ask on open; one fetch per process spares the cold server.
    private var authoritiesCache: List<Authority>? = null

    suspend fun authorities() = authoritiesCache?.let { Result.success(it) }
        ?: call { dedupe(api.authorities().authorities).also { authoritiesCache = it } }

    suspend fun profile(userId: String) = call { api.profile(userId) }

    /**
     * Wraps a call so screens get one readable message. The server reports failures as
     * {"error": "..."}; the old client looked for "message" and showed a generic string.
     */
    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString<ApiError>(body) }.getOrNull()
        // The server's database is wiped on restart (free hosting), so a saved login can point at
        // a deleted user: newer servers answer 401, older ones fail on the users foreign key.
        // Either way the session is dead; drop it so the app asks to sign in again.
        val msg = if (e.code() == 401 || body.contains("FOREIGN KEY", ignoreCase = true)) {
            session.signOut()
            "Your session has expired. Please sign in again."
        } else parsed?.error ?: parsed?.message ?: when (e.code()) {
            404 -> "Not found"
            else -> "The NagrikVoice server returned an error (${e.code()})"
        }
        Result.failure(CivicException(msg))
    } catch (e: IOException) {
        Result.failure(CivicException("Can't reach the NagrikVoice server. Check your connection and try again."))
    } catch (e: Exception) {
        Result.failure(CivicException(e.message ?: "Something went wrong"))
    }

    companion object {
        /** Great-circle distance; used to decide whether a location is inside an authority's area. */
        fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }

        /**
         * The server seeds Mumbai, Pune and Nashik twice (directory import + a second seed list)
         * under different ids. Names match exactly, so keep one: the busier row, else the first,
         * which is the directory-imported one with real department contacts.
         */
        fun dedupe(list: List<Authority>): List<Authority> {
            fun activity(a: Authority) = a.unresolvedCount + a.resolvedVerifiedCount + a.awaitingEmailCount + a.failedDeliveryCount
            val keep = list.groupBy { it.name.trim().lowercase() }.mapValues { (_, same) -> same.maxBy(::activity) }
            return list.filter { keep[it.name.trim().lowercase()] === it }
        }

        /**
         * Authorities with a known centre, nearest first; "covered" means within
         * [CivicConfig.COVERAGE_RADIUS_KM] of one.
         * ponytail: centre-distance stands in for real ward boundaries, so near a border the
         * nearest centre can be the wrong corporation; the report screen lets people pick.
         */
        fun byDistance(list: List<Authority>, lat: Double, lng: Double): List<Pair<Authority, Double>> =
            list.mapNotNull { a ->
                if (a.centerLat == null || a.centerLng == null) null else a to distanceKm(lat, lng, a.centerLat, a.centerLng)
            }.sortedBy { it.second }
    }
}

class CivicException(message: String) : Exception(message)
