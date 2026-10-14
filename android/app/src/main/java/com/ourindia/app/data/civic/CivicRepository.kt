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
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
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

    suspend fun register(name: String, email: String, password: String, phone: String?) = call {
        api.register(RegisterRequest(name.trim(), email.trim(), password, phone?.trim()?.ifBlank { null }))
            .also(session::save)
    }

    fun signOut() = session.signOut()

    suspend fun feed(filter: String?, category: String?, lat: Double?, lng: Double?) =
        call { api.feed(filter = filter, category = category, latitude = lat, longitude = lng).complaints }

    suspend fun detail(id: String) = call { api.detail(id) }

    suspend fun duplicates(lat: Double, lng: Double, category: String, subtype: String?) =
        call { api.checkDuplicate(lat, lng, category, subtype).candidates }

    suspend fun create(request: CreateComplaintRequest) = call { api.create(request).complaint }

    suspend fun toggleSupport(id: String) = call { api.toggleSupport(id) }

    suspend fun claimResolution(id: String, evidence: String) = call { api.claimResolution(id, ClaimResolutionRequest(evidence)) }

    suspend fun retryDelivery(id: String) = call { api.retryDelivery(id) }

    suspend fun rankings() = call { api.rankings() }

    suspend fun authorities() = call { api.authorities().authorities }

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
        val msg = parsed?.error ?: parsed?.message ?: when (e.code()) {
            401 -> "Please sign in again"
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
    }
}

class CivicException(message: String) : Exception(message)
