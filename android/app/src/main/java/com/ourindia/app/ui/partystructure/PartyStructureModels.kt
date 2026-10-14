package com.ourindia.app.ui.partystructure

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.data.leaders.WikiLeaders
import com.ourindia.app.ui.theme.CivicColors

/** Active App Flow Screen */
enum class FeatureScreen {
    PARTY_SELECTOR,
    WORKSPACE,
    POLITICIAN_PROFILE
}

enum class StructureView(val label: String) { ORBIT("Orbit"), CHART("Chart") }

/** Switchable Sub-Modules within the Political Workspace */
enum class WorkspaceSubModule(val title: String, val icon: ImageVector) {
    HIERARCHY("Structure", Icons.Outlined.AccountTree),
    MAP("Map", Icons.Outlined.Map),
    ANALYTICS("Insights", Icons.Outlined.Insights)
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
    CONSTITUENCY("Constituency/Assembly Incharge", Color(0xFF6D4C9F), 20, "Constituency"),
    TALUKA_MANDAL("Taluka/Mandal/Block President", Color(0xFF6D4C9F), 21, "Taluka/Mandal"),
    TALUKA_GS("Taluka/Mandal Gen. Secretary", Color(0xFF6D4C9F), 22, "Taluka/Mandal"),
    // ── Ward / Local Tier ────────────────────────────────────────────
    WARD("Ward President", Color(0xFFB83B64), 23, "Ward"),
    WARD_GS("Ward General Secretary", Color(0xFFB83B64), 24, "Ward"),
    // ── Booth Tier ───────────────────────────────────────────────────
    BOOTH("Booth Committee President", Color(0xFF4B5563), 25, "Booth");

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
        is Verified -> "Verified"
        is PendingVerification -> "Pending verification"
        is Conflicting -> "Conflicting sources"
        is Stale -> "Stale — needs update"
        is Rejected -> "Rejected"
        is Unknown -> "Unknown"
        is NotYetFetched -> "Not yet recorded"
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
    val photoUrl: String? = null,
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

/** One party position and its (possibly unknown) holder. */
data class PartyTreeNode(
    val id: String,
    val partyName: String,
    val level: HierarchyLevel,
    val parentId: String?,
    val roleTitle: String,
    val holderName: String,
    val state: String? = null,
    val district: String? = null,
    val photoUrl: String? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.NotYetFetched,
    /** Where the holder's name comes from: the seed's citation, or a Wikipedia change. */
    val source: VerifiedSource? = null
) {
    val isNotFetched: Boolean
        get() = holderName.isBlank() ||
                holderName == WikiLeaders.VACANT ||
                holderName.contains("Not yet fetched", ignoreCase = true) ||
                holderName.contains("Leader data not yet available", ignoreCase = true)

    /** Name shown in UI — unknown holders read as an open seat, never as a placeholder string. */
    val displayName: String
        get() = when {
            holderName == WikiLeaders.VACANT -> "Vacant"
            isNotFetched -> "Not yet recorded"
            else -> holderName
        }

    val jurisdiction: String
        get() = listOfNotNull(district, state).joinToString(", ").ifEmpty { "All India" }
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
            president = "Nitin Nabin",
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
            description = "Emerged from the anti-corruption movement; governs Punjab and led Delhi from 2015 to 2025.",
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
            president = "M. A. Baby (General Secretary)",
            description = "Major left-wing national party; has governed Kerala, West Bengal and Tripura, and is now in opposition in all three.",
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
        PartyMetadata(
            name = "Shiv Sena",
            shortName = "SHS",
            color = Color(0xFFEA580C),
            symbolEmoji = "🏹",
            foundedYear = "1966",
            headquarters = "Mumbai, Maharashtra",
            president = "Eknath Shinde",
            description = "Governing regional party in Maharashtra with deep grassroots shakha networks.",
            nationalSeats = 7,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra",
            recognizedStatus = "Recognized State Party"
        ),
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
            primaryState = "Maharashtra",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Nationalist Congress Party",
            shortName = "NCP",
            color = CivicColors.NCP,
            symbolEmoji = "⏰",
            foundedYear = "1999",
            headquarters = "Mumbai, Maharashtra",
            president = "Ajit Pawar",
            description = "Major political force in Maharashtra with extensive cooperative and district networks.",
            nationalSeats = 1,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Nationalist Congress Party (SP)",
            shortName = "NCP(SP)",
            color = Color(0xFF047857),
            symbolEmoji = "🎺",
            foundedYear = "2024",
            headquarters = "Mumbai, Maharashtra",
            president = "Sharad Pawar",
            description = "Prominent political party in Western India led by veteran leader Sharad Pawar.",
            nationalSeats = 8,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Maharashtra",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Maharashtra",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All India Trinamool Congress",
            shortName = "TMC",
            color = CivicColors.Teal,
            symbolEmoji = "🌱",
            foundedYear = "1998",
            headquarters = "Kolkata, West Bengal",
            president = "Mamata Banerjee",
            description = "Major political party governing West Bengal with strong grassroots representation across eastern India.",
            nationalSeats = 29,
            statesPresent = 3,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "West Bengal",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All India Forward Bloc",
            shortName = "AIFB",
            color = Color(0xFFDC2626),
            symbolEmoji = "🦁",
            foundedYear = "1939",
            headquarters = "Kolkata, West Bengal",
            president = "Debabrata Biswas",
            description = "Left-wing nationalist political party founded by Netaji Subhas Chandra Bose.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "West Bengal",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Dravida Munnetra Kazhagam",
            shortName = "DMK",
            color = CivicColors.CivicRed,
            symbolEmoji = "☀️",
            foundedYear = "1949",
            headquarters = "Chennai, Tamil Nadu",
            president = "M. K. Stalin",
            description = "Governing state political party in Tamil Nadu with deep grassroots district and union networks.",
            nationalSeats = 22,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Desiya Murpokku Dravida Kazhagam",
            shortName = "DMDK",
            color = Color(0xFFD97706),
            symbolEmoji = "🥁",
            foundedYear = "2005",
            headquarters = "Chennai, Tamil Nadu",
            president = "Premallatha Vijayakant",
            description = "Regional political party in Tamil Nadu founded by Vijayakanth.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Naam Tamilar Katchi",
            shortName = "NTK",
            color = Color(0xFFB91C1C),
            symbolEmoji = "🌾",
            foundedYear = "1958",
            headquarters = "Chennai, Tamil Nadu",
            president = "Seeman",
            description = "Tamil nationalist political party active across Tamil Nadu and Puducherry.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Pattali Makkal Katchi",
            shortName = "PMK",
            color = Color(0xFFF59E0B),
            symbolEmoji = "🥭",
            foundedYear = "1989",
            headquarters = "Chennai, Tamil Nadu",
            president = "Anbumani Ramadoss",
            description = "State party in Tamil Nadu representing agrarian communities.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Marumalarchi Dravida Munnetra Kazhagam",
            shortName = "MDMK",
            color = Color(0xFFEF4444),
            symbolEmoji = "🔝",
            foundedYear = "1994",
            headquarters = "Chennai, Tamil Nadu",
            president = "Vaiko",
            description = "Regional Dravidian political party active across Tamil Nadu.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Tamilaga Vettri Kazhagam",
            shortName = "TVK",
            color = Color(0xFFB45309),
            symbolEmoji = "🚩",
            foundedYear = "2024",
            headquarters = "Chennai, Tamil Nadu",
            president = "C. Joseph Vijay",
            description = "Progressive regional party in Tamil Nadu focusing on social justice and youth representation.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tamil Nadu",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All India N.R. Congress",
            shortName = "AINRC",
            color = Color(0xFFF59E0B),
            symbolEmoji = "🪣",
            foundedYear = "2011",
            headquarters = "Puducherry",
            president = "N. Rangaswamy",
            description = "Ruling regional party in the Union Territory of Puducherry.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Puducherry",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Telugu Desam Party",
            shortName = "TDP",
            color = Color(0xFFEAB308),
            symbolEmoji = "🚲",
            foundedYear = "1982",
            headquarters = "Amaravati, Andhra Pradesh",
            president = "N. Chandrababu Naidu",
            description = "Governing party in Andhra Pradesh pioneering technology-driven governance.",
            nationalSeats = 16,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Andhra Pradesh",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "YSR Congress Party",
            shortName = "YSRCP",
            color = Color(0xFF0284C7),
            symbolEmoji = "🚪",
            foundedYear = "2011",
            headquarters = "Tadepalli, Andhra Pradesh",
            president = "Y. S. Jagan Mohan Reddy",
            description = "Major political party in Andhra Pradesh with extensive grassroots volunteer networks.",
            nationalSeats = 4,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Andhra Pradesh",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Andhra Pradesh",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Telangana",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All India Majlis-e-Ittehadul Muslimeen",
            shortName = "AIMIM",
            color = Color(0xFF059669),
            symbolEmoji = "🪁",
            foundedYear = "1927",
            headquarters = "Hyderabad, Telangana",
            president = "Asaduddin Owaisi",
            description = "Regional party advocating for minority empowerment and constitutional rights.",
            nationalSeats = 1,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Telangana",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Janata Dal (United)",
            shortName = "JD(U)",
            color = Color(0xFF15803D),
            symbolEmoji = "🏹",
            foundedYear = "2003",
            headquarters = "Patna, Bihar",
            president = "Nitish Kumar",
            description = "Major governing political party in Bihar focused on social development and good governance.",
            nationalSeats = 12,
            statesPresent = 3,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Rashtriya Janata Dal",
            shortName = "RJD",
            color = Color(0xFF16A34A),
            symbolEmoji = "🏮",
            foundedYear = "1997",
            headquarters = "Patna, Bihar",
            president = "Lalu Prasad Yadav",
            description = "Major political force in Bihar with strong social justice cadre and district block committees.",
            nationalSeats = 4,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Lok Janshakti Party (Ram Vilas)",
            shortName = "LJP(RV)",
            color = Color(0xFF2563EB),
            symbolEmoji = "🚁",
            foundedYear = "2021",
            headquarters = "Patna, Bihar",
            president = "Chirag Paswan",
            description = "Regional party in Bihar focusing on Dalit welfare and youth development.",
            nationalSeats = 5,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Rashtriya Lok Janshakti Party",
            shortName = "RLJP",
            color = Color(0xFF3B82F6),
            symbolEmoji = "🧵",
            foundedYear = "2021",
            headquarters = "Patna, Bihar",
            president = "Pashupati Kumar Paras",
            description = "State political party in Bihar active in parliamentary and assembly politics.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Communist Party of India (Marxist-Leninist) Liberation",
            shortName = "CPI(ML)L",
            color = Color(0xFFB91C1C),
            symbolEmoji = "🚩",
            foundedYear = "1974",
            headquarters = "Patna, Bihar",
            president = "Dipankar Bhattacharya",
            description = "Left-wing political party active in rural and working-class movements in Bihar.",
            nationalSeats = 2,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Hindustani Awam Morcha (Secular)",
            shortName = "HAM(S)",
            color = Color(0xFF047857),
            symbolEmoji = "🍳",
            foundedYear = "2015",
            headquarters = "Patna, Bihar",
            president = "Jitan Ram Manjhi",
            description = "Regional party in Bihar championing marginalized communities and rural development.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Bihar",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Samajwadi Party",
            shortName = "SP",
            color = Color(0xFFB91C1C),
            symbolEmoji = "🚲",
            foundedYear = "1992",
            headquarters = "Lucknow, Uttar Pradesh",
            president = "Akhilesh Yadav",
            description = "Major political party in Uttar Pradesh with extensive district and assembly youth networks.",
            nationalSeats = 37,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Uttar Pradesh",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Apna Dal (Soneylal)",
            shortName = "AD(S)",
            color = Color(0xFFCA8A04),
            symbolEmoji = "☕",
            foundedYear = "2016",
            headquarters = "Lucknow, Uttar Pradesh",
            president = "Anupriya Patel",
            description = "Regional party in Uttar Pradesh representing Kurmi and OBC communities.",
            nationalSeats = 2,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Uttar Pradesh",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Rashtriya Lok Dal",
            shortName = "RLD",
            color = Color(0xFF16A34A),
            symbolEmoji = "🚜",
            foundedYear = "1996",
            headquarters = "New Delhi / UP",
            president = "Jayant Chaudhary",
            description = "Regional party in Western Uttar Pradesh focused on farmers and agrarian reform.",
            nationalSeats = 2,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Uttar Pradesh",
            recognizedStatus = "Recognized State Party"
        ),
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
            primaryState = "Odisha",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Jharkhand",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All Jharkhand Students Union",
            shortName = "AJSU",
            color = Color(0xFF65A30D),
            symbolEmoji = "🍌",
            foundedYear = "1986",
            headquarters = "Ranchi, Jharkhand",
            president = "Sudesh Mahto",
            description = "State political party in Jharkhand focusing on youth mobilization and local employment.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Jharkhand",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Janta Congress Chhattisgarh",
            shortName = "JCC",
            color = Color(0xFFF97316),
            symbolEmoji = "🚜",
            foundedYear = "2016",
            headquarters = "Raipur, Chhattisgarh",
            president = "Renu Jogi",
            description = "Regional party in Chhattisgarh founded by former CM Ajit Jogi.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Chhattisgarh",
            recognizedStatus = "Recognized State Party"
        ),
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
            primaryState = "Punjab",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Indian National Lok Dal",
            shortName = "INLD",
            color = Color(0xFF15803D),
            symbolEmoji = "👓",
            foundedYear = "1996",
            headquarters = "Chandigarh, Haryana",
            president = "Abhay Singh Chautala",
            description = "State party in Haryana with rural roots among farmer and peasant communities.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Haryana",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Jannayak Janta Party",
            shortName = "JJP",
            color = Color(0xFF0D9488),
            symbolEmoji = "🔑",
            foundedYear = "2018",
            headquarters = "Chandigarh, Haryana",
            president = "Dushyant Chautala",
            description = "Regional party in Haryana focusing on youth, rural development, and employment.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Haryana",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Rashtriya Loktantrik Party",
            shortName = "RLP",
            color = Color(0xFFEA580C),
            symbolEmoji = "🍾",
            foundedYear = "2018",
            headquarters = "Jaipur, Rajasthan",
            president = "Hanuman Beniwal",
            description = "Regional political party in Rajasthan championing farmer causes and student welfare.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Rajasthan",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Bharat Adivasi Party",
            shortName = "BAP",
            color = Color(0xFF059669),
            symbolEmoji = "🏒",
            foundedYear = "2023",
            headquarters = "Dungarpur, Rajasthan",
            president = "Rajkumar Roat",
            description = "State party in Rajasthan and MP dedicated to tribal rights and local self-rule.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Rajasthan",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Janata Dal (Secular)",
            shortName = "JD(S)",
            color = Color(0xFF15803D),
            symbolEmoji = "🌾",
            foundedYear = "1999",
            headquarters = "Bengaluru, Karnataka",
            president = "H. D. Deve Gowda",
            description = "Prominent regional party in Karnataka championing agrarian and rural interests.",
            nationalSeats = 2,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Karnataka",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Communist Party of India",
            shortName = "CPI",
            color = Color(0xFFDC2626),
            symbolEmoji = "🌽",
            foundedYear = "1925",
            headquarters = "New Delhi / Kerala",
            president = "D. Raja",
            description = "Historic left-wing party with recognized state status in Kerala, Tamil Nadu, and Manipur.",
            nationalSeats = 2,
            statesPresent = 3,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Kerala",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Indian Union Muslim League",
            shortName = "IUML",
            color = Color(0xFF047857),
            symbolEmoji = "🪜",
            foundedYear = "1948",
            headquarters = "Malappuram, Kerala",
            president = "Sadiq Ali Shihab Thangal",
            description = "Prominent state party in Kerala with strong presence in legislative and civic governance.",
            nationalSeats = 3,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Kerala",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Kerala Congress",
            shortName = "KC",
            color = Color(0xFF2563EB),
            symbolEmoji = "🐎",
            foundedYear = "1964",
            headquarters = "Kottayam, Kerala",
            president = "P. J. Joseph",
            description = "Regional party in Kerala primarily representing central Travancore agrarian communities.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Kerala",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Kerala Congress (M)",
            shortName = "KC(M)",
            color = Color(0xFF0284C7),
            symbolEmoji = "📺",
            foundedYear = "1979",
            headquarters = "Kottayam, Kerala",
            president = "Jose K. Mani",
            description = "State party in Kerala with widespread representation in central Kerala local bodies.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Kerala",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Revolutionary Socialist Party",
            shortName = "RSP",
            color = Color(0xFFE11D48),
            symbolEmoji = "🔨",
            foundedYear = "1940",
            headquarters = "Kollam, Kerala",
            president = "Manoj Bhattacharya",
            description = "Left-wing political party active in Kerala and West Bengal labour movements.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Kerala",
            recognizedStatus = "Recognized State Party"
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
            primaryState = "Jammu & Kashmir",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Jammu and Kashmir Peoples Democratic Party",
            shortName = "PDP",
            color = Color(0xFF047857),
            symbolEmoji = "🖋️",
            foundedYear = "1999",
            headquarters = "Srinagar, Jammu & Kashmir",
            president = "Mehbooba Mufti",
            description = "State political party in J&K focused on regional dialogue and self-governance.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Jammu & Kashmir",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Jammu and Kashmir National Panthers Party",
            shortName = "JKNPP",
            color = Color(0xFF4338CA),
            symbolEmoji = "🚴",
            foundedYear = "1982",
            headquarters = "Jammu, Jammu & Kashmir",
            president = "Harsh Dev Singh",
            description = "Regional party in Jammu and Kashmir advocating for civic empowerment and integrity.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Jammu & Kashmir",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Maharashtrawadi Gomantak Party",
            shortName = "MGP",
            color = Color(0xFFF97316),
            symbolEmoji = "🦁",
            foundedYear = "1963",
            headquarters = "Panaji, Goa",
            president = "Deepak Dhavalikar",
            description = "Historic regional political party in Goa with deep roots in local village panchayats.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Goa",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Goa Forward Party",
            shortName = "GFP",
            color = Color(0xFF0D9488),
            symbolEmoji = "🥥",
            foundedYear = "2016",
            headquarters = "Fatorda, Goa",
            president = "Vijai Sardesai",
            description = "Regional party in Goa committed to safeguarding Goan identity and environment.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Goa",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Revolutionary Goans Party",
            shortName = "RGP",
            color = Color(0xFFEAB308),
            symbolEmoji = "⚽",
            foundedYear = "2022",
            headquarters = "Panaji, Goa",
            president = "Viresh Borkar",
            description = "Grassroots youth-driven regional party in Goa protecting rights of native Goans.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Goa",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Asom Gana Parishad",
            shortName = "AGP",
            color = Color(0xFF16A34A),
            symbolEmoji = "🐘",
            foundedYear = "1985",
            headquarters = "Guwahati, Assam",
            president = "Atul Bora",
            description = "Regional political party in Assam that emerged from the historic Assam Movement.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Assam",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "All India United Democratic Front",
            shortName = "AIUDF",
            color = Color(0xFF059669),
            symbolEmoji = "🔒",
            foundedYear = "2005",
            headquarters = "Guwahati, Assam",
            president = "Badruddin Ajmal",
            description = "Regional party in Assam working for minority welfare and rural empowerment.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Assam",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Bodoland People's Front",
            shortName = "BPF",
            color = Color(0xFFF59E0B),
            symbolEmoji = "🍊",
            foundedYear = "2005",
            headquarters = "Kokrajhar, Assam",
            president = "Hagrama Mohilary",
            description = "State political party in Assam representing the Bodoland Territorial Region.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Assam",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "United People's Party Liberal",
            shortName = "UPPL",
            color = Color(0xFFEAB308),
            symbolEmoji = "🚜",
            foundedYear = "2015",
            headquarters = "Kokrajhar, Assam",
            president = "Urkhao Gwra Brahma",
            description = "Ruling party in Bodoland Territorial Council committed to peace and development.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Assam",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Tipra Motha Party",
            shortName = "TMP",
            color = Color(0xFFF43F5E),
            symbolEmoji = "🍍",
            foundedYear = "2019",
            headquarters = "Agartala, Tripura",
            president = "Pradyot Bikram Manikya Deb Barma",
            description = "Regional party in Tripura advocating for indigenous tribal rights and Greater Tipraland.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tripura",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Indigenous People's Front of Tripura",
            shortName = "IPFT",
            color = Color(0xFF0D9488),
            symbolEmoji = "🏹",
            foundedYear = "2009",
            headquarters = "Agartala, Tripura",
            president = "Prem Kumar Reang",
            description = "Regional political party in Tripura active in indigenous tribal welfare.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Tripura",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Naga People's Front",
            shortName = "NPF",
            color = Color(0xFF2563EB),
            symbolEmoji = "🐓",
            foundedYear = "1963",
            headquarters = "Kohima, Nagaland",
            president = "Neiphiu Rio",
            description = "Major regional political party in Nagaland and Manipur representing Naga heritage.",
            nationalSeats = 0,
            statesPresent = 2,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Nagaland",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "People's Party of Arunachal",
            shortName = "PPA",
            color = Color(0xFFD97706),
            symbolEmoji = "🌽",
            foundedYear = "1977",
            headquarters = "Itanagar, Arunachal Pradesh",
            president = "Kamen Ringu",
            description = "First regional political party of Arunachal Pradesh championing state autonomy.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Arunachal Pradesh",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Sikkim Krantikari Morcha",
            shortName = "SKM",
            color = Color(0xFFDC2626),
            symbolEmoji = "🪔",
            foundedYear = "2013",
            headquarters = "Gangtok, Sikkim",
            president = "Prem Singh Tamang",
            description = "Ruling regional party in Sikkim dedicated to social welfare and inclusive progress.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Sikkim",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Sikkim Democratic Front",
            shortName = "SDF",
            color = Color(0xFF047857),
            symbolEmoji = "☂️",
            foundedYear = "1993",
            headquarters = "Gangtok, Sikkim",
            president = "Pawan Kumar Chamling",
            description = "Major political party in Sikkim with extensive 25-year governance record.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Sikkim",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Citizen Action Party – Sikkim",
            shortName = "CAP-SK",
            color = Color(0xFF0284C7),
            symbolEmoji = "💡",
            foundedYear = "2023",
            headquarters = "Gangtok, Sikkim",
            president = "Ganesh Kumar Rai",
            description = "Reform-oriented state party in Sikkim advocating transparency and anti-corruption.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Sikkim",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "United Democratic Party",
            shortName = "UDP",
            color = Color(0xFF0D9488),
            symbolEmoji = "🥁",
            foundedYear = "1997",
            headquarters = "Shillong, Meghalaya",
            president = "Metbah Lyngdoh",
            description = "Major regional party in Meghalaya governing through multi-party coalitions.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Meghalaya",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Voice of the People Party",
            shortName = "VOPP",
            color = Color(0xFF1D4ED8),
            symbolEmoji = "📢",
            foundedYear = "2021",
            headquarters = "Shillong, Meghalaya",
            president = "Ardent Miller Basaiawmoit",
            description = "Fast-growing regional party in Meghalaya championing clean governance and citizen rights.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Meghalaya",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Hill State People's Democratic Party",
            shortName = "HSPDP",
            color = Color(0xFF7C3AED),
            symbolEmoji = "🦁",
            foundedYear = "1968",
            headquarters = "Shillong, Meghalaya",
            president = "KP Pangniang",
            description = "Historic regional political party in Meghalaya rooted in tribal empowerment.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Meghalaya",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Mizo National Front",
            shortName = "MNF",
            color = Color(0xFF2563EB),
            symbolEmoji = "⭐️",
            foundedYear = "1961",
            headquarters = "Aizawl, Mizoram",
            president = "Zoramthanga",
            description = "Historic regional political party in Mizoram instrumental in state formation and governance.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Mizoram",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Zoram People's Movement",
            shortName = "ZPM",
            color = Color(0xFFE11D48),
            symbolEmoji = "🎩",
            foundedYear = "2017",
            headquarters = "Aizawl, Mizoram",
            president = "Lalduhoma",
            description = "Governing state political party in Mizoram focused on good governance and anti-corruption.",
            nationalSeats = 1,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Mizoram",
            recognizedStatus = "Recognized State Party"
        ),
        PartyMetadata(
            name = "Zoram Nationalist Party",
            shortName = "ZNP",
            color = Color(0xFF059669),
            symbolEmoji = "☀️",
            foundedYear = "1997",
            headquarters = "Aizawl, Mizoram",
            president = "H. Lalrinmawia",
            description = "Regional political party in Mizoram dedicated to sustainable mountain development.",
            nationalSeats = 0,
            statesPresent = 1,
            classification = PartyClassification.STATE_REGIONAL,
            primaryState = "Mizoram",
            recognizedStatus = "Recognized State Party"
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
    /** Parties that have structure data on this device (seeded or synced). */
    val partiesWithData: Set<String> = emptySet(),

    // Search — results are a jump list; the chart itself is never filtered
    val searchQuery: String = "",
    val searchResults: List<PartyTreeNode> = emptyList(),

    // Every position of the active party, unfiltered. Map and Insights read this.
    val allNodes: List<PartyTreeNode> = emptyList(),

    // Structure: orbit (default) or the top-down chart
    val structureView: StructureView = StructureView.ORBIT,
    val orgTree: OrgTree = OrgTree.Empty,
    /** Unit in the centre of the orbit; null = the party's root. */
    val orbitUnitId: String? = null,
    /** Last successful Wikipedia check, ISO date. */
    val leadersCheckedAt: String? = null,

    // Hierarchy chart
    val chart: HierarchyChart = HierarchyChart.Empty,
    val collapsedUnitIds: Set<String> = emptySet(),
    val focusedNodeId: String? = null,
    /** Bumped on every locate request so re-locating the same node still pans. */
    val focusRequest: Int = 0,

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
    val isSyncing: Boolean = false,
    /** One-shot snackbar text; the screen calls userMessageShown() after displaying it. */
    val userMessage: String? = null
)
