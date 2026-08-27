package com.ourindia.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ourindia.app.data.local.OurIndiaDatabase
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.dao.SyncMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `parties` (`id` TEXT NOT NULL, `officialName` TEXT NOT NULL, `shortName` TEXT NOT NULL, `symbol` TEXT NOT NULL, `color` TEXT NOT NULL, `foundedYear` INTEGER NOT NULL, `headquarters` TEXT NOT NULL, `status` TEXT NOT NULL, `metadata` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `political_organization_units` (`id` TEXT NOT NULL, `partyId` TEXT NOT NULL, `parentUnitId` TEXT, `unitType` TEXT NOT NULL, `officialName` TEXT NOT NULL, `hierarchyLevel` INTEGER NOT NULL, `geographicScope` TEXT NOT NULL, `state` TEXT, `district` TEXT, `constituency` TEXT, `blockTalukaMandal` TEXT, `ward` TEXT, `booth` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_organization_units_partyId` ON `political_organization_units` (`partyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_organization_units_parentUnitId` ON `political_organization_units` (`parentUnitId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `political_positions` (`id` TEXT NOT NULL, `partyId` TEXT NOT NULL, `organizationUnitType` TEXT NOT NULL, `positionName` TEXT NOT NULL, `officialTitle` TEXT NOT NULL, `hierarchyLevel` INTEGER NOT NULL, `positionType` TEXT NOT NULL, `description` TEXT, `sourceId` TEXT, `verificationStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_positions_partyId` ON `political_positions` (`partyId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `politicians` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `photo` TEXT, `biography` TEXT, `education` TEXT, `status` TEXT NOT NULL, `metadata` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `political_position_assignments` (`id` TEXT NOT NULL, `partyId` TEXT NOT NULL, `politicianId` TEXT NOT NULL, `positionId` TEXT NOT NULL, `organizationUnitId` TEXT NOT NULL, `parentAssignmentId` TEXT, `effectiveFrom` TEXT NOT NULL, `effectiveTo` TEXT, `isActive` INTEGER NOT NULL, `verificationStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_position_assignments_partyId` ON `political_position_assignments` (`partyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_position_assignments_politicianId` ON `political_position_assignments` (`politicianId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_position_assignments_positionId` ON `political_position_assignments` (`positionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_political_position_assignments_organizationUnitId` ON `political_position_assignments` (`organizationUnitId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `source_registry` (`sourceId` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `url` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `authorityLevel` TEXT NOT NULL, `lastChecked` TEXT, `lastChanged` TEXT, `contentHash` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`sourceId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `verification_records` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `politicianId` TEXT, `positionAssignmentId` TEXT, `organizationUnitId` TEXT, `verificationStatus` TEXT NOT NULL, `verificationTimestamp` TEXT NOT NULL, `confidence` REAL NOT NULL, `reviewer` TEXT, `conflictInfo` TEXT, `notes` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_verification_records_sourceId` ON `verification_records` (`sourceId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `geography` (`id` TEXT NOT NULL, `parentId` TEXT, `name` TEXT NOT NULL, `levelType` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_geography_parentId` ON `geography` (`parentId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_metadata` (`id` TEXT NOT NULL, `lastSyncTimestamp` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `dataVersion` INTEGER NOT NULL, `recordVersion` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOurIndiaDatabase(
        @ApplicationContext context: Context
    ): OurIndiaDatabase {
        return Room.databaseBuilder(
            context,
            OurIndiaDatabase::class.java,
            "our_india_db"
        )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    @Provides
    fun providePartyNodeDao(database: OurIndiaDatabase): PartyNodeDao {
        return database.partyNodeDao()
    }

    @Provides
    fun provideSyncMetadataDao(database: OurIndiaDatabase): SyncMetadataDao {
        return database.syncMetadataDao()
    }
}
