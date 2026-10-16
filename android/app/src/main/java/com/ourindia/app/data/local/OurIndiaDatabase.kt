package com.ourindia.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ourindia.app.data.leaders.LeaderUpdateDao
import com.ourindia.app.data.leaders.LeaderUpdateEntity
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.dao.SyncMetadataDao
import com.ourindia.app.data.local.entity.*

@Database(
    entities = [
        PartyNodeEntity::class,
        PartyEntity::class,
        PoliticalOrganizationUnitEntity::class,
        PoliticalPositionEntity::class,
        PoliticianEntity::class,
        PoliticalPositionAssignmentEntity::class,
        SourceRegistryEntity::class,
        VerificationRecordEntity::class,
        GeographyEntity::class,
        SyncMetadataEntity::class,
        LeaderUpdateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class OurIndiaDatabase : RoomDatabase() {
    abstract fun partyNodeDao(): PartyNodeDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun leaderUpdateDao(): LeaderUpdateDao
}
