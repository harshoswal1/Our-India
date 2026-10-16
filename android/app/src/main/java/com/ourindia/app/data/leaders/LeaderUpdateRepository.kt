package com.ourindia.app.data.leaders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ourindia.app.BuildConfig
import com.ourindia.app.ui.partystructure.PartySeedData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reads party and state-unit infoboxes from Wikipedia and records changes in Room. */
@Singleton
class LeaderUpdateRepository @Inject constructor(private val dao: LeaderUpdateDao) {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns how many posts changed in this check. */
    suspend fun check(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val targets = WikiLeaders.targets(PartySeedData.parties.flatMap(PartySeedData::nodesFor))
            val stored = dao.all().associateBy { it.nodeId }
            val today = LocalDate.now().toString()
            val rows = targets.chunked(MAX_TITLES).flatMap { chunk ->
                val pages = fetch(chunk.map { it.title }.distinct())
                chunk.mapNotNull { t ->
                    val (resolved, text) = pages[t.title] ?: return@mapNotNull null
                    if (t.mustContain != null && !resolved.contains(t.mustContain, ignoreCase = true)) return@mapNotNull null
                    val value = WikiLeaders.infoboxValue(text, t.fields) ?: return@mapNotNull null
                    WikiLeaders.next(stored[t.nodeId], t.nodeId, resolved, value, today)
                }
            }
            dao.upsertAll(rows)
            rows.count { it.changedAt == today }
        }.onFailure { Timber.w(it, "Wikipedia leader check failed") }
    }

    /** requested title → (title after normalisation and redirects, wikitext). Missing pages are left out. */
    private fun fetch(titles: List<String>): Map<String, Pair<String, String>> {
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "revisions")
            .addQueryParameter("rvprop", "content")
            .addQueryParameter("rvslots", "main")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("titles", titles.joinToString("|"))
            .build()
        // Wikimedia asks API clients to identify themselves.
        val request = Request.Builder().url(url)
            .header("User-Agent", "OurIndia/${BuildConfig.VERSION_NAME} (Android; party leader monthly check)")
            .build()
        val body = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Wikipedia HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        return parseQuery(json.parseToJsonElement(body).jsonObject, titles)
    }

    companion object {
        /** The API's limit for content of several pages in one request. */
        const val MAX_TITLES = 50

        internal fun parseQuery(root: JsonObject, titles: List<String>): Map<String, Pair<String, String>> {
            val query = root["query"]?.jsonObject ?: return emptyMap()
            fun hops(key: String) = query[key]?.jsonArray.orEmpty().associate {
                it.jsonObject["from"]!!.jsonPrimitive.content to it.jsonObject["to"]!!.jsonPrimitive.content
            }
            val normalized = hops("normalized")
            val redirects = hops("redirects")
            val pages = query["pages"]?.jsonArray.orEmpty().mapNotNull { p ->
                val o = p.jsonObject
                if (o["missing"] != null) return@mapNotNull null
                val text = o["revisions"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("slots")?.jsonObject?.get("main")?.jsonObject?.get("content")?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                o["title"]!!.jsonPrimitive.content to text
            }.toMap()
            return titles.mapNotNull { t ->
                val n = normalized[t] ?: t
                val resolved = redirects[n] ?: n
                pages[resolved]?.let { t to (resolved to it) }
            }.toMap()
        }
    }
}

/** Monthly, on any network; the first run happens soon after install and records the baseline. */
@HiltWorker
class LeaderUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LeaderUpdateRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (repo.check().isSuccess) Result.success() else Result.retry()

    companion object {
        private const val NAME = "leader-updates"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LeaderUpdateWorker>(30, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
