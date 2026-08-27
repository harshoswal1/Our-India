package com.ourindia.app.data.repository

import com.ourindia.app.BuildConfig
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.dao.SyncMetadataDao
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSyncRepositoryImpl @Inject constructor(
    private val partyNodeDao: PartyNodeDao,
    private val syncMetadataDao: SyncMetadataDao
) : PoliticalSyncRepository {

    private val client = OkHttpClient()
    private val _lastSyncTimeFlow = MutableStateFlow(0L)

    init {
        // Initialize last sync time from DB
        CoroutineScope(Dispatchers.IO).launch {
            val state = getSyncState()
            if (state != null) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(state.lastSyncTimestamp)
                    if (date != null) {
                        _lastSyncTimeFlow.value = date.time
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse initial sync timestamp")
                }
            }
        }
    }

    override suspend fun performDeltaSync(): Result<Unit> = withContext(Dispatchers.IO) {
        val lastState = getSyncState()
        val lastTimestamp = lastState?.lastSyncTimestamp ?: "2026-08-01 00:00:00"

        // Build Supabase REST request filtering by last updated date if available
        // URL format: https://<project>.supabase.co/rest/v1/party_structure
        val url = if (BuildConfig.SUPABASE_URL.endsWith("/")) {
            "${BuildConfig.SUPABASE_URL}party_structure"
        } else {
            "${BuildConfig.SUPABASE_URL}/party_structure"
        }

        // Query changes newer than last sync timestamp using postgrest syntax
        val requestUrl = "$url?versionDate=gt.$lastTimestamp"

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Supabase returned error: ${response.code} - ${response.message}")
                    // Graceful offline/migration fallback
                    return@withContext Result.failure(Exception("Supabase HTTP Error: ${response.code}"))
                }

                val bodyString = response.body?.string() ?: "[]"
                val parsedNodes = parsePartyNodesJson(bodyString)

                if (parsedNodes.isNotEmpty()) {
                    partyNodeDao.insertAll(parsedNodes)
                    Timber.i("Successfully synced ${parsedNodes.size} nodes from Supabase")
                }

                // Update sync metadata
                val currentTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val newMetadata = SyncMetadataEntity(
                    id = "sync_state",
                    lastSyncTimestamp = currentTimestamp,
                    schemaVersion = 2,
                    dataVersion = lastState?.dataVersion?.plus(1) ?: 1,
                    recordVersion = lastState?.recordVersion?.plus(parsedNodes.size) ?: parsedNodes.size,
                    updatedAt = currentTimestamp
                )
                syncMetadataDao.upsert(newMetadata)
                _lastSyncTimeFlow.value = System.currentTimeMillis()

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.w(e, "Supabase Sync failed. Operating in offline cache mode.")
            // Graceful offline fallback
            Result.failure(e)
        }
    }

    override fun observeLastSyncTime(): Flow<Long> {
        return _lastSyncTimeFlow.asStateFlow()
    }

    override suspend fun getSyncState(): SyncMetadataEntity? = withContext(Dispatchers.IO) {
        syncMetadataDao.getById("sync_state")
    }

    override suspend fun triggerForceReSync(): Result<Unit> = withContext(Dispatchers.IO) {
        // Clear metadata and run a clean sync pulling all data
        try {
            syncMetadataDao.deleteById("sync_state")
            performDeltaSync()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePartyNodesJson(jsonStr: String): List<PartyNodeEntity> {
        val list = mutableListOf<PartyNodeEntity>()
        try {
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
            for (element in jsonArray) {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val partyName = obj["partyName"]?.jsonPrimitive?.content ?: ""
                val level = obj["level"]?.jsonPrimitive?.content ?: "NATIONAL"
                val parentId = obj["parentId"]?.jsonPrimitive?.content
                val roleTitle = obj["roleTitle"]?.jsonPrimitive?.content ?: ""
                val holderName = obj["holderName"]?.jsonPrimitive?.content ?: ""
                val state = obj["state"]?.jsonPrimitive?.content
                val district = obj["district"]?.jsonPrimitive?.content
                val versionDate = obj["versionDate"]?.jsonPrimitive?.content ?: "2026-08-01"

                list.add(
                    PartyNodeEntity(
                        id = id,
                        partyName = partyName,
                        level = level,
                        parentId = parentId,
                        roleTitle = roleTitle,
                        holderName = holderName,
                        state = state,
                        district = district,
                        versionDate = versionDate
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing party node sync json")
        }
        return list
    }
}
