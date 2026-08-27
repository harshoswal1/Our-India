package com.ourindia.app.data.local.dao

import androidx.room.*
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════════
// Party Structure DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface PartyNodeDao {

    @Query("SELECT * FROM party_structure WHERE partyName = :party ORDER BY level, holderName")
    fun observeByParty(party: String): Flow<List<PartyNodeEntity>>

    @Query("SELECT * FROM party_structure WHERE parentId IS NULL ORDER BY partyName")
    fun observeRootNodes(): Flow<List<PartyNodeEntity>>

    @Query("SELECT * FROM party_structure WHERE parentId = :parentId")
    fun observeChildren(parentId: String): Flow<List<PartyNodeEntity>>

    @Query("SELECT DISTINCT partyName FROM party_structure ORDER BY partyName")
    fun observePartyNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<PartyNodeEntity>)

    @Query("DELETE FROM party_structure")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(nodes: List<PartyNodeEntity>) {
        deleteAll()
        insertAll(nodes)
    }
}

// ═══════════════════════════════════════════════════════════════════
// Sync Metadata DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE id = :id")
    suspend fun deleteById(id: String)
}

