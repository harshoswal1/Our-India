package com.ourindia.app.ui.civic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.civic.Authority
import com.ourindia.app.data.civic.CivicConfig
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.civic.CreateComplaintRequest
import com.ourindia.app.data.civic.DuplicateCandidate
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

/** Where the report will go, decided from the GPS fix and the server's authority list. */
sealed interface Coverage {
    data object Unknown : Coverage
    data class Covered(val authority: Authority, val distanceKm: Double) : Coverage
    data class Outside(val nearest: Authority?, val distanceKm: Double?) : Coverage
}

sealed interface ReportStage {
    data object Editing : ReportStage
    data object Checking : ReportStage
    data class Duplicate(val candidate: DuplicateCandidate) : ReportStage
    data object Sending : ReportStage
    data class Sent(val complaint: Complaint) : ReportStage
}

data class ReportUiState(
    val photo: String? = null,
    val photoTakenAt: String? = null,
    val encodingPhoto: Boolean = false,
    val location: ResolvedLocation? = null,
    val locating: Boolean = false,
    val coverage: Coverage = Coverage.Unknown,
    val sendAsTest: Boolean = false,
    val category: IssueCategory? = null,
    val subtype: String = "",
    val title: String = "",
    val description: String = "",
    val landmark: String = "",
    val stage: ReportStage = ReportStage.Editing,
    val message: String? = null
) {
    /** The authority this report would be addressed to, or null if it can't be sent yet. */
    val target: Authority?
        get() = when (val c = coverage) {
            is Coverage.Covered -> c.authority
            is Coverage.Outside -> if (sendAsTest) c.nearest else null
            Coverage.Unknown -> null
        }

    val missing: List<String>
        get() = buildList {
            if (photo == null) add("a photo")
            if (location == null) add("your location")
            if (location != null && target == null) add("a covered area (or test mode)")
            if (category == null) add("a category")
            if (title.isBlank()) add("a title")
        }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: CivicRepository,
    private val locationStore: LocationStore
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(location = locationStore.location.value))
    val state = _state.asStateFlow()
    private var authorities: List<Authority> = emptyList()

    val signedIn get() = repo.user.value != null

    init {
        viewModelScope.launch {
            repo.authorities().onSuccess { authorities = it; recomputeCoverage() }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
        recomputeCoverage()
    }

    fun onPhoto(file: File?) {
        file ?: return
        _state.update { it.copy(encodingPhoto = true) }
        viewModelScope.launch {
            val encoded = encodePhotoForUpload(context, file)
            file.delete()
            _state.update {
                if (encoded == null) it.copy(encodingPhoto = false, message = "Couldn't read that photo — please take it again")
                else it.copy(encodingPhoto = false, photo = encoded, photoTakenAt = Instant.now().toString())
            }
        }
    }

    fun setLocating(on: Boolean) = _state.update { it.copy(locating = on) }

    fun onLocation(loc: ResolvedLocation) {
        locationStore.location.value = loc
        _state.update { it.copy(location = loc, locating = false) }
        recomputeCoverage()
    }

    fun onLocationFailed(message: String) = _state.update { it.copy(locating = false, message = message) }

    fun setSendAsTest(on: Boolean) = _state.update { it.copy(sendAsTest = on) }
    fun setCategory(c: IssueCategory) = _state.update { it.copy(category = c, subtype = if (it.category == c) it.subtype else "") }
    fun setSubtype(s: String) = _state.update { it.copy(subtype = s) }
    fun setTitle(s: String) = _state.update { it.copy(title = s.take(120)) }
    fun setDescription(s: String) = _state.update { it.copy(description = s.take(1000)) }
    fun setLandmark(s: String) = _state.update { it.copy(landmark = s.take(120)) }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun recomputeCoverage() {
        val loc = _state.value.location
        val lat = loc?.latitude
        val lng = loc?.longitude
        if (lat == null || lng == null || authorities.isEmpty()) {
            _state.update { it.copy(coverage = Coverage.Unknown) }
            return
        }
        val nearest = authorities
            .mapNotNull { a -> if (a.centerLat == null || a.centerLng == null) null else a to CivicRepository.distanceKm(lat, lng, a.centerLat, a.centerLng) }
            .minByOrNull { it.second }
        val coverage = when {
            nearest == null -> Coverage.Outside(authorities.firstOrNull(), null)
            nearest.second <= CivicConfig.COVERAGE_RADIUS_KM -> Coverage.Covered(nearest.first, nearest.second)
            else -> Coverage.Outside(nearest.first, nearest.second)
        }
        _state.update { it.copy(coverage = coverage) }
    }

    /** Step 1: look for the same problem nearby so it gets support, not a duplicate. */
    fun submit() {
        val s = _state.value
        if (s.missing.isNotEmpty()) {
            _state.update { it.copy(message = "Still needed: ${s.missing.joinToString(", ")}") }
            return
        }
        val loc = s.location ?: return
        _state.update { it.copy(stage = ReportStage.Checking) }
        viewModelScope.launch {
            val dup = repo.duplicates(loc.latitude!!, loc.longitude!!, s.category!!.code, s.subtype.ifBlank { null })
                .getOrNull()?.firstOrNull()
            if (dup != null) _state.update { it.copy(stage = ReportStage.Duplicate(dup)) } else send()
        }
    }

    fun supportDuplicate(candidate: DuplicateCandidate, onDone: (String) -> Unit) {
        viewModelScope.launch {
            repo.toggleSupport(candidate.id)
                .onSuccess { _state.update { it.copy(stage = ReportStage.Editing) }; onDone(candidate.id) }
                .onFailure { e -> _state.update { it.copy(stage = ReportStage.Editing, message = e.message) } }
        }
    }

    fun sendAnyway() = viewModelScope.launch { send() }

    fun backToEditing() = _state.update { it.copy(stage = ReportStage.Editing) }

    private suspend fun send() {
        val s = _state.value
        val loc = s.location ?: return
        val authority = s.target ?: return
        val outside = s.coverage is Coverage.Outside
        _state.update { it.copy(stage = ReportStage.Sending) }
        val request = CreateComplaintRequest(
            // A report sent from outside the corporation's area is marked as a test in its own
            // text, so officials reading it can't mistake it for a local complaint.
            title = (if (outside) "[Test] " else "") + s.title.trim(),
            description = s.description.trim().ifBlank { s.title.trim() } +
                if (outside) "\n\nTest report sent from ${loc.label} (outside ${authority.name}'s area) via Our India." else "",
            category = s.category!!.code,
            subtype = s.subtype.ifBlank { null },
            photoUrl = s.photo!!,
            latitude = loc.latitude!!,
            longitude = loc.longitude!!,
            locationAccuracy = loc.accuracyMeters?.toDouble(),
            landmark = s.landmark.trim().ifBlank { null },
            locality = loc.taluka ?: loc.district ?: loc.label,
            administrativeArea = authority.name,
            authorityId = authority.key,
            cameraCaptureTimestamp = s.photoTakenAt
        )
        repo.create(request)
            .onSuccess { c -> _state.update { it.copy(stage = ReportStage.Sent(c)) } }
            .onFailure { e -> _state.update { it.copy(stage = ReportStage.Editing, message = e.message) } }
    }

    fun reset() {
        _state.value = ReportUiState(location = locationStore.location.value)
        recomputeCoverage()
    }
}
