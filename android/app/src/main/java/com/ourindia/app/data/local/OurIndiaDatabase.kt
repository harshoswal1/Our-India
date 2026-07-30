package com.ourindia.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ourindia.app.data.local.dao.*
import com.ourindia.app.data.local.entity.*

@Database(
    entities = [
        CachedIssueEntity::class,
        GrievanceEntity::class,
        LegalCacheEntity::class,
        PartyNodeEntity::class,
        LeaderEntity::class,
        OfflineActionEntity::class,
        ElectionResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OurIndiaDatabase : RoomDatabase() {
    abstract fun grievanceDao(): GrievanceDao
    abstract fun cachedIssueDao(): CachedIssueDao
    abstract fun legalCacheDao(): LegalCacheDao
    abstract fun partyNodeDao(): PartyNodeDao
    abstract fun leaderDao(): LeaderDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun electionResultDao(): ElectionResultDao
}
