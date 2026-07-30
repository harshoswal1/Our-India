package com.ourindia.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached civic issues from the news scraping pipeline */
@Entity(
    tableName = "cached_issues",
    indices = [Index("district"), Index("expiresAt")]
)
data class CachedIssueEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val severity: Int,
    val district: String,
    val state: String,
    val lat: Double,
    val lng: Double,
    val responsibleDept: String,
    val sourceUrl: String,
    val sourceName: String,
    val upvoteCount: Int,
    val createdAt: Long,
    val expiresAt: Long // 7-day TTL
)

/** User-submitted grievances + nearby grievances cache */
@Entity(
    tableName = "grievances",
    indices = [Index("userId"), Index("status")]
)
data class GrievanceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val category: String,
    val severity: Int,
    val lat: Double,
    val lng: Double,
    val district: String,
    val state: String,
    val photoUrls: String, // JSON array serialized
    val upvoteCount: Int,
    val status: String, // SUBMITTED | VERIFIED | IN_PROGRESS | RESOLVED | REJECTED
    val assignedDepartment: String?,
    val isLocalDraft: Boolean = false,
    val syncedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** Cached legal Q&A pairs (30-day TTL) */
@Entity(
    tableName = "legal_cache",
    indices = [Index("queryHash")]
)
data class LegalCacheEntity(
    @PrimaryKey val id: String,
    val queryHash: String, // SHA-256 for deduplication
    val queryText: String,
    val responseText: String,
    val citations: String, // JSON array
    val confidence: Int, // 0-100
    val expiresAt: Long
)

/** Political party organizational tree nodes */
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

/** Area-based elected leaders and officials */
@Entity(
    tableName = "leaders",
    indices = [Index("constituency"), Index("role")]
)
data class LeaderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val party: String,
    val role: String, // MP | MLA | CORPORATOR | MAYOR | COMMISSIONER
    val constituency: String,
    val state: String,
    val district: String,
    val ward: String?,
    val phone: String?,
    val email: String?,
    val photoUrl: String?,
    val attendance: Float?, // percentage
    val lastUpdated: String
)

/** Offline action queue for sync when connectivity returns */
@Entity(
    tableName = "offline_queue",
    indices = [Index("createdAt")]
)
data class OfflineActionEntity(
    @PrimaryKey val id: String,
    val actionType: String, // SUBMIT_GRIEVANCE | UPVOTE
    val payload: String, // JSON of the request body
    val retryCount: Int = 0,
    val createdAt: Long,
    val expiresAt: Long // 24-hour expiry
)

/** Election tracker data cache */
@Entity(
    tableName = "election_results",
    indices = [Index("state"), Index("constituency")]
)
data class ElectionResultEntity(
    @PrimaryKey val id: String,
    val electionName: String, // "Lok Sabha 2024"
    val state: String,
    val constituency: String,
    val candidateName: String,
    val party: String,
    val votes: Int,
    val isLeading: Boolean,
    val margin: Int,
    val roundsCompleted: Int,
    val totalRounds: Int,
    val status: String, // COUNTING | DECLARED
    val lastUpdated: Long
)
