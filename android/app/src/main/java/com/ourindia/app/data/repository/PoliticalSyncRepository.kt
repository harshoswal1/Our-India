package com.ourindia.app.data.repository

import com.ourindia.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the synchronization repository between Supabase (Cloud) and Room (Local Cache).
 * Integrates incremental delta synchronizations.
 */
interface PoliticalSyncRepository {
    
    /**
     * Performs a lightweight delta sync by sending the local lastSyncTimestamp
     * and applying retrieved change sets (inserts, updates, deactivations) to Room.
     */
    suspend fun performDeltaSync(): Result<Unit>
    
    /** Observes the timestamp of the last successful synchronization */
    fun observeLastSyncTime(): Flow<Long>
    
    /** Fetches the active synchronization status */
    suspend fun getSyncState(): SyncMetadataEntity?
    
    /** Forces a full sync by wiping Room tables and pulling canonical datasets */
    suspend fun triggerForceReSync(): Result<Unit>
}
