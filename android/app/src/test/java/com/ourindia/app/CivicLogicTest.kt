package com.ourindia.app

import com.ourindia.app.data.civic.AuthoritiesResponse
import com.ourindia.app.data.civic.Authority
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.civic.ComplaintDetailResponse
import com.ourindia.app.data.civic.RankingsResponse
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.di.CivicModule
import com.ourindia.app.ui.civic.Coverage
import com.ourindia.app.ui.civic.IssueCategory
import com.ourindia.app.ui.civic.ReportUiState
import com.ourindia.app.ui.civic.coverageFor
import com.ourindia.app.ui.civic.relativeTime
import com.ourindia.app.ui.civic.shortName
import com.ourindia.app.ui.civic.statusLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CivicLogicTest {
    private val json = CivicModule.provideJson()

    // Verbatim from https://citizencomplaint.onrender.com/api/v1 on 2026-10-14.
    private val rankingsJson = """{"ranking_policy":"OPTION_B_VERIFIED_DELIVERY","formula":"Unresolved Count = Confirmed Delivered Complaints - Verified Resolved Complaints","rankings":[{"authority_id":"auth_kmc_kolhapur","name":"Kolhapur Municipal Corporation","code":"KMC","state":"Maharashtra","district":"Kolhapur","unresolved_count":0,"resolved_verified_count":0,"awaiting_email_count":0,"failed_delivery_count":0,"overdue_count":0,"total_supports":0,"resolution_rate_percent":0}]}"""
    private val authoritiesJson = """{"authorities":[{"id":"auth_kmc_kolhapur","name":"Kolhapur Municipal Corporation","code":"KMC","state":"Maharashtra","district":"Kolhapur","type":"MUNICIPAL_CORPORATION","center_lat":16.705,"center_lng":74.2433,"unresolved_count":0,"resolved_verified_count":0,"total_delivered_count":0,"failed_delivery_count":0,"pending_email_count":0,"created_at":"2026-10-14T09:31:51.850Z","updated_at":"2026-10-14T09:31:51.850Z"}]}"""

    @Test
    fun parsesRealServerResponses() {
        val r = json.decodeFromString<RankingsResponse>(rankingsJson)
        assertEquals("auth_kmc_kolhapur", r.rankings.single().key)
        val a = json.decodeFromString<AuthoritiesResponse>(authoritiesJson).authorities.single()
        assertEquals("auth_kmc_kolhapur", a.key)
        assertEquals(16.705, a.centerLat!!, 1e-9)

        // SQLite rows: ints for flags, extra columns the app doesn't model, nulls.
        val detail = json.decodeFromString<ComplaintDetailResponse>(
            """{"complaint":{"id":"KMC-2026-0001","reporter_id":"u1","title":"Pothole","description":"d","category":"ROADS_AND_INFRASTRUCTURE",
               "subtype":null,"photo_url":"data:image/jpeg;base64,AAAA","latitude":16.7,"longitude":74.2,"locality":"Tarabai Park",
               "administrative_area":"Kolhapur Municipal Corporation","authority_id":"auth_kmc_kolhapur","department_id":"dep_pwd",
               "email_status":"DELIVERED","resolution_status":"UNRESOLVED","support_count":3,"is_overdue":0,"flagged_count":0,
               "is_hidden":0,"submission_timestamp":"2026-10-14T10:00:00.000Z","created_at":"2026-10-14T10:00:00.000Z","updated_at":"x",
               "authority_name":"Kolhapur Municipal Corporation","department_name":"City Engineer / PWD"},
               "history":[{"id":"h1","complaint_id":"KMC-2026-0001","previous_status":"PENDING_EMAIL","new_status":"UNRESOLVED","changed_by_user_id":null,"reason":"Delivered","timestamp":"2026-10-14T10:01:00.000Z"}],
               "resolutions":[],"deliveries":[]}"""
        )
        assertEquals(3, detail.complaint.supportCount)
        assertEquals("UNRESOLVED", detail.history.single().newStatus)
    }

    private fun complaint(email: String, resolution: String, overdue: Int = 0) = Complaint(
        id = "X", title = "t", category = "ROADS_AND_INFRASTRUCTURE", latitude = 0.0, longitude = 0.0,
        emailStatus = email, resolutionStatus = resolution, isOverdue = overdue
    )

    @Test
    fun statusLabelsNeverOverclaim() {
        assertEquals("Awaiting delivery", statusLook(complaint("PENDING", "PENDING_EMAIL")).label)
        assertEquals("Delivery failed", statusLook(complaint("FAILED", "PENDING_EMAIL")).label)
        assertEquals("Open", statusLook(complaint("DELIVERED", "UNRESOLVED")).label)
        assertEquals("Overdue", statusLook(complaint("DELIVERED", "UNRESOLVED", overdue = 1)).label)
        // A claimed fix is not a fix until verified.
        assertEquals("Fix under review", statusLook(complaint("DELIVERED", "RESOLUTION_CLAIMED")).label)
        assertEquals("Resolved", statusLook(complaint("DELIVERED", "RESOLVED_VERIFIED")).label)
        assertEquals(IssueCategory.OTHER, IssueCategory.of("SOMETHING_NEW"))
    }

    @Test
    fun reportOnlyTargetsAnAuthorityWhenCoveredOrExplicitlyTest() {
        val kmc = Authority(id = "auth_kmc_kolhapur", name = "Kolhapur Municipal Corporation", code = "KMC")
        val here = ResolvedLocation(state = "Maharashtra", district = "Pune", latitude = 18.52, longitude = 73.85)
        val base = ReportUiState(photo = "data:x", location = here, category = IssueCategory.ROADS, title = "Pothole")

        assertEquals(kmc, base.copy(coverage = Coverage.Covered(kmc, 2.0)).target)
        val outside = base.copy(coverage = Coverage.Outside(kmc, 210.0))
        assertNull(outside.target)
        assertTrue(outside.missing.any { "test" in it })
        assertEquals(kmc, outside.copy(sendAsTest = true).target)
        assertTrue(outside.copy(sendAsTest = true).missing.isEmpty())
        assertTrue(base.copy(photo = null, coverage = Coverage.Covered(kmc, 1.0)).missing.contains("a photo"))
    }

    @Test
    fun multiCityRoutingPicksNearestDedupesAndAllowsOverride() {
        // Live server on 2026-10-14 lists Pune twice (directory import + second seed).
        val pune = Authority(id = "auth_pune", name = "Pune Municipal Corporation (PMC)", centerLat = 18.5204, centerLng = 73.8567)
        val puneDup = pune.copy(id = "auth_pmc_pune")
        val pcmc = Authority(id = "auth_pimpri", name = "Pimpri-Chinchwad Municipal Corporation (PCMC)", centerLat = 18.6298, centerLng = 73.7997)
        val kmc = Authority(id = "auth_kmc_kolhapur", name = "Kolhapur Municipal Corporation (KMC)", centerLat = 16.705, centerLng = 74.2433)
        val list = CivicRepository.dedupe(listOf(kmc, pune, pcmc, puneDup))
        assertEquals(listOf("auth_kmc_kolhapur", "auth_pune", "auth_pimpri"), list.map { it.key })
        // A duplicate that actually holds reports wins over the empty one.
        assertEquals("auth_pmc_pune", CivicRepository.dedupe(listOf(pune, puneDup.copy(unresolvedCount = 2))).single().key)

        // Kothrud, Pune: both PMC and PCMC are in range; nearest first, the other offered.
        val c = coverageFor(list, 18.5074, 73.8077) as Coverage.Covered
        assertEquals("auth_pune", c.authority.key)
        assertEquals(listOf("auth_pimpri"), c.others.map { it.first.key })
        assertEquals("auth_pimpri", (coverageFor(list, 18.5074, 73.8077, chosenKey = "auth_pimpri") as Coverage.Covered).authority.key)
        // A choice that isn't in range is ignored rather than mis-routing the report.
        assertEquals("auth_pune", (coverageFor(list, 18.5074, 73.8077, chosenKey = "auth_kmc_kolhapur") as Coverage.Covered).authority.key)
        // Nagpur: nothing within range.
        assertTrue(coverageFor(list, 21.1458, 79.0882) is Coverage.Outside)

        assertEquals("Pune", pune.shortName)
        assertEquals("Satara (council)", Authority(name = "Satara Municipal Council").shortName)
        assertEquals("Municipal Corporation of Delhi (MCD)".substringBefore(" ("), Authority(name = "Municipal Corporation of Delhi (MCD)").shortName)
    }

    @Test
    fun distanceAndRelativeTime() {
        // Kolhapur to Pune is ~200 km by great circle.
        val d = CivicRepository.distanceKm(16.705, 74.2433, 18.5204, 73.8567)
        assertTrue("was $d", d in 195.0..210.0)
        val now = Instant.parse("2026-10-14T12:00:00Z")
        assertEquals("2 h ago", relativeTime("2026-10-14T10:00:00Z", now))
        assertEquals("3 d ago", relativeTime("2026-10-11T12:00:00Z", now))
        assertEquals("", relativeTime("not a date", now))
    }
}
