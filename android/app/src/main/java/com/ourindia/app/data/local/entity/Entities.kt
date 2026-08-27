package com.ourindia.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Legacy/Active: Political party organizational tree nodes mapping visual states */
@Entity(
    tableName = "party_structure",
    indices = [Index("partyName"), Index("parentId")]
)
data class PartyNodeEntity(
    @PrimaryKey val id: String,
    val partyName: String,
    val level: String, // NATIONAL | STATE | DISTRICT | WARD
    val parentId: String?,
    val roleTitle: String,
    val holderName: String,
    val state: String?,
    val district: String?,
    val versionDate: String // "2026-08-01"
)

/** Domain A: Political Party catalog */
@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val officialName: String,
    val shortName: String,
    val symbol: String,
    val color: String,
    val foundedYear: Int,
    val headquarters: String,
    val status: String, // ACTIVE | INACTIVE
    val metadata: String? // JSON metadata extension
)

/** Domain B: Political Organization Units (States, Districts, Mandals, Wards, Booths) */
@Entity(
    tableName = "political_organization_units",
    indices = [Index("partyId"), Index("parentUnitId")]
)
data class PoliticalOrganizationUnitEntity(
    @PrimaryKey val id: String,
    val partyId: String,
    val parentUnitId: String?,
    val unitType: String, // NATIONAL | STATE | DISTRICT | CONSTITUENCY | MANDAL | WARD | BOOTH
    val officialName: String,
    val hierarchyLevel: Int,
    val geographicScope: String,
    val state: String?,
    val district: String?,
    val constituency: String?,
    val blockTalukaMandal: String?,
    val ward: String?,
    val booth: String?,
    val status: String // ACTIVE | INACTIVE
)

/** Domain C: Political Positions / Designations */
@Entity(
    tableName = "political_positions",
    indices = [Index("partyId")]
)
data class PoliticalPositionEntity(
    @PrimaryKey val id: String,
    val partyId: String,
    val organizationUnitType: String,
    val positionName: String,
    val officialTitle: String,
    val hierarchyLevel: Int,
    val positionType: String, // ELECTED | PARTY_ORGANIZATIONAL
    val description: String?,
    val sourceId: String?,
    val verificationStatus: String // VERIFIED | PENDING | CONFLICTING
)

/** Domain D: Politician profiles */
@Entity(tableName = "politicians")
data class PoliticianEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photo: String?,
    val biography: String?,
    val education: String?,
    val status: String, // ACTIVE | INACTIVE | DECEASED
    val metadata: String? // JSON metadata
)

/** Domain E: Time-aware, versioned Position Assignments (Preserves historical roles) */
@Entity(
    tableName = "political_position_assignments",
    indices = [
        Index("partyId"),
        Index("politicianId"),
        Index("positionId"),
        Index("organizationUnitId")
    ]
)
data class PoliticalPositionAssignmentEntity(
    @PrimaryKey val id: String,
    val partyId: String,
    val politicianId: String,
    val positionId: String,
    val organizationUnitId: String,
    val parentAssignmentId: String?,
    val effectiveFrom: String,
    val effectiveTo: String?,
    val isActive: Boolean,
    val verificationStatus: String // VERIFIED | PENDING | CONFLICTING
)

/** Domain F: Ingestion Source Registry */
@Entity(tableName = "source_registry")
data class SourceRegistryEntity(
    @PrimaryKey val sourceId: String,
    val sourceName: String,
    val url: String,
    val sourceType: String, // GOVERNMENT | PARTY_WEBSITE | MEDIA
    val authorityLevel: String, // LEVEL_1 | LEVEL_2 | LEVEL_3 | LEVEL_4
    val lastChecked: String?,
    val lastChanged: String?,
    val contentHash: String?,
    val status: String // ACTIVE | INACTIVE
)

/** Domain G: Layered Verification Records */
@Entity(
    tableName = "verification_records",
    indices = [Index("sourceId")]
)
data class VerificationRecordEntity(
    @PrimaryKey val id: String,
    val recordId: String, // References assignment/politician/position ID
    val sourceId: String,
    val verificationStatus: String, // VERIFIED | PENDING | CONFLICTING | STALE | REJECTED | UNKNOWN
    val verificationTimestamp: String,
    val confidence: Float,
    val reviewer: String?,
    val conflictInfo: String?,
    val notes: String?
)

/** Domain H: Reusable Geographic Nodes */
@Entity(
    tableName = "geography",
    indices = [Index("parentId")]
)
data class GeographyEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val levelType: String // STATE | DISTRICT | CONSTITUENCY | WARD
)

/** Domain I: Local Sync Delta Tracker */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: String, // e.g., "sync_state"
    val lastSyncTimestamp: String,
    val schemaVersion: Int,
    val dataVersion: Int,
    val recordVersion: Int,
    val updatedAt: String
)
