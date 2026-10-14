package com.ourindia.app.data.civic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the NagrikVoice backend (snake_case JSON). Unknown fields are ignored by
// the Json config in CivicModule, so server additions don't break the app.

@Serializable
data class CivicUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "CITIZEN",
    val phone: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class AuthResponse(val user: CivicUser, val token: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val role: String = "CITIZEN"
)

@Serializable
data class Complaint(
    val id: String,
    @SerialName("reporter_id") val reporterId: String = "",
    val title: String,
    val description: String = "",
    val category: String,
    val subtype: String? = null,
    @SerialName("photo_url") val photoUrl: String = "",
    val latitude: Double,
    val longitude: Double,
    @SerialName("location_accuracy") val locationAccuracy: Double? = null,
    val landmark: String? = null,
    val locality: String = "",
    @SerialName("administrative_area") val administrativeArea: String = "",
    @SerialName("authority_id") val authorityId: String = "",
    @SerialName("authority_name") val authorityName: String? = null,
    @SerialName("department_name") val departmentName: String? = null,
    @SerialName("email_status") val emailStatus: String = "PENDING",
    @SerialName("resolution_status") val resolutionStatus: String = "PENDING_EMAIL",
    @SerialName("support_count") val supportCount: Int = 0,
    @SerialName("is_overdue") val isOverdue: Int = 0,
    @SerialName("user_has_supported") val userHasSupported: Int = 0,
    @SerialName("submission_timestamp") val submissionTimestamp: String = "",
    @SerialName("delivered_timestamp") val deliveredTimestamp: String? = null,
    @SerialName("resolved_timestamp") val resolvedTimestamp: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("distance_meters") val distanceMeters: Double? = null
) {
    val supported: Boolean get() = userHasSupported == 1
    val overdue: Boolean get() = isOverdue == 1
}

@Serializable
data class FeedResponse(val complaints: List<Complaint> = emptyList(), val total: Int = 0)

@Serializable
data class StatusChange(
    val id: String = "",
    @SerialName("previous_status") val previousStatus: String = "",
    @SerialName("new_status") val newStatus: String = "",
    val reason: String = "",
    val timestamp: String = ""
)

@Serializable
data class ComplaintDetailResponse(
    val complaint: Complaint,
    val history: List<StatusChange> = emptyList()
)

@Serializable
data class DuplicateCandidate(
    val id: String,
    val title: String,
    val description: String = "",
    @SerialName("photo_url") val photoUrl: String = "",
    val category: String = "",
    val locality: String = "",
    @SerialName("distance_meters") val distanceMeters: Double = 0.0,
    @SerialName("resolution_status") val resolutionStatus: String = "",
    @SerialName("support_count") val supportCount: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class CheckDuplicateResponse(
    @SerialName("has_duplicates") val hasDuplicates: Boolean = false,
    val candidates: List<DuplicateCandidate> = emptyList()
)

@Serializable
data class CreateComplaintRequest(
    val title: String,
    val description: String,
    val category: String,
    val subtype: String? = null,
    @SerialName("photo_url") val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("location_accuracy") val locationAccuracy: Double? = null,
    val landmark: String? = null,
    val locality: String,
    @SerialName("administrative_area") val administrativeArea: String,
    @SerialName("authority_id") val authorityId: String,
    // The server has no mail provider yet; this asks it to simulate delivery. Every report
    // sent this way is labelled "demo delivery" in the app (see CivicConfig).
    @SerialName("simulate_delivery") val simulateDelivery: Boolean = CivicConfig.SIMULATED_DELIVERY,
    @SerialName("delivery_outcome") val deliveryOutcome: String = "DELIVERED",
    @SerialName("camera_capture_timestamp") val cameraCaptureTimestamp: String? = null
)

@Serializable
data class CreateComplaintResponse(val complaint: Complaint)

@Serializable
data class SupportResponse(val supported: Boolean, val supportCount: Int)

@Serializable
data class ClaimResolutionRequest(
    @SerialName("evidence_description") val evidenceDescription: String,
    @SerialName("evidence_photo_url") val evidencePhotoUrl: String? = null
)

@Serializable
data class RetryRequest(
    @SerialName("simulate_delivery") val simulateDelivery: Boolean = CivicConfig.SIMULATED_DELIVERY,
    @SerialName("delivery_outcome") val deliveryOutcome: String = "DELIVERED"
)

@Serializable
data class Authority(
    @SerialName("authority_id") val authorityId: String? = null,
    val id: String? = null,
    val name: String,
    val code: String = "",
    val state: String = "",
    val district: String = "",
    @SerialName("center_lat") val centerLat: Double? = null,
    @SerialName("center_lng") val centerLng: Double? = null,
    @SerialName("unresolved_count") val unresolvedCount: Int = 0,
    @SerialName("resolved_verified_count") val resolvedVerifiedCount: Int = 0,
    @SerialName("awaiting_email_count") val awaitingEmailCount: Int = 0,
    @SerialName("failed_delivery_count") val failedDeliveryCount: Int = 0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("total_supports") val totalSupports: Int = 0,
    @SerialName("resolution_rate_percent") val resolutionRatePercent: Double = 0.0
) {
    // /rankings calls it authority_id, /authorities calls it id.
    val key: String get() = authorityId ?: id ?: code
}

@Serializable
data class RankingsResponse(
    val formula: String = "",
    val rankings: List<Authority> = emptyList()
)

@Serializable
data class AuthoritiesResponse(val authorities: List<Authority> = emptyList())

@Serializable
data class ProfileResponse(
    val user: CivicUser,
    val submitted: List<Complaint> = emptyList(),
    val supported: List<Complaint> = emptyList()
)

@Serializable
data class ApiError(val error: String? = null, val message: String? = null)

object CivicConfig {
    /**
     * The NagrikVoice server has no mail provider wired up, so delivery is simulated on
     * request. The app keeps asking for it (product decision) but must never present a
     * simulated delivery as a real email to the corporation — UI reads this flag.
     */
    const val SIMULATED_DELIVERY = true

    /** A report is "in coverage" when it is this close to an authority's centre. */
    const val COVERAGE_RADIUS_KM = 30.0
}
