package com.ourindia.app.data.local.dao

import androidx.room.*
import com.ourindia.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════════
// Grievance DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface GrievanceDao {

    @Query("SELECT * FROM grievances WHERE userId = :userId ORDER BY createdAt DESC LIMIT 20")
    fun observeMyGrievances(userId: String): Flow<List<GrievanceEntity>>

    @Query("""
        SELECT * FROM grievances 
        WHERE lat BETWEEN :minLat AND :maxLat 
          AND lng BETWEEN :minLng AND :maxLng 
        ORDER BY upvoteCount DESC LIMIT 50
    """)
    fun observeNearbyGrievances(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double
    ): Flow<List<GrievanceEntity>>

    @Query("SELECT * FROM grievances WHERE id = :id")
    suspend fun getById(id: String): GrievanceEntity?

    @Upsert
    suspend fun upsert(grievance: GrievanceEntity)

    @Upsert
    suspend fun upsertAll(grievances: List<GrievanceEntity>)

    @Query("SELECT * FROM grievances WHERE isLocalDraft = 1 AND syncedAt IS NULL")
    suspend fun getPendingDrafts(): List<GrievanceEntity>

    @Query("DELETE FROM grievances WHERE isLocalDraft = 0 AND syncedAt < :cutoff")
    suspend fun pruneOldCached(cutoff: Long)
}

// ═══════════════════════════════════════════════════════════════════
// Cached Issues DAO (News Map)
// ═══════════════════════════════════════════════════════════════════
@Dao
interface CachedIssueDao {

    @Query("SELECT * FROM cached_issues ORDER BY severity DESC, createdAt DESC LIMIT 500")
    fun observeAll(): Flow<List<CachedIssueEntity>>

    @Query("SELECT * FROM cached_issues WHERE district = :district ORDER BY severity DESC")
    fun observeByDistrict(district: String): Flow<List<CachedIssueEntity>>

    @Query("""
        SELECT * FROM cached_issues 
        WHERE category = :category 
        ORDER BY severity DESC LIMIT 200
    """)
    fun observeByCategory(category: String): Flow<List<CachedIssueEntity>>

    @Upsert
    suspend fun upsertAll(issues: List<CachedIssueEntity>)

    @Query("DELETE FROM cached_issues WHERE expiresAt < :now")
    suspend fun pruneExpired(now: Long)

    @Query("SELECT COUNT(*) FROM cached_issues")
    suspend fun count(): Int
}

// ═══════════════════════════════════════════════════════════════════
// Legal Cache DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface LegalCacheDao {

    @Query("SELECT * FROM legal_cache WHERE queryHash = :hash AND expiresAt > :now LIMIT 1")
    suspend fun findCachedAnswer(hash: String, now: Long): LegalCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LegalCacheEntity)

    @Query("DELETE FROM legal_cache WHERE expiresAt < :now")
    suspend fun pruneExpired(now: Long)

    @Query("SELECT * FROM legal_cache ORDER BY expiresAt DESC LIMIT 20")
    fun observeRecentQueries(): Flow<List<LegalCacheEntity>>
}

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
// Leaders DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface LeaderDao {

    @Query("SELECT * FROM leaders WHERE district = :district ORDER BY role")
    fun observeByDistrict(district: String): Flow<List<LeaderEntity>>

    @Query("SELECT * FROM leaders ORDER BY role, name")
    fun observeAll(): Flow<List<LeaderEntity>>

    @Upsert
    suspend fun upsertAll(leaders: List<LeaderEntity>)

    @Query("DELETE FROM leaders")
    suspend fun deleteAll()
}

// ═══════════════════════════════════════════════════════════════════
// Offline Queue DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface OfflineQueueDao {

    @Query("SELECT * FROM offline_queue WHERE expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getPending(now: Long): List<OfflineActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: OfflineActionEntity)

    @Delete
    suspend fun delete(action: OfflineActionEntity)

    @Query("UPDATE offline_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: String)

    @Query("DELETE FROM offline_queue WHERE expiresAt < :now")
    suspend fun pruneExpired(now: Long)

    @Query("SELECT COUNT(*) FROM offline_queue")
    fun observeCount(): Flow<Int>
}

// ═══════════════════════════════════════════════════════════════════
// Election Results DAO
// ═══════════════════════════════════════════════════════════════════
@Dao
interface ElectionResultDao {

    @Query("SELECT * FROM election_results ORDER BY state, constituency, votes DESC")
    fun observeAll(): Flow<List<ElectionResultEntity>>

    @Query("SELECT * FROM election_results WHERE state = :state ORDER BY constituency, votes DESC")
    fun observeByState(state: String): Flow<List<ElectionResultEntity>>

    @Upsert
    suspend fun upsertAll(results: List<ElectionResultEntity>)

    @Query("DELETE FROM election_results")
    suspend fun deleteAll()
}
