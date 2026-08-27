package com.ourindia.app.ui.partystructure

import androidx.compose.ui.graphics.Color
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.theme.CivicColors

/** Active App Flow Screen */
enum class FeatureScreen {
    PARTY_SELECTOR,
    WORKSPACE,
    POLITICIAN_PROFILE
}

/** Switchable Sub-Modules within the Political Workspace */
enum class WorkspaceSubModule(val title: String, val subtitle: String, val iconEmoji: String) {
    HIERARCHY("Hierarchy", "Virtual Canvas Tree", "🌲"),
    MAP("Map Explorer", "Geographic Drill-Down", "🗺️"),
    ANALYTICS("Analytics", "Representation & Stats", "📊")
}

/**
 * Full position-driven hierarchy levels.
 * tier = vertical ordering (lower tier = higher in hierarchy).
 * groupLabel = the broad organizational group this belongs to.
 */
enum class HierarchyLevel(val label: String, val badgeColor: Color, val tier: Int, val groupLabel: String) {
    // ── National Tier ────────────────────────────────────────────────
    NATIONAL("National Leader", CivicColors.Saffron, 1, "National"),
    NATIONAL_WORKING("Nat. Working President", CivicColors.Saffron, 2, "National"),
    NATIONAL_VP("National Vice President", CivicColors.Saffron, 3, "National"),
    NATIONAL_GS("National Gen. Secretary", CivicColors.Saffron, 4, "National"),
    NATIONAL_GS_ORG("National GS (Org.)", CivicColors.Saffron, 5, "National"),
    NATIONAL_SECRETARY("National Secretary", CivicColors.Saffron, 6, "National"),
    NATIONAL_JOINT_SECRETARY("National Joint Secretary", CivicColors.Saffron, 7, "National"),
    NATIONAL_TREASURER("National Treasurer", CivicColors.Saffron, 8, "National"),
    // ── State Tier ───────────────────────────────────────────────────
    STATE("State President", CivicColors.Navy, 9, "State"),
    STATE_WORKING("State Working President", CivicColors.Navy, 10, "State"),
    STATE_VP("State Vice President", CivicColors.Navy, 11, "State"),
    STATE_GS("State General Secretary", CivicColors.Navy, 12, "State"),
    STATE_GS_ORG("State GS (Org.)", CivicColors.Navy, 13, "State"),
    STATE_SECRETARY("State Secretary", CivicColors.Navy, 14, "State"),
    STATE_TREASURER("State Treasurer", CivicColors.Navy, 15, "State"),
    // ── District Tier ────────────────────────────────────────────────
    DISTRICT("District President", CivicColors.Teal, 16, "District"),
    DISTRICT_WORKING("District Working President", CivicColors.Teal, 17, "District"),
    DISTRICT_GS("District General Secretary", CivicColors.Teal, 18, "District"),
    DISTRICT_SECRETARY("District Secretary", CivicColors.Teal, 19, "District"),
    // ── Sub-district / Mandal / Block / Taluka Tier ──────────────────
    CONSTITUENCY("Constituency/Assembly Incharge", Color(0xFF7B2D8B), 20, "Constituency"),
    TALUKA_MANDAL("Taluka/Mandal/Block President", Color(0xFF7B2D8B), 21, "Taluka/Mandal"),
    TALUKA_GS("Taluka/Mandal Gen. Secretary", Color(0xFF7B2D8B), 22, "Taluka/Mandal"),
    // ── Ward / Local Tier ────────────────────────────────────────────
    WARD("Ward President", CivicColors.CivicRed, 23, "Ward"),
    WARD_GS("Ward General Secretary", CivicColors.CivicRed, 24, "Ward"),
    // ── Booth Tier ───────────────────────────────────────────────────
    BOOTH("Booth Committee President", Color(0xFF374151), 25, "Booth");

    companion object {
        fun fromString(value: String): HierarchyLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NATIONAL
        }
    }
}

/** Verification status for political facts */
sealed class VerificationStatus {
    object Verified : VerificationStatus()
    object PendingVerification : VerificationStatus()
    object Conflicting : VerificationStatus()
    object Stale : VerificationStatus()
    object Rejected : VerificationStatus()
    object Unknown : VerificationStatus()
    object NotYetFetched : VerificationStatus()

    fun displayLabel(): String = when (this) {
        is Verified -> "✓ Verified"
        is PendingVerification -> "⏳ Pending Verification"
        is Conflicting -> "⚠ Conflicting Sources"
        is Stale -> "🕒 Stale — Needs Update"
        is Rejected -> "✗ Rejected"
        is Unknown -> "? Unknown"
        is NotYetFetched -> "○ Not Yet Fetched"
    }
}

/** Career Timeline Milestone for Politician Profile */
data class TimelineMilestone(
    val year: String,
    val title: String,
    val description: String,
    val category: String = "Appointment" // Election, Appointment, Milestone, Policy
)

/** Verified Source Reference for Facts */
data class VerifiedSource(
    val title: String,
    val authority: String,
    val date: String,
    val referenceUrl: String? = null
)

/** Detailed Politician Profile */
data class PoliticianProfile(
    val id: String,
    val name: String,
    val roleTitle: String,
    val party: String,
    val level: HierarchyLevel,
    val state: String? = null,
    val district: String? = null,
    val ward: String? = null,
    val photoEmoji: String = "👤",
    val bio: String,
    val education: String,
    val constituency: String? = null,
    val officeAddress: String? = null,
    val timeline: List<TimelineMilestone> = emptyList(),
    val keyAchievements: List<String> = emptyList(),
    val verifiedSources: List<VerifiedSource> = emptyList(),
    val lastUpdated: String = "2026-08-01",
    val verificationStatus: VerificationStatus = VerificationStatus.NotYetFetched
)

/** UI data representation of a Party Node in tree view */
data class PartyTreeNode(
    val id: String,
    val partyName: String,
    val level: HierarchyLevel,
    val parentId: String?,
    val roleTitle: String,
    val holderName: String,
    val state: String? = null,
    val district: String? = null,
    val taluka: String? = null,
    val ward: String? = null,
    val jurisdictionLabel: String = "", // label shown on connection line
    val photoEmoji: String = "👤",
    val photoUrl: String? = null,
    val children: List<PartyTreeNode> = emptyList(),
    val isExpanded: Boolean = true,
    val verificationStatus: VerificationStatus = VerificationStatus.NotYetFetched,
    val positionOrder: Int = 0, // used for layout ordering within the same parent
    // Calculated virtual canvas layout position
    val canvasX: Float = 0f,
    val canvasY: Float = 0f
) {
    val isNotFetched: Boolean
        get() = holderName.contains("Not yet fetched", ignoreCase = true) ||
                holderName.contains("Leader data not yet available", ignoreCase = true) ||
                verificationStatus is VerificationStatus.NotYetFetched && holderName.isBlank()
}

enum class PartyClassification {
    NATIONAL,
    STATE_REGIONAL
}

/** Party Info Metadata */
data class PartyMetadata(
    val name: String,
    val shortName: String,
    val color: Color,
    val symbolEmoji: String,
    val foundedYear: String,
    val headquarters: String,
    val president: String,
    val description: String,
    val nationalSeats: Int,
    val statesPresent: Int,
    val classification: PartyClassification = PartyClassification.NATIONAL,
    val primaryState: String? = null,
    val recognizedStatus: String = "Recognized"
)

/** Geographic Representation Data */
data class StateGeoData(
    val stateName: String,
    val stateCode: String,
    val leaderCount: Int,
    val districtCount: Int,
    val topLeaderName: String,
    val topLeaderRole: String,
    val representationScore: Float, // 0.0 to 1.0 intensity
    val districts: List<DistrictGeoData> = emptyList()
)

data class DistrictGeoData(
    val districtName: String,
    val leaderCount: Int,
    val keyLeaders: List<String>
)

/** Available Parties List */
object PartyCatalog {
    val nationalParties = listOf(
        PartyMetadata(
            name = "Bharatiya Janata Party",
            shortName = "BJP",
            color = CivicColors.BJP,
            symbolEmoji = "🪷",
            foundedYear = "1980",
            headquarters = "New Delhi",
            president = "J. P. Nadda",
            description = "Major national political party in India with a nationwide organization spanning national to local booth committees.",
            nationalSeats = 240,
            statesPresent = 28,
            classification = PartyClassification.NATIONAL
        ),
        PartyMetadata(
            name = "Indian National Congress",
            shortName = "INC",
            color = CivicColors.INC,
            symbolEmoji = "✋",
            foundedYear = "1885",
            headquarters = "New Delhi",
            president = "Mallikarjun Kharge",
            description = "One of the oldest democratic political parties in the world, with historic nationwide grassroots presence.",
            nationalSeats = 99,
            statesPresent = 28,
            classification = PartyClassification.NATIONAL
        ),
        PartyMetadata(
            name = "Aam Aadmi Party",
            shortName = "AAP",
            color = CivicColors.AAP,
            symbolEmoji = "🧹",
            foundedYear = "2012",
            headquarters = "New Delhi",
            president = "Arvind Kejriwal",
            description = "Emerged from the anti-corruption movement, governing Delhi & Punjab with focus on education and healthcare.",
            nationalSeats = 3,
            statesPresent = 6,
            classification = PartyClassification.NATIONAL
        ),
        PartyMetadata(
            name = "Bahujan Samaj Party",
            shortName = "BSP",
            color = Color(0xFF1E3A8A),
            symbolEmoji = "🐘",
            foundedYear = "1984",
            headquarters = "New Delhi",
            president = "Mayawati",
            description = "National party representing Bahujans (SC, ST, OBC, religious minorities) with strong cadre organization.",
            nationalSeats = 0,
            statesPresent = 18,
            classification = PartyClassification.NATIONAL
        ),
        PartyMetadata(
            name = "Communist Party of India (Marxist)",
            shortName = "CPI(M)",
            color = Color(0xFFDC2626),
            symbolEmoji = "☭",
            foundedYear = "1964",
            headquarters = "New Delhi",
            president = "M. V. Govindan (State)",
            description = "Major left-wing national party with governance experience in Kerala, West Bengal, and Tripura.",
            nationalSeats = 4,
            statesPresent = 12,
            classification = PartyClassification.NATIONAL
        ),
        PartyMetadata(
            name = "National People's Party",
            shortName = "NPP",
            color = Color(0xFF2563EB),
            symbolEmoji = "📖",
            foundedYear = "2013",
            headquarters = "Shillong, Meghalaya",
            president = "Conrad Sangma",
            description = "First national political party from Northeast India, leading governments in Meghalaya and active across NE.",
            nationalSeats = 0,
            statesPresent = 4,
            classification = PartyClassification.NATIONAL
        )
    )

    val regionalParties = listOf(
        // Maharashtra
        PartyMetadata(
            name = "Shiv Sena (UBT)",
            shortName = "SS(UBT)",
            color = Color(0xFFF97316),
            symbolEmoji = "🔥",
            foundedYear = "1966",
            headquarters = "Mumbai, Maharashtra",
            president = "Uddhav Thackeray",
            description = "Regional party in Maharashtra rooted in Marathi pride and social welfare.",
            nationalSeats = 9,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra"
        ),
        PartyMetadata(
            name = "Shiv Sena",
            shortName = "SHS",
            color = Color(0xFFEA580C),
            symbolEmoji = "🏹",
            foundedYear = "1966",
            headquarters = "Mumbai, Maharashtra",
            president = "Eknath Shinde",
            description = "Governing party in Maharashtra with deep grassroots shakha networks.",
            nationalSeats = 7,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra"
        ),
        PartyMetadata(
            name = "Nationalist Congress Party (SP)",
            shortName = "NCP(SP)",
            color = CivicColors.NCP,
            symbolEmoji = "🎺",
            foundedYear = "1999",
            headquarters = "Mumbai, Maharashtra",
            president = "Sharad Pawar",
            description = "Prominent political force in Western India with significant cooperative and district organizational strength.",
            nationalSeats = 8,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra"
        ),
        PartyMetadata(
            name = "Maharashtra Navnirman Sena",
            shortName = "MNS",
            color = Color(0xFF9A3412),
            symbolEmoji = "🚂",
            foundedYear = "2006",
            headquarters = "Mumbai, Maharashtra",
            president = "Raj Thackeray",
            description = "Regional party focused on Maharashtra development and sons-of-the-soil advocacy.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra"
        ),

        // West Bengal
        PartyMetadata(
            name = "All India Trinamool Congress",
            shortName = "TMC",
            color = CivicColors.Teal,
            symbolEmoji = "🌱",
            foundedYear = "1998",
            headquarters = "Kolkata, West Bengal",
            president = "Mamata Banerjee",
            description = "Major political party governing West Bengal with strong grassroots representation across the eastern region.",
            nationalSeats = 29,
            statesPresent = 3,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "West Bengal"
        ),

        // Tamil Nadu
        PartyMetadata(
            name = "Dravida Munnetra Kazhagam",
            shortName = "DMK",
            color = CivicColors.CivicRed,
            symbolEmoji = "☀️",
            foundedYear = "1949",
            headquarters = "Chennai, Tamil Nadu",
            president = "M. K. Stalin",
            description = "Key state political party governing Tamil Nadu with deep grassroots district and union networks.",
            nationalSeats = 22,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu"
        ),
        PartyMetadata(
            name = "All India Anna Dravida Munnetra Kazhagam",
            shortName = "AIADMK",
            color = Color(0xFF047857),
            symbolEmoji = "🍃",
            foundedYear = "1972",
            headquarters = "Chennai, Tamil Nadu",
            president = "Edappadi K. Palaniswami",
            description = "Major political party in Tamil Nadu with historic mass base and district welfare committees.",
            nationalSeats = 0,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu"
        ),

        // Andhra Pradesh & Telangana
        PartyMetadata(
            name = "Telugu Desam Party",
            shortName = "TDP",
            color = Color(0xFFEAB308),
            symbolEmoji = "🚲",
            foundedYear = "1982",
            headquarters = "Amaravati, Andhra Pradesh",
            president = "N. Chandrababu Naidu",
            description = "Governing party in Andhra Pradesh pioneering technology-driven governance and strong district networks.",
            nationalSeats = 16,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Andhra Pradesh"
        ),
        PartyMetadata(
            name = "YSR Congress Party",
            shortName = "YSRCP",
            color = Color(0xFF0284C7),
            symbolEmoji = "🚪",
            foundedYear = "2011",
            headquarters = "Tadepalli, Andhra Pradesh",
            president = "Y. S. Jagan Mohan Reddy",
            description = "Major political party in Andhra Pradesh with extensive village volunteer and mandal committees.",
            nationalSeats = 4,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Andhra Pradesh"
        ),
        PartyMetadata(
            name = "Jana Sena Party",
            shortName = "JSP",
            color = Color(0xFFDC2626),
            symbolEmoji = "🥛",
            foundedYear = "2014",
            headquarters = "Mangalagiri, Andhra Pradesh",
            president = "Pawan Kalyan",
            description = "Prominent political party in Andhra Pradesh focusing on transparency and youth empowerment.",
            nationalSeats = 2,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Andhra Pradesh"
        ),
        PartyMetadata(
            name = "Bharat Rashtra Samithi",
            shortName = "BRS",
            color = Color(0xFFEC4899),
            symbolEmoji = "🚗",
            foundedYear = "2001",
            headquarters = "Hyderabad, Telangana",
            president = "K. Chandrashekar Rao",
            description = "Key political party in Telangana instrumental in state formation with wide mandal presence.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Telangana"
        ),

        // Bihar & Uttar Pradesh
        PartyMetadata(
            name = "Janata Dal (United)",
            shortName = "JD(U)",
            color = Color(0xFF15803D),
            symbolEmoji = "🏹",
            foundedYear = "2003",
            headquarters = "Patna, Bihar",
            president = "Nitish Kumar",
            description = "Major political party in Bihar governing with focus on social development and grassroots panchayat bodies.",
            nationalSeats = 12,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar"
        ),
        PartyMetadata(
            name = "Rashtriya Janata Dal",
            shortName = "RJD",
            color = Color(0xFF16A34A),
            symbolEmoji = "🏮",
            foundedYear = "1997",
            headquarters = "Patna, Bihar",
            president = "Lalu Prasad Yadav",
            description = "Major political force in Bihar with strong social justice cadre and district-level block representation.",
            nationalSeats = 4,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar"
        ),
        PartyMetadata(
            name = "Samajwadi Party",
            shortName = "SP",
            color = Color(0xFFB91C1C),
            symbolEmoji = "🚲",
            foundedYear = "1992",
            headquarters = "Lucknow, Uttar Pradesh",
            president = "Akhilesh Yadav",
            description = "Major political party in Uttar Pradesh with extensive district, assembly, and youth wing networks.",
            nationalSeats = 37,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Uttar Pradesh"
        ),

        // Odisha & Jharkhand
        PartyMetadata(
            name = "Biju Janata Dal",
            shortName = "BJD",
            color = Color(0xFF047857),
            symbolEmoji = "🐚",
            foundedYear = "1997",
            headquarters = "Bhubaneswar, Odisha",
            president = "Naveen Patnaik",
            description = "Major regional party in Odisha with extensive women self-help group and block organization.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Odisha"
        ),
        PartyMetadata(
            name = "Jharkhand Mukti Morcha",
            shortName = "JMM",
            color = Color(0xFF15803D),
            symbolEmoji = "🏹",
            foundedYear = "1972",
            headquarters = "Ranchi, Jharkhand",
            president = "Hemant Soren",
            description = "Governing regional party in Jharkhand rooted in tribal welfare and natural resource rights.",
            nationalSeats = 3,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Jharkhand"
        ),

        // Punjab, Karnataka, J&K, Assam
        PartyMetadata(
            name = "Shiromani Akali Dal",
            shortName = "SAD",
            color = Color(0xFF1D4ED8),
            symbolEmoji = "⚖️",
            foundedYear = "1920",
            headquarters = "Chandigarh, Punjab",
            president = "Sukhbir Singh Badal",
            description = "Historic regional party in Punjab with strong rural circles and district jathas.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Punjab"
        ),
        PartyMetadata(
            name = "Janata Dal (Secular)",
            shortName = "JD(S)",
            color = Color(0xFF15803D),
            symbolEmoji = "🌾",
            foundedYear = "1999",
            headquarters = "Bengaluru, Karnataka",
            president = "H. D. Deve Gowda",
            description = "Prominent regional party in Karnataka championing agrarian interests.",
            nationalSeats = 2,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Karnataka"
        ),
        PartyMetadata(
            name = "Jammu & Kashmir National Conference",
            shortName = "JKNC",
            color = Color(0xFFDC2626),
            symbolEmoji = "🌾",
            foundedYear = "1932",
            headquarters = "Srinagar, Jammu & Kashmir",
            president = "Farooq Abdullah",
            description = "Historic political party in Jammu & Kashmir with deep provincial halqa committees.",
            nationalSeats = 2,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Jammu & Kashmir"
        )
    )

    /** All parties combined */
    val parties: List<PartyMetadata> = nationalParties + regionalParties

    fun getParty(shortName: String): PartyMetadata {
        return parties.firstOrNull { it.shortName.equals(shortName, ignoreCase = true) }
            ?: PartyMetadata(
                name = shortName,
                shortName = shortName,
                color = CivicColors.Others,
                symbolEmoji = "🏛️",
                foundedYear = "N/A",
                headquarters = "India",
                president = "N/A",
                description = "Registered political party in India.",
                nationalSeats = 0,
                statesPresent = 0
            )
    }
}

/** UI State for Party Structure Feature */
data class PartyStructureUiState(
    val currentScreen: FeatureScreen = FeatureScreen.PARTY_SELECTOR,
    val selectedParty: String = "BJP",
    val activeSubModule: WorkspaceSubModule = WorkspaceSubModule.HIERARCHY,

    // Search & Filters
    val searchQuery: String = "",
    val selectedLevelFilter: HierarchyLevel? = null,

    // Hierarchy Tree & Canvas
    val rootNodes: List<PartyTreeNode> = emptyList(),
    val allFlattenedNodes: List<PartyTreeNode> = emptyList(),
    val expandedNodeIds: Set<String> = emptySet(),
    val canvasScale: Float = 0.5f,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
    val focusedNodeId: String? = null,

    // Popups & Profile Details
    val selectedNodeDetail: PartyTreeNode? = null,
    val selectedProfile: PoliticianProfile? = null,

    // Map Sub-Module State
    val selectedState: String? = null,
    val selectedDistrict: String? = null,
    val resolvedLocation: ResolvedLocation? = null,
    val stateGeoDataList: List<StateGeoData> = emptyList(),

    // Loading & System
    val isLoading: Boolean = false,
    val isLocationLoading: Boolean = false,
    val isOfflineMode: Boolean = true,
    val totalLeadersCount: Int = 0,
    val errorMessage: String? = null
)
