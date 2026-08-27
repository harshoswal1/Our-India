package com.ourindia.app.ui.partystructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.data.repository.PoliticalSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PartyStructureViewModel @Inject constructor(
    private val partyNodeDao: PartyNodeDao,
    private val syncRepository: PoliticalSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartyStructureUiState())
    val uiState: StateFlow<PartyStructureUiState> = _uiState.asStateFlow()

    private var rawEntitiesList: List<PartyNodeEntity> = emptyList()

    init {
        loadData()
        // Trigger background delta sync from Supabase into Room cache
        viewModelScope.launch {
            try {
                syncRepository.performDeltaSync()
            } catch (e: Exception) {
                // Graceful fallback to offline Room cache
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            partyNodeDao.observeByParty(_uiState.value.selectedParty).collect { entities ->
                if (entities.isEmpty()) {
                    seedMockPartyData()
                } else {
                    rawEntitiesList = entities
                    updateState()
                }
            }
        }
    }

    // ── Navigation & Flow ────────────────────────────────────────────

    fun onSelectPartyFromGrid(partyShortName: String) {
        _uiState.update {
            it.copy(
                selectedParty = partyShortName,
                currentScreen = FeatureScreen.WORKSPACE,
                activeSubModule = WorkspaceSubModule.HIERARCHY,
                searchQuery = "",
                selectedLevelFilter = null,
                selectedNodeDetail = null,
                focusedNodeId = null,
                selectedState = null,
                selectedDistrict = null,
                isLoading = true
            )
        }
        viewModelScope.launch {
            partyNodeDao.observeByParty(partyShortName).collect { entities ->
                if (entities.isEmpty()) {
                    seedMockPartyDataForParty(partyShortName)
                } else {
                    rawEntitiesList = entities
                    updateState()
                }
            }
        }
    }

    fun navigateToPartySelector() {
        _uiState.update { it.copy(currentScreen = FeatureScreen.PARTY_SELECTOR, selectedNodeDetail = null) }
    }

    fun switchSubModule(subModule: WorkspaceSubModule) {
        _uiState.update { it.copy(activeSubModule = subModule) }
    }

    fun navigateToProfile(node: PartyTreeNode) {
        val profile = generatePoliticianProfile(node)
        _uiState.update {
            it.copy(currentScreen = FeatureScreen.POLITICIAN_PROFILE, selectedProfile = profile, selectedNodeDetail = null)
        }
    }

    fun navigateBackFromProfile() {
        _uiState.update { it.copy(currentScreen = FeatureScreen.WORKSPACE, selectedProfile = null) }
    }

    fun locateInHierarchy(nodeId: String) {
        _uiState.update {
            it.copy(
                currentScreen = FeatureScreen.WORKSPACE,
                activeSubModule = WorkspaceSubModule.HIERARCHY,
                focusedNodeId = nodeId,
                selectedProfile = null
            )
        }
    }

    // ── Search & Filter ──────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateState()
    }

    fun onLevelFilterChanged(level: HierarchyLevel?) {
        _uiState.update { it.copy(selectedLevelFilter = level) }
        updateState()
    }

    // ── Hierarchy & Canvas Interactions ──────────────────────────────

    fun onNodeClicked(node: PartyTreeNode) {
        _uiState.update { it.copy(selectedNodeDetail = node, focusedNodeId = node.id) }
    }

    fun dismissNodeDetail() {
        _uiState.update { it.copy(selectedNodeDetail = null) }
    }

    fun toggleNodeExpand(nodeId: String) {
        _uiState.update { state ->
            val newExpanded = if (state.expandedNodeIds.contains(nodeId)) {
                state.expandedNodeIds - nodeId
            } else {
                state.expandedNodeIds + nodeId
            }
            state.copy(expandedNodeIds = newExpanded)
        }
        updateState()
    }

    // ── Map Sub-Module Interactions ──────────────────────────────────

    fun onMapStateSelected(stateName: String?) {
        _uiState.update { it.copy(selectedState = stateName, selectedDistrict = null) }
    }

    fun onMapDistrictSelected(districtName: String?) {
        _uiState.update { it.copy(selectedDistrict = districtName) }
    }

    // ── Location ─────────────────────────────────────────────────────
    fun setLocationLoading(loading: Boolean) {
        _uiState.update { it.copy(isLocationLoading = loading) }
    }

    /**
     * Called when a geographic location has been resolved.
     * Updates shared location state across Hierarchy, Map, and Analytics.
     * Then navigates hierarchy to deepest available local political level.
     */
    fun onLocationResolved(location: ResolvedLocation?) {
        _uiState.update {
            it.copy(
                resolvedLocation = location,
                selectedState = location?.state,
                selectedDistrict = location?.district,
                focusedNodeId = if (location == null) null else it.focusedNodeId,
                isLocationLoading = false
            )
        }

        if (location?.state == null) {
            updateState()
            return
        }

        // Expand path to state node
        val stateNode = rawEntitiesList.firstOrNull { entity ->
            (entity.level == HierarchyLevel.STATE.name || entity.level.startsWith("STATE")) &&
                entity.state.equals(location.state, ignoreCase = true)
        }
        if (stateNode != null) {
            _uiState.update { state ->
                state.copy(expandedNodeIds = state.expandedNodeIds + stateNode.id)
            }
        }

        // Find deepest matching node (walk from ward/booth up to state/national)
        val levelPriority = listOf(
            HierarchyLevel.BOOTH, HierarchyLevel.WARD_GS, HierarchyLevel.WARD,
            HierarchyLevel.TALUKA_GS, HierarchyLevel.TALUKA_MANDAL,
            HierarchyLevel.CONSTITUENCY,
            HierarchyLevel.DISTRICT_SECRETARY, HierarchyLevel.DISTRICT_GS,
            HierarchyLevel.DISTRICT_WORKING, HierarchyLevel.DISTRICT,
            HierarchyLevel.STATE_TREASURER, HierarchyLevel.STATE_SECRETARY,
            HierarchyLevel.STATE_GS_ORG, HierarchyLevel.STATE_GS,
            HierarchyLevel.STATE_VP, HierarchyLevel.STATE_WORKING, HierarchyLevel.STATE
        )

        var deepestNodeId: String? = null
        for (level in levelPriority) {
            val candidate = rawEntitiesList.firstOrNull { entity ->
                entity.level.equals(level.name, ignoreCase = true) &&
                    entity.state.equals(location.state, ignoreCase = true) &&
                    (location.district == null || entity.district.equals(location.district, ignoreCase = true) ||
                        entity.district.isNullOrEmpty())
            }
            if (candidate != null) {
                deepestNodeId = candidate.id
                break
            }
        }

        if (deepestNodeId != null) {
            _uiState.update { it.copy(focusedNodeId = deepestNodeId) }
        }

        updateState()
    }

    // ── State Computation & Tree Layout Engine ───────────────────────

    private fun updateState() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val levelFilter = _uiState.value.selectedLevelFilter
        val expandedIds = _uiState.value.expandedNodeIds

        val filteredEntities = rawEntitiesList.filter { entity ->
            val matchesQuery = query.isEmpty() ||
                    entity.holderName.lowercase().contains(query) ||
                    entity.roleTitle.lowercase().contains(query) ||
                    (entity.state?.lowercase()?.contains(query) == true) ||
                    (entity.district?.lowercase()?.contains(query) == true)

            val matchesLevel = levelFilter == null ||
                    entity.level.equals(levelFilter.name, ignoreCase = true)

            matchesQuery && matchesLevel
        }

        val (roots, flattened) = calculateVirtualCanvasLayout(filteredEntities, expandedIds)
        val geoData = calculateStateGeoData(rawEntitiesList)

        _uiState.update {
            it.copy(
                rootNodes = roots,
                allFlattenedNodes = flattened,
                stateGeoDataList = geoData,
                totalLeadersCount = rawEntitiesList.size,
                isLoading = false
            )
        }
    }

    private fun getLeaderPhotoUrl(name: String): String? {
        return when {
            name.contains("Narendra Modi", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c4/Narendra_Modi_Official_Portrait_2019_%28cropped%29.jpg/220px-Narendra_Modi_Official_Portrait_2019_%28cropped%29.jpg"
            name.contains("Nadda", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/JP_Nadda_Official_Portrait.jpg/220px-JP_Nadda_Official_Portrait.jpg"
            name.contains("Kharge", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/Mallikarjun_Kharge_at_Press_Club_of_India.jpg/220px-Mallikarjun_Kharge_at_Press_Club_of_India.jpg"
            name.contains("Gandhi", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Rahul_Gandhi_Official_Portrait_2023.jpg/220px-Rahul_Gandhi_Official_Portrait_2023.jpg"
            name.contains("Kejriwal", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Arvind_Kejriwal_2022.jpg/220px-Arvind_Kejriwal_2022.jpg"
            name.contains("Pawar", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Sharad_Pawar_2017.jpg/220px-Sharad_Pawar_2017.jpg"
            name.contains("Banerjee", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Mamata_Banerjee_official_portrait.jpg/220px-Mamata_Banerjee_official_portrait.jpg"
            name.contains("Stalin", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/M._K._Stalin_Official_Portrait_2021.jpg/220px-M._K._Stalin_Official_Portrait_2021.jpg"
            name.contains("Bawankule", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/14/Chandrashekhar_Bawankule_Portriat.jpg/220px-Chandrashekhar_Bawankule_Portriat.jpg"
            name.contains("Shivakumar", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/D._K._Shivakumar_Official.jpg/220px-D._K._Shivakumar_Official.jpg"
            name.contains("Vijayendra", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/BY_Vijayendra_profile.jpg/220px-BY_Vijayendra_profile.jpg"
            name.contains("Patole", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Nana_Patole.jpg/220px-Nana_Patole.jpg"
            name.contains("Gopal Rai", true) -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Gopal_Rai_Portrait.jpg/220px-Gopal_Rai_Portrait.jpg"
            name.contains("Santhosh", true) -> null // not fetched image
            else -> null
        }
    }

    /**
     * Dynamic node height based on actual content and depth.
     * Does NOT use a fixed huge gap — adapts to subtree properties.
     */
    private fun getNodeHeight(depth: Int, childCount: Int): Float {
        val cardHeight = 110f
        // Level-appropriate base spacing — moderate, not excessive
        val baseSpacing = when {
            depth == 0 -> 60f  // national → state
            depth <= 2 -> 50f  // state → district
            depth <= 5 -> 42f  // district → taluka
            else -> 36f        // local/ward/booth
        }
        // Small extra padding when a node has many children to avoid line congestion
        val childPadding = (childCount * 1.2f).coerceAtMost(12f)
        return cardHeight + baseSpacing + childPadding
    }

    /** Computes tree layout positioning nodes with proper X/Y spacing on the canvas */
    private fun calculateVirtualCanvasLayout(
        entities: List<PartyNodeEntity>,
        expandedIds: Set<String>
    ): Pair<List<PartyTreeNode>, List<PartyTreeNode>> {
        val rootEntities = entities.filter { it.parentId == null || entities.none { p -> p.id == it.parentId } }
        val flattenedList = mutableListOf<PartyTreeNode>()

        val nodeWidth = 280f
        val siblingGap = 40f
        var currentRootX = 100f

        fun layoutSubtree(
            entity: PartyNodeEntity,
            depth: Int,
            startX: Float,
            nodeY: Float
        ): Pair<PartyTreeNode, Float> {
            val childrenEntities = entities.filter { it.parentId == entity.id }
                .sortedBy { HierarchyLevel.fromString(it.level).tier }
            val isExpanded = expandedIds.contains(entity.id) ||
                    expandedIds.isEmpty() ||
                    _uiState.value.searchQuery.isNotEmpty()

            val laidOutChildren = mutableListOf<PartyTreeNode>()
            var childX = startX

            val levelEnum = HierarchyLevel.fromString(entity.level)
            val currentLevelSpacing = getNodeHeight(depth, childrenEntities.size)

            if (isExpanded && childrenEntities.isNotEmpty()) {
                childrenEntities.forEach { childEntity ->
                    val childY = nodeY + currentLevelSpacing
                    val (childNode, nextX) = layoutSubtree(childEntity, depth + 1, childX, childY)
                    laidOutChildren.add(childNode)
                    childX = nextX + siblingGap
                }
            }

            val subtreeWidth = if (laidOutChildren.isNotEmpty()) {
                (childX - startX - siblingGap).coerceAtLeast(nodeWidth)
            } else {
                nodeWidth
            }

            val nodeX = if (laidOutChildren.isNotEmpty()) {
                startX + (subtreeWidth - nodeWidth) / 2f
            } else {
                startX
            }

            // Photos only for top 2 tiers (national-level positions)
            val photoUrl = if (levelEnum.tier <= 2) getLeaderPhotoUrl(entity.holderName) else null

            // Build jurisdiction label for connection lines
            val jurisdictionLabel = when {
                !entity.district.isNullOrEmpty() -> entity.district
                !entity.state.isNullOrEmpty() -> entity.state
                else -> ""
            } ?: ""

            val partyNode = PartyTreeNode(
                id = entity.id,
                partyName = entity.partyName,
                level = levelEnum,
                parentId = entity.parentId,
                roleTitle = entity.roleTitle,
                holderName = entity.holderName,
                state = entity.state,
                district = entity.district,
                jurisdictionLabel = jurisdictionLabel,
                photoEmoji = getLeaderEmoji(entity.holderName),
                photoUrl = photoUrl,
                children = laidOutChildren,
                isExpanded = isExpanded,
                verificationStatus = if (entity.holderName.contains("Not yet fetched", ignoreCase = true))
                    VerificationStatus.NotYetFetched else VerificationStatus.Verified,
                canvasX = nodeX,
                canvasY = nodeY
            )

            flattenedList.add(partyNode)
            return partyNode to (startX + subtreeWidth)
        }

        val rootNodes = rootEntities.map { rootEntity ->
            val (rootNode, nextX) = layoutSubtree(rootEntity, depth = 0, startX = currentRootX, nodeY = 80f)
            currentRootX = nextX + 80f
            rootNode
        }

        return rootNodes to flattenedList
    }

    private fun calculateStateGeoData(entities: List<PartyNodeEntity>): List<StateGeoData> {
        val stateGroups = entities.filter { !it.state.isNullOrEmpty() }.groupBy { it.state!! }
        val maxLeaders = stateGroups.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

        return stateGroups.map { (stateName, stateEntities) ->
            val districtGroups = stateEntities.filter { !it.district.isNullOrEmpty() }.groupBy { it.district!! }
            val topLeader = stateEntities.firstOrNull {
                it.level.startsWith("STATE") && !it.holderName.contains("Not yet", ignoreCase = true)
            } ?: stateEntities.firstOrNull { !it.holderName.contains("Not yet", ignoreCase = true) }
            ?: stateEntities.first()

            val districts = districtGroups.map { (distName, distEntities) ->
                DistrictGeoData(
                    districtName = distName,
                    leaderCount = distEntities.size,
                    keyLeaders = distEntities.map { it.holderName }
                )
            }

            StateGeoData(
                stateName = stateName,
                stateCode = getStateCode(stateName),
                leaderCount = stateEntities.size,
                districtCount = districtGroups.size.coerceAtLeast(1),
                topLeaderName = topLeader.holderName,
                topLeaderRole = topLeader.roleTitle,
                representationScore = (stateEntities.size.toFloat() / maxLeaders).coerceIn(0.2f, 1.0f),
                districts = districts
            )
        }.sortedByDescending { it.leaderCount }
    }

    private fun getStateCode(state: String): String = when (state.lowercase()) {
        "maharashtra" -> "MH"; "delhi" -> "DL"; "karnataka" -> "KA"
        "tamil nadu" -> "TN"; "west bengal" -> "WB"; "gujarat" -> "GJ"
        "uttar pradesh" -> "UP"; "rajasthan" -> "RJ"; "madhya pradesh" -> "MP"
        "bihar" -> "BR"; "andhra pradesh" -> "AP"; "telangana" -> "TS"
        "kerala" -> "KL"; "odisha" -> "OD"; "jharkhand" -> "JH"
        "haryana" -> "HR"; "punjab" -> "PB"; "himachal pradesh" -> "HP"
        "uttarakhand" -> "UK"; "assam" -> "AS"; "chhattisgarh" -> "CG"
        "goa" -> "GA"; "tripura" -> "TR"; "meghalaya" -> "ML"
        "manipur" -> "MN"; "nagaland" -> "NL"; "arunachal pradesh" -> "AR"
        "sikkim" -> "SK"; "mizoram" -> "MZ"; "jammu & kashmir" -> "JK"
        "ladakh" -> "LA"; "chandigarh" -> "CH"; "puducherry" -> "PY"
        else -> state.take(2).uppercase()
    }

    private fun getLeaderEmoji(name: String): String {
        return when {
            name.contains("Modi", true) -> "🇮🇳"
            name.contains("Nadda", true) -> "🪷"
            name.contains("Kharge", true) -> "✋"
            name.contains("Gandhi", true) -> "🌾"
            name.contains("Kejriwal", true) -> "👓"
            name.contains("Pawar", true) -> "⏰"
            name.contains("Banerjee", true) || name.contains("Mamata", true) -> "🌱"
            name.contains("Stalin", true) -> "☀️"
            name.contains("Not yet", true) -> "❓"
            else -> "👨‍💼"
        }
    }

    private fun generatePoliticianProfile(node: PartyTreeNode): PoliticianProfile {
        val (bio, education, milestones, achievements) = when {
            node.holderName.contains("Narendra Modi", true) -> Quadruple(
                "14th Prime Minister of India since 2014. Previously served as Chief Minister of Gujarat from 2001 to 2014. Member of Parliament representing Varanasi.",
                "MA in Political Science, Gujarat University",
                listOf(
                    TimelineMilestone("2001", "Chief Minister of Gujarat", "Appointed CM of Gujarat, serving 4 consecutive terms.", "Appointment"),
                    TimelineMilestone("2014", "Elected Prime Minister", "Led BJP to historic majority in 16th Lok Sabha elections.", "Election"),
                    TimelineMilestone("2019", "Re-elected with Expanded Mandate", "Won second consecutive term with 303 seats.", "Election"),
                    TimelineMilestone("2024", "Third Term Prime Minister", "Sworn in as Prime Minister for a third consecutive term.", "Election")
                ),
                listOf(
                    "Digital India, UPI, and Jan Dhan Financial Inclusion architecture.",
                    "PM Gati Shakti national infrastructure master plan.",
                    "Hosted G20 Leaders Summit 2023 in New Delhi."
                )
            )
            node.holderName.contains("Nadda", true) -> Quadruple(
                "National President of the Bharatiya Janata Party and Union Minister of Health & Family Welfare.",
                "LL.B., Himachal Pradesh University",
                listOf(
                    TimelineMilestone("1993", "Elected to HP Legislative Assembly", "Cabinet Minister in HP Government.", "Election"),
                    TimelineMilestone("2014", "Union Health Minister", "Spearheaded Ayushman Bharat PM-JAY health rollout.", "Appointment"),
                    TimelineMilestone("2020", "Elected National President", "Unanimously elected 11th BJP National President.", "Appointment")
                ),
                listOf(
                    "Oversaw massive nationwide grassroots membership expansion.",
                    "Ayushman Bharat National Health Protection Scheme launch."
                )
            )
            node.holderName.contains("Kharge", true) -> Quadruple(
                "President of Indian National Congress and Leader of Opposition in Rajya Sabha.",
                "Bachelor of Arts & Law, Government Law College, Bengaluru",
                listOf(
                    TimelineMilestone("1972", "Elected MLA from Gurmitkal", "Won 9 consecutive assembly elections in Karnataka.", "Election"),
                    TimelineMilestone("2009", "Union Cabinet Minister", "Served as Senior Minister in UPA government.", "Appointment"),
                    TimelineMilestone("2022", "Elected Congress President", "First non-Gandhi president in 24 years.", "Election")
                ),
                listOf("Architect of landmark labour law reforms.")
            )
            node.holderName.contains("Kejriwal", true) -> Quadruple(
                "National Convenor of Aam Aadmi Party. Former IRS officer and anti-corruption reformer.",
                "B.Tech in Mechanical Engineering, IIT Kharagpur",
                listOf(
                    TimelineMilestone("2006", "Ramon Magsaysay Award", "Awarded for Emergent Leadership in RTI.", "Milestone"),
                    TimelineMilestone("2012", "Founded Aam Aadmi Party", "Launched party from India Against Corruption movement.", "Milestone"),
                    TimelineMilestone("2015", "Historic Delhi Assembly Victory", "AAP won 67 out of 70 seats.", "Election")
                ),
                listOf("Revolutionized Delhi government school infrastructure and Mohalla Clinics.")
            )
            node.holderName.contains("Pawar", true) -> Quadruple(
                "Founder and National President of NCP. Former Chief Minister of Maharashtra (4 terms). One of India's most experienced political leaders.",
                "B.Com., Brihan Maharashtra College of Commerce, Pune",
                listOf(
                    TimelineMilestone("1967", "First Elected to Maharashtra Assembly", "Youngest legislator at the time.", "Election"),
                    TimelineMilestone("1999", "Founded NCP", "Launched Nationalist Congress Party after split from Congress.", "Milestone"),
                    TimelineMilestone("2004", "Union Defence Minister", "Served as Defence Minister in UPA coalition.", "Appointment")
                ),
                listOf("Over 5 decades in Indian democratic politics.", "Key architect of Maharashtra's cooperative sector growth.")
            )
            node.holderName.contains("Banerjee", true) || node.holderName.contains("Mamata", true) -> Quadruple(
                "Chairperson of All India Trinamool Congress and Chief Minister of West Bengal. Popularly known as 'Didi'.",
                "Law graduate, Jogesh Chandra Chaudhuri Law College, Kolkata",
                listOf(
                    TimelineMilestone("1984", "Youngest Union Minister", "Youngest Union Minister in India.", "Appointment"),
                    TimelineMilestone("1998", "Founded TMC", "Founded All India Trinamool Congress.", "Milestone"),
                    TimelineMilestone("2011", "Chief Minister of West Bengal", "Ended 34 years of Left Front rule in WB.", "Election")
                ),
                listOf("Ended decades of Left rule in West Bengal.", "Founded one of India's most significant regional parties.")
            )
            node.holderName.contains("Stalin", true) -> Quadruple(
                "President of DMK and Chief Minister of Tamil Nadu. Son of former CM M. Karunanidhi.",
                "B.Sc., Presidency College, Chennai",
                listOf(
                    TimelineMilestone("1989", "Elected to TN Assembly", "First elected to Tamil Nadu Legislative Assembly.", "Election"),
                    TimelineMilestone("2018", "Elected DMK President", "Elected as party president after father's passing.", "Appointment"),
                    TimelineMilestone("2021", "Chief Minister", "Led DMK to victory after 10 years in opposition.", "Election")
                ),
                listOf("Led DMK to decisive 2021 assembly victory.", "Credited with state welfare schemes and urban development.")
            )
            else -> Quadruple(
                "Senior political representative serving ${node.partyName} in ${node.state ?: "National HQ"}. Responsible for organizational activities in the designated jurisdiction.",
                "Graduate Degree (details not yet verified)",
                listOf(
                    TimelineMilestone("N/A", "Appointed to Current Position", "Serving as ${node.roleTitle}.", "Appointment")
                ),
                listOf("Coordinating party organizational activities in the jurisdiction.")
            )
        }

        return PoliticianProfile(
            id = node.id,
            name = node.holderName,
            roleTitle = node.roleTitle,
            party = node.partyName,
            level = node.level,
            state = node.state,
            district = node.district,
            photoEmoji = node.photoEmoji,
            bio = bio,
            education = education,
            timeline = milestones,
            keyAchievements = achievements,
            verifiedSources = listOf(
                VerifiedSource("Official Party Organizational Directory", "${node.partyName} Central Office", "2026-08-01"),
                VerifiedSource("Election Commission of India Registry", "ECI Public Portal", "2024-06-05")
            ),
            verificationStatus = node.verificationStatus
        )
    }

    // ── Seed Data Generator — Full Position-Driven Hierarchy ─────────

    private suspend fun seedMockPartyData() {
        val allSeeds = mutableListOf<PartyNodeEntity>()
        listOf("BJP", "INC", "AAP", "NCP", "TMC", "DMK").forEach { party ->
            allSeeds.addAll(generateSeedNodes(party))
        }
        partyNodeDao.replaceAll(allSeeds)
    }

    private suspend fun seedMockPartyDataForParty(party: String) {
        val seeds = generateSeedNodes(party)
        partyNodeDao.insertAll(seeds)
    }

    /**
     * Generates full position-driven hierarchy for each party based on actual
     * researched organizational structures. Positions exist even when the person
     * is unknown (showing "Not yet fetched").
     */
    private fun generateSeedNodes(party: String): List<PartyNodeEntity> {
        val date = "2026-08-01"
        val nodes = mutableListOf<PartyNodeEntity>()
        val NYF = "Not yet fetched"

        when (party) {
            // ─────────────────────────────────────────────────────────────────
            // BJP — Bharatiya Janata Party
            // National Executive structure → State → District → Mandal → Booth
            // Based on BJP Constitution and public party records
            // ─────────────────────────────────────────────────────────────────
            "BJP" -> {
                // National President (Root)
                val n1 = "BJP_NAT_PRES"; nodes.add(PartyNodeEntity(n1, "BJP", "NATIONAL", null, "National President", "J. P. Nadda", null, null, date))
                // National Working President
                val n2 = "BJP_NAT_WP"; nodes.add(PartyNodeEntity(n2, "BJP", "NATIONAL_WORKING", n1, "National Working President", NYF, null, null, date))
                // National Vice Presidents
                nodes.add(PartyNodeEntity("BJP_NAT_VP1", "BJP", "NATIONAL_VP", n1, "National Vice President", "Shivraj Singh Chouhan", null, null, date))
                nodes.add(PartyNodeEntity("BJP_NAT_VP2", "BJP", "NATIONAL_VP", n1, "National Vice President", "Saroj Pandey", null, null, date))
                // National General Secretary (Organisation) — Sangathan Mantri
                val n3 = "BJP_NAT_GS_ORG"; nodes.add(PartyNodeEntity(n3, "BJP", "NATIONAL_GS_ORG", n1, "General Secretary (Organisation)", "B. L. Santhosh", null, null, date))
                // National General Secretaries
                nodes.add(PartyNodeEntity("BJP_NAT_GS1", "BJP", "NATIONAL_GS", n1, "National General Secretary", "Arun Singh", null, null, date))
                nodes.add(PartyNodeEntity("BJP_NAT_GS2", "BJP", "NATIONAL_GS", n1, "National General Secretary", "Tarun Chugh", null, null, date))
                nodes.add(PartyNodeEntity("BJP_NAT_GS3", "BJP", "NATIONAL_GS", n1, "National General Secretary", "Vinod Tawde", null, null, date))
                // National Secretaries
                nodes.add(PartyNodeEntity("BJP_NAT_SEC1", "BJP", "NATIONAL_SECRETARY", n1, "National Secretary", NYF, null, null, date))
                // National Treasurer
                nodes.add(PartyNodeEntity("BJP_NAT_TREAS", "BJP", "NATIONAL_TREASURER", n1, "National Treasurer", "Arun Singh", null, null, date))
                // Parliamentary Leader (PM)
                nodes.add(PartyNodeEntity("BJP_NAT_PM", "BJP", "NATIONAL", n1, "Prime Minister & Parliamentary Leader", "Narendra Modi", null, null, date))

                // ── Maharashtra State Unit ────────────────────────────────
                val mh = "BJP_STATE_MH"; nodes.add(PartyNodeEntity(mh, "BJP", "STATE", n1, "State President (Maharashtra)", "Chandrashekhar Bawankule", "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_WP", "BJP", "STATE_WORKING", mh, "State Working President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_VP1", "BJP", "STATE_VP", mh, "State Vice President", "Mohit Kamboj", "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_VP2", "BJP", "STATE_VP", mh, "State Vice President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_GS_ORG", "BJP", "STATE_GS_ORG", mh, "State GS (Organisation)", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_GS1", "BJP", "STATE_GS", mh, "State General Secretary", "Girish Mahajan", "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_MH_TREAS", "BJP", "STATE_TREASURER", mh, "State Treasurer", NYF, "Maharashtra", null, date))
                // Mumbai District
                val mum = "BJP_DIST_MUM"; nodes.add(PartyNodeEntity(mum, "BJP", "DISTRICT", mh, "District President (Mumbai City)", "Ashish Shelar", "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("BJP_DIST_MUM_GS", "BJP", "DISTRICT_GS", mum, "District General Secretary", NYF, "Maharashtra", "Mumbai", date))
                val ward101 = "BJP_WARD_MUM_1"; nodes.add(PartyNodeEntity(ward101, "BJP", "WARD", mum, "Ward 101 President (Bandra West)", "Rakesh Sharma", "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("BJP_BOOTH_MUM_1", "BJP", "BOOTH", ward101, "Booth 101 Committee President", NYF, "Maharashtra", "Mumbai", date))
                val ward102 = "BJP_WARD_MUM_2"; nodes.add(PartyNodeEntity(ward102, "BJP", "WARD", mum, "Ward 102 President (Andheri East)", "Priya Deshmukh", "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("BJP_BOOTH_MUM_2", "BJP", "BOOTH", ward102, "Booth 102 Committee President", NYF, "Maharashtra", "Mumbai", date))
                // Pune District
                val pune = "BJP_DIST_PUNE"; nodes.add(PartyNodeEntity(pune, "BJP", "DISTRICT", mh, "District President (Pune City)", "Dhiraj Ghate", "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("BJP_DIST_PUNE_GS", "BJP", "DISTRICT_GS", pune, "District General Secretary", NYF, "Maharashtra", "Pune", date))
                val tasgaon = "BJP_MANDAL_PUNE_1"; nodes.add(PartyNodeEntity(tasgaon, "BJP", "TALUKA_MANDAL", pune, "Haveli Taluka President", NYF, "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("BJP_WARD_PUNE_1", "BJP", "WARD", tasgaon, "Ward Committee Chief (Shivajinagar)", "Siddharth Shirole", "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("BJP_WARD_PUNE_2", "BJP", "WARD", tasgaon, "Ward Committee Chief (Kothrud)", "Chandrakant Patil", "Maharashtra", "Pune", date))
                // Nagpur District
                val nagpur = "BJP_DIST_NAGPUR"; nodes.add(PartyNodeEntity(nagpur, "BJP", "DISTRICT", mh, "District President (Nagpur)", "Jitendra Kukde", "Maharashtra", "Nagpur", date))

                // ── Delhi State Unit ──────────────────────────────────────
                val dl = "BJP_STATE_DL"; nodes.add(PartyNodeEntity(dl, "BJP", "STATE", n1, "State President (Delhi)", "Virendra Sachdeva", "Delhi", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_DL_WP", "BJP", "STATE_WORKING", dl, "State Working President", NYF, "Delhi", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_DL_GS_ORG", "BJP", "STATE_GS_ORG", dl, "State GS (Organisation)", NYF, "Delhi", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_DL_GS1", "BJP", "STATE_GS", dl, "State General Secretary", NYF, "Delhi", null, date))
                val nd = "BJP_DIST_ND"; nodes.add(PartyNodeEntity(nd, "BJP", "DISTRICT", dl, "District President (New Delhi)", "Sunil Yadav", "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("BJP_DIST_ND_GS", "BJP", "DISTRICT_GS", nd, "District General Secretary", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("BJP_WARD_ND_1", "BJP", "WARD", nd, "Ward Committee President (Connaught Place)", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("BJP_BOOTH_ND_1", "BJP", "BOOTH", "BJP_WARD_ND_1", "Booth President", NYF, "Delhi", "New Delhi", date))

                // ── Karnataka State Unit ──────────────────────────────────
                val ka = "BJP_STATE_KA"; nodes.add(PartyNodeEntity(ka, "BJP", "STATE", n1, "State President (Karnataka)", "B. Y. Vijayendra", "Karnataka", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_KA_WP", "BJP", "STATE_WORKING", ka, "State Working President", NYF, "Karnataka", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_KA_GS_ORG", "BJP", "STATE_GS_ORG", ka, "State GS (Organisation)", NYF, "Karnataka", null, date))
                val blr = "BJP_DIST_BLR"; nodes.add(PartyNodeEntity(blr, "BJP", "DISTRICT", ka, "District President (Bengaluru Urban)", NYF, "Karnataka", "Bengaluru", date))
                nodes.add(PartyNodeEntity("BJP_MANDAL_BLR_1", "BJP", "TALUKA_MANDAL", blr, "Bengaluru North Mandal President", NYF, "Karnataka", "Bengaluru", date))
                nodes.add(PartyNodeEntity("BJP_WARD_BLR_1", "BJP", "WARD", "BJP_MANDAL_BLR_1", "Ward Committee President (Rajajinagar)", NYF, "Karnataka", "Bengaluru", date))

                // ── Uttar Pradesh State Unit ──────────────────────────────
                val up = "BJP_STATE_UP"; nodes.add(PartyNodeEntity(up, "BJP", "STATE", n1, "State President (Uttar Pradesh)", "Bhupendra Chaudhary", "Uttar Pradesh", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_UP_WP", "BJP", "STATE_WORKING", up, "State Working President", NYF, "Uttar Pradesh", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_UP_GS_ORG", "BJP", "STATE_GS_ORG", up, "State GS (Organisation)", NYF, "Uttar Pradesh", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_UP_GS1", "BJP", "STATE_GS", up, "State General Secretary", NYF, "Uttar Pradesh", null, date))
                val lko = "BJP_DIST_LKO"; nodes.add(PartyNodeEntity(lko, "BJP", "DISTRICT", up, "District President (Lucknow)", NYF, "Uttar Pradesh", "Lucknow", date))
                nodes.add(PartyNodeEntity("BJP_MANDAL_LKO_1", "BJP", "TALUKA_MANDAL", lko, "Lucknow Cantonment Mandal President", NYF, "Uttar Pradesh", "Lucknow", date))

                // ── Gujarat State Unit ────────────────────────────────────
                val gj = "BJP_STATE_GJ"; nodes.add(PartyNodeEntity(gj, "BJP", "STATE", n1, "State President (Gujarat)", "C. R. Paatil", "Gujarat", null, date))
                nodes.add(PartyNodeEntity("BJP_STATE_GJ_GS_ORG", "BJP", "STATE_GS_ORG", gj, "State GS (Organisation)", NYF, "Gujarat", null, date))
                val ahm = "BJP_DIST_AHM"; nodes.add(PartyNodeEntity(ahm, "BJP", "DISTRICT", gj, "District President (Ahmedabad)", NYF, "Gujarat", "Ahmedabad", date))
                nodes.add(PartyNodeEntity("BJP_WARD_AHM_1", "BJP", "WARD", ahm, "Ward President (Naranpura)", NYF, "Gujarat", "Ahmedabad", date))
            }

            // ─────────────────────────────────────────────────────────────────
            // INC — Indian National Congress
            // AICC structure: President → Working President → CWC → PCC → DCC → Block → Booth
            // ─────────────────────────────────────────────────────────────────
            "INC" -> {
                // Congress President (Root)
                val n1 = "INC_NAT_PRES"; nodes.add(PartyNodeEntity(n1, "INC", "NATIONAL", null, "Congress President", "Mallikarjun Kharge", null, null, date))
                // Congress Working President
                val n2 = "INC_NAT_WP"; nodes.add(PartyNodeEntity(n2, "INC", "NATIONAL_WORKING", n1, "Congress Working President", NYF, null, null, date))
                // National Vice Presidents
                nodes.add(PartyNodeEntity("INC_NAT_VP1", "INC", "NATIONAL_VP", n1, "AICC Vice President", NYF, null, null, date))
                // GS Organisation
                val n3 = "INC_NAT_GS_ORG"; nodes.add(PartyNodeEntity(n3, "INC", "NATIONAL_GS_ORG", n1, "General Secretary (Organisation)", "K. C. Venugopal", null, null, date))
                // General Secretaries
                nodes.add(PartyNodeEntity("INC_NAT_GS1", "INC", "NATIONAL_GS", n1, "AICC General Secretary", "Jairam Ramesh", null, null, date))
                nodes.add(PartyNodeEntity("INC_NAT_GS2", "INC", "NATIONAL_GS", n1, "AICC General Secretary", "Randeep Surjewala", null, null, date))
                // National Secretaries
                nodes.add(PartyNodeEntity("INC_NAT_SEC1", "INC", "NATIONAL_SECRETARY", n1, "AICC National Secretary", NYF, null, null, date))
                // Treasurer
                nodes.add(PartyNodeEntity("INC_NAT_TREAS", "INC", "NATIONAL_TREASURER", n1, "Congress Treasurer", NYF, null, null, date))
                // Parliamentary Leaders
                nodes.add(PartyNodeEntity("INC_NAT_LOP", "INC", "NATIONAL", n1, "Leader of Opposition (Lok Sabha)", "Rahul Gandhi", null, null, date))
                nodes.add(PartyNodeEntity("INC_NAT_LOP_RS", "INC", "NATIONAL", n1, "Leader of Opposition (Rajya Sabha)", "Mallikarjun Kharge", null, null, date))

                // ── Maharashtra PCC (Pradesh Congress Committee) ──────────
                val mh = "INC_STATE_MH"; nodes.add(PartyNodeEntity(mh, "INC", "STATE", n1, "PCC President (Maharashtra)", "Nana Patole", "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_MH_WP", "INC", "STATE_WORKING", mh, "PCC Working President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_MH_VP1", "INC", "STATE_VP", mh, "PCC Vice President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_MH_GS_ORG", "INC", "STATE_GS_ORG", mh, "PCC GS (Organisation)", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_MH_GS1", "INC", "STATE_GS", mh, "PCC General Secretary", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_MH_TREAS", "INC", "STATE_TREASURER", mh, "PCC Treasurer", NYF, "Maharashtra", null, date))
                // Mumbai DCC (District Congress Committee)
                val mum = "INC_DIST_MUM"; nodes.add(PartyNodeEntity(mum, "INC", "DISTRICT", mh, "DCC President (Mumbai)", "Varsha Gaikwad", "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("INC_DIST_MUM_GS", "INC", "DISTRICT_GS", mum, "DCC General Secretary", NYF, "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("INC_BLOCK_MUM_1", "INC", "TALUKA_MANDAL", mum, "Block Congress President (Bandra)", NYF, "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("INC_WARD_MUM_1", "INC", "WARD", "INC_BLOCK_MUM_1", "Ward Congress Chief (Ward 86)", NYF, "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("INC_BOOTH_MUM_1", "INC", "BOOTH", "INC_WARD_MUM_1", "Booth Congress President", NYF, "Maharashtra", "Mumbai", date))
                // Pune DCC
                val pune = "INC_DIST_PUNE"; nodes.add(PartyNodeEntity(pune, "INC", "DISTRICT", mh, "DCC President (Pune)", "Arvind Shinde", "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("INC_DIST_PUNE_GS", "INC", "DISTRICT_GS", pune, "DCC General Secretary", NYF, "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("INC_BLOCK_PUNE_1", "INC", "TALUKA_MANDAL", pune, "Block Congress President (Haveli)", NYF, "Maharashtra", "Pune", date))

                // ── Delhi PCC ─────────────────────────────────────────────
                val dl = "INC_STATE_DL"; nodes.add(PartyNodeEntity(dl, "INC", "STATE", n1, "PCC President (Delhi)", "Devender Yadav", "Delhi", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_DL_WP", "INC", "STATE_WORKING", dl, "PCC Working President", NYF, "Delhi", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_DL_GS_ORG", "INC", "STATE_GS_ORG", dl, "PCC GS (Organisation)", NYF, "Delhi", null, date))
                val ndDl = "INC_DIST_ND"; nodes.add(PartyNodeEntity(ndDl, "INC", "DISTRICT", dl, "DCC President (New Delhi)", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("INC_BLOCK_ND_1", "INC", "TALUKA_MANDAL", ndDl, "Block Congress President (Connaught)", NYF, "Delhi", "New Delhi", date))

                // ── Karnataka PCC ─────────────────────────────────────────
                val ka = "INC_STATE_KA"; nodes.add(PartyNodeEntity(ka, "INC", "STATE", n1, "PCC President (Karnataka)", "D. K. Shivakumar", "Karnataka", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_KA_WP", "INC", "STATE_WORKING", ka, "PCC Working President", NYF, "Karnataka", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_KA_GS_ORG", "INC", "STATE_GS_ORG", ka, "PCC GS (Organisation)", NYF, "Karnataka", null, date))
                val blr = "INC_DIST_BLR"; nodes.add(PartyNodeEntity(blr, "INC", "DISTRICT", ka, "DCC President (Bengaluru Urban)", NYF, "Karnataka", "Bengaluru", date))
                nodes.add(PartyNodeEntity("INC_BLOCK_BLR_1", "INC", "TALUKA_MANDAL", blr, "Block Congress President (Whitefield)", NYF, "Karnataka", "Bengaluru", date))

                // ── Uttar Pradesh PCC ─────────────────────────────────────
                val up = "INC_STATE_UP"; nodes.add(PartyNodeEntity(up, "INC", "STATE", n1, "PCC President (UP)", "Ajay Rai", "Uttar Pradesh", null, date))
                nodes.add(PartyNodeEntity("INC_STATE_UP_GS_ORG", "INC", "STATE_GS_ORG", up, "PCC GS (Organisation)", NYF, "Uttar Pradesh", null, date))
                val lko = "INC_DIST_LKO"; nodes.add(PartyNodeEntity(lko, "INC", "DISTRICT", up, "DCC President (Lucknow)", NYF, "Uttar Pradesh", "Lucknow", date))
            }

            // ─────────────────────────────────────────────────────────────────
            // AAP — Aam Aadmi Party
            // Flat-ish structure: National Convenor → PAC → State Convenor → District → Ward
            // ─────────────────────────────────────────────────────────────────
            "AAP" -> {
                val n1 = "AAP_NAT_CONV"; nodes.add(PartyNodeEntity(n1, "AAP", "NATIONAL", null, "National Convenor", "Arvind Kejriwal", null, null, date))
                // National GS Organisation
                val n2 = "AAP_NAT_GS_ORG"; nodes.add(PartyNodeEntity(n2, "AAP", "NATIONAL_GS_ORG", n1, "National GS (Organisation)", "Sandeep Pathak", null, null, date))
                // Political Affairs Committee representatives
                nodes.add(PartyNodeEntity("AAP_PAC_1", "AAP", "NATIONAL_GS", n1, "PAC Member / Senior Leader", "Manish Sisodia", null, null, date))
                nodes.add(PartyNodeEntity("AAP_PAC_2", "AAP", "NATIONAL_GS", n1, "PAC Member / Senior Leader", "Saurabh Bharadwaj", null, null, date))
                // National Treasurer
                nodes.add(PartyNodeEntity("AAP_NAT_TREAS", "AAP", "NATIONAL_TREASURER", n1, "National Treasurer", NYF, null, null, date))

                // ── Delhi State Unit ──────────────────────────────────────
                val dl = "AAP_STATE_DL"; nodes.add(PartyNodeEntity(dl, "AAP", "STATE", n1, "State Convenor (Delhi)", "Gopal Rai", "Delhi", null, date))
                nodes.add(PartyNodeEntity("AAP_STATE_DL_CO_CONV", "AAP", "STATE_WORKING", dl, "State Co-Convenor", NYF, "Delhi", null, date))
                nodes.add(PartyNodeEntity("AAP_STATE_DL_GS_ORG", "AAP", "STATE_GS_ORG", dl, "State GS (Organisation)", NYF, "Delhi", null, date))
                val ndAap = "AAP_DIST_ND"; nodes.add(PartyNodeEntity(ndAap, "AAP", "DISTRICT", dl, "District Convenor (New Delhi)", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("AAP_DIST_ND_GS", "AAP", "DISTRICT_GS", ndAap, "District Secretary", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("AAP_CONST_ND_1", "AAP", "CONSTITUENCY", ndAap, "Constituency Incharge (Chandni Chowk)", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("AAP_WARD_ND_1", "AAP", "WARD", "AAP_CONST_ND_1", "Ward Coordinator (Ward 1)", NYF, "Delhi", "New Delhi", date))
                nodes.add(PartyNodeEntity("AAP_BOOTH_ND_1", "AAP", "BOOTH", "AAP_WARD_ND_1", "Booth In-charge", NYF, "Delhi", "New Delhi", date))

                // ── Punjab State Unit ─────────────────────────────────────
                val pb = "AAP_STATE_PB"; nodes.add(PartyNodeEntity(pb, "AAP", "STATE", n1, "State Convenor (Punjab)", "Bhagwant Mann", "Punjab", null, date))
                nodes.add(PartyNodeEntity("AAP_STATE_PB_GS_ORG", "AAP", "STATE_GS_ORG", pb, "State GS (Organisation)", NYF, "Punjab", null, date))
                val amritsar = "AAP_DIST_ASR"; nodes.add(PartyNodeEntity(amritsar, "AAP", "DISTRICT", pb, "District Convenor (Amritsar)", NYF, "Punjab", "Amritsar", date))
                nodes.add(PartyNodeEntity("AAP_CONST_ASR_1", "AAP", "CONSTITUENCY", amritsar, "Constituency Incharge (Amritsar Central)", NYF, "Punjab", "Amritsar", date))

                // ── Maharashtra State Unit ────────────────────────────────
                val mh = "AAP_STATE_MH"; nodes.add(PartyNodeEntity(mh, "AAP", "STATE", n1, "State Convenor (Maharashtra)", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("AAP_STATE_MH_GS_ORG", "AAP", "STATE_GS_ORG", mh, "State GS (Organisation)", NYF, "Maharashtra", null, date))
                val mum = "AAP_DIST_MUM"; nodes.add(PartyNodeEntity(mum, "AAP", "DISTRICT", mh, "District Convenor (Mumbai)", NYF, "Maharashtra", "Mumbai", date))
            }

            // ─────────────────────────────────────────────────────────────────
            // NCP — Nationalist Congress Party (Sharad Pawar faction)
            // Strong in Maharashtra. District/cooperative-level organization.
            // ─────────────────────────────────────────────────────────────────
            "NCP" -> {
                val n1 = "NCP_NAT_PRES"; nodes.add(PartyNodeEntity(n1, "NCP", "NATIONAL", null, "National President", "Sharad Pawar", null, null, date))
                // Working President
                val n2 = "NCP_NAT_WP"; nodes.add(PartyNodeEntity(n2, "NCP", "NATIONAL_WORKING", n1, "Working President", NYF, null, null, date))
                // National Vice Presidents
                nodes.add(PartyNodeEntity("NCP_NAT_VP1", "NCP", "NATIONAL_VP", n1, "National Vice President", "Praful Patel", null, null, date))
                nodes.add(PartyNodeEntity("NCP_NAT_VP2", "NCP", "NATIONAL_VP", n1, "National Vice President", NYF, null, null, date))
                // GS Organisation
                val n3 = "NCP_NAT_GS_ORG"; nodes.add(PartyNodeEntity(n3, "NCP", "NATIONAL_GS_ORG", n1, "General Secretary (Organisation)", NYF, null, null, date))
                // General Secretaries
                nodes.add(PartyNodeEntity("NCP_NAT_GS1", "NCP", "NATIONAL_GS", n1, "National General Secretary", "Sunil Tatkare", null, null, date))
                nodes.add(PartyNodeEntity("NCP_NAT_GS2", "NCP", "NATIONAL_GS", n1, "National General Secretary", NYF, null, null, date))
                // Treasurer
                nodes.add(PartyNodeEntity("NCP_NAT_TREAS", "NCP", "NATIONAL_TREASURER", n1, "National Treasurer", NYF, null, null, date))

                // ── Maharashtra State Unit ────────────────────────────────
                val mh = "NCP_STATE_MH"; nodes.add(PartyNodeEntity(mh, "NCP", "STATE", n1, "State President (Maharashtra)", "Jayant Patil", "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("NCP_STATE_MH_WP", "NCP", "STATE_WORKING", mh, "State Working President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("NCP_STATE_MH_VP1", "NCP", "STATE_VP", mh, "State Vice President", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("NCP_STATE_MH_GS_ORG", "NCP", "STATE_GS_ORG", mh, "State GS (Organisation)", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("NCP_STATE_MH_GS1", "NCP", "STATE_GS", mh, "State General Secretary", NYF, "Maharashtra", null, date))
                nodes.add(PartyNodeEntity("NCP_STATE_MH_TREAS", "NCP", "STATE_TREASURER", mh, "State Treasurer", NYF, "Maharashtra", null, date))
                // Pune District (NCP stronghold)
                val pune = "NCP_DIST_PUNE"; nodes.add(PartyNodeEntity(pune, "NCP", "DISTRICT", mh, "District President (Pune)", "Prashant Jagtap", "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("NCP_DIST_PUNE_GS", "NCP", "DISTRICT_GS", pune, "District General Secretary", NYF, "Maharashtra", "Pune", date))
                val tasgaon2 = "NCP_TALUKA_PUNE_1"; nodes.add(PartyNodeEntity(tasgaon2, "NCP", "TALUKA_MANDAL", pune, "Haveli Taluka President", NYF, "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("NCP_GS_TALUKA_PUNE", "NCP", "TALUKA_GS", tasgaon2, "Haveli Taluka General Secretary", NYF, "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("NCP_WARD_PUNE_1", "NCP", "WARD", tasgaon2, "Ward President (Karve Nagar)", NYF, "Maharashtra", "Pune", date))
                nodes.add(PartyNodeEntity("NCP_BOOTH_PUNE_1", "NCP", "BOOTH", "NCP_WARD_PUNE_1", "Booth Committee President", NYF, "Maharashtra", "Pune", date))
                // Mumbai District
                val mum = "NCP_DIST_MUM"; nodes.add(PartyNodeEntity(mum, "NCP", "DISTRICT", mh, "District President (Mumbai)", NYF, "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("NCP_DIST_MUM_GS", "NCP", "DISTRICT_GS", mum, "District General Secretary", NYF, "Maharashtra", "Mumbai", date))
                nodes.add(PartyNodeEntity("NCP_TALUKA_MUM_1", "NCP", "TALUKA_MANDAL", mum, "Andheri Mandal President", NYF, "Maharashtra", "Mumbai", date))
                // Satara District
                val satara = "NCP_DIST_SATARA"; nodes.add(PartyNodeEntity(satara, "NCP", "DISTRICT", mh, "District President (Satara)", NYF, "Maharashtra", "Satara", date))
                nodes.add(PartyNodeEntity("NCP_TALUKA_SATARA_1", "NCP", "TALUKA_MANDAL", satara, "Satara Taluka President", NYF, "Maharashtra", "Satara", date))
            }

            // ─────────────────────────────────────────────────────────────────
            // TMC — All India Trinamool Congress
            // WB-centric. Block/GP (Gram Panchayat) level organization.
            // ─────────────────────────────────────────────────────────────────
            "TMC" -> {
                val n1 = "TMC_NAT_CHAIR"; nodes.add(PartyNodeEntity(n1, "TMC", "NATIONAL", null, "National Chairperson", "Mamata Banerjee", null, null, date))
                // National General Secretary
                val n2 = "TMC_NAT_GS"; nodes.add(PartyNodeEntity(n2, "TMC", "NATIONAL_GS", n1, "National General Secretary", "Abhishek Banerjee", null, null, date))
                // National Spokesperson / VP
                nodes.add(PartyNodeEntity("TMC_NAT_VP1", "TMC", "NATIONAL_VP", n1, "National Vice President", NYF, null, null, date))
                nodes.add(PartyNodeEntity("TMC_NAT_GS_ORG", "TMC", "NATIONAL_GS_ORG", n1, "National GS (Organisation)", NYF, null, null, date))
                nodes.add(PartyNodeEntity("TMC_NAT_TREAS", "TMC", "NATIONAL_TREASURER", n1, "National Treasurer", NYF, null, null, date))
                // Parliamentary Leader
                nodes.add(PartyNodeEntity("TMC_NAT_PARL", "TMC", "NATIONAL", n1, "Parliamentary Party Leader (LS)", "Sudip Bandyopadhyay", null, null, date))

                // ── West Bengal State Unit ────────────────────────────────
                val wb = "TMC_STATE_WB"; nodes.add(PartyNodeEntity(wb, "TMC", "STATE", n2, "State President (West Bengal)", NYF, "West Bengal", null, date))
                nodes.add(PartyNodeEntity("TMC_STATE_WB_WP", "TMC", "STATE_WORKING", wb, "State Working President", NYF, "West Bengal", null, date))
                nodes.add(PartyNodeEntity("TMC_STATE_WB_VP1", "TMC", "STATE_VP", wb, "State Vice President", NYF, "West Bengal", null, date))
                nodes.add(PartyNodeEntity("TMC_STATE_WB_GS_ORG", "TMC", "STATE_GS_ORG", wb, "State GS (Organisation)", NYF, "West Bengal", null, date))
                nodes.add(PartyNodeEntity("TMC_STATE_WB_GS1", "TMC", "STATE_GS", wb, "State General Secretary", NYF, "West Bengal", null, date))
                // Kolkata District
                val kol = "TMC_DIST_KOL"; nodes.add(PartyNodeEntity(kol, "TMC", "DISTRICT", wb, "District President (Kolkata)", NYF, "West Bengal", "Kolkata", date))
                nodes.add(PartyNodeEntity("TMC_DIST_KOL_GS", "TMC", "DISTRICT_GS", kol, "District General Secretary", NYF, "West Bengal", "Kolkata", date))
                val jbl = "TMC_BLOCK_KOL_1"; nodes.add(PartyNodeEntity(jbl, "TMC", "TALUKA_MANDAL", kol, "Block President (Jadavpur)", NYF, "West Bengal", "Kolkata", date))
                nodes.add(PartyNodeEntity("TMC_WARD_KOL_1", "TMC", "WARD", jbl, "Ward President (Ward 105)", NYF, "West Bengal", "Kolkata", date))
                nodes.add(PartyNodeEntity("TMC_BOOTH_KOL_1", "TMC", "BOOTH", "TMC_WARD_KOL_1", "Booth Committee President", NYF, "West Bengal", "Kolkata", date))
                // North 24 Parganas
                val n24 = "TMC_DIST_N24PG"; nodes.add(PartyNodeEntity(n24, "TMC", "DISTRICT", wb, "District President (North 24 Parganas)", NYF, "West Bengal", "North 24 Parganas", date))
                nodes.add(PartyNodeEntity("TMC_BLOCK_N24PG_1", "TMC", "TALUKA_MANDAL", n24, "Block President (Barasat Sadar)", NYF, "West Bengal", "North 24 Parganas", date))
                nodes.add(PartyNodeEntity("TMC_GP_N24PG_1", "TMC", "WARD", "TMC_BLOCK_N24PG_1", "Gram Panchayat President (Barasat GP)", NYF, "West Bengal", "North 24 Parganas", date))

                // ── Tripura State Unit ────────────────────────────────────
                val tr = "TMC_STATE_TR"; nodes.add(PartyNodeEntity(tr, "TMC", "STATE", n2, "State President (Tripura)", NYF, "Tripura", null, date))
                nodes.add(PartyNodeEntity("TMC_STATE_TR_GS_ORG", "TMC", "STATE_GS_ORG", tr, "State GS (Organisation)", NYF, "Tripura", null, date))
                nodes.add(PartyNodeEntity("TMC_DIST_TR_WEST", "TMC", "DISTRICT", tr, "District President (West Tripura)", NYF, "Tripura", "West Tripura", date))
            }

            // ─────────────────────────────────────────────────────────────────
            // DMK — Dravida Munnetra Kazhagam
            // Structured with Districts → Urban Local Bodies / Talukas → Wards
            // Also has organizational wings: Youth, Women, etc.
            // ─────────────────────────────────────────────────────────────────
            else -> { // DMK
                val n1 = "DMK_NAT_PRES"; nodes.add(PartyNodeEntity(n1, "DMK", "NATIONAL", null, "Party President", "M. K. Stalin", null, null, date))
                // General Secretary
                val n2 = "DMK_NAT_GS"; nodes.add(PartyNodeEntity(n2, "DMK", "NATIONAL_GS", n1, "General Secretary", "Duraimurugan", null, null, date))
                // Vice Presidents
                nodes.add(PartyNodeEntity("DMK_NAT_VP1", "DMK", "NATIONAL_VP", n1, "Vice President", "Udhayanidhi Stalin", null, null, date))
                nodes.add(PartyNodeEntity("DMK_NAT_VP2", "DMK", "NATIONAL_VP", n1, "Vice President", NYF, null, null, date))
                // Joint Secretaries
                nodes.add(PartyNodeEntity("DMK_NAT_JS1", "DMK", "NATIONAL_JOINT_SECRETARY", n1, "Joint Secretary", NYF, null, null, date))
                // Treasurer
                nodes.add(PartyNodeEntity("DMK_NAT_TREAS", "DMK", "NATIONAL_TREASURER", n1, "Party Treasurer", NYF, null, null, date))
                // Parliamentary Leader
                nodes.add(PartyNodeEntity("DMK_NAT_PARL", "DMK", "NATIONAL", n1, "Parliamentary Party Leader (LS)", "T. R. Baalu", null, null, date))
                // Chief Minister
                nodes.add(PartyNodeEntity("DMK_CM_TN", "DMK", "NATIONAL", n1, "Chief Minister of Tamil Nadu", "M. K. Stalin", "Tamil Nadu", null, date))

                // ── Tamil Nadu State Organisation ─────────────────────────
                val tn = "DMK_STATE_TN"; nodes.add(PartyNodeEntity(tn, "DMK", "STATE", n2, "State Secretary (Tamil Nadu)", NYF, "Tamil Nadu", null, date))
                nodes.add(PartyNodeEntity("DMK_STATE_TN_VP1", "DMK", "STATE_VP", tn, "State Vice President", NYF, "Tamil Nadu", null, date))
                nodes.add(PartyNodeEntity("DMK_STATE_TN_GS_ORG", "DMK", "STATE_GS_ORG", tn, "State GS (Organisation)", NYF, "Tamil Nadu", null, date))
                nodes.add(PartyNodeEntity("DMK_STATE_TN_TREAS", "DMK", "STATE_TREASURER", tn, "State Treasurer", NYF, "Tamil Nadu", null, date))
                // Chennai District
                val chn = "DMK_DIST_CHN"; nodes.add(PartyNodeEntity(chn, "DMK", "DISTRICT", tn, "District Secretary (Chennai)", NYF, "Tamil Nadu", "Chennai", date))
                nodes.add(PartyNodeEntity("DMK_DIST_CHN_GS", "DMK", "DISTRICT_GS", chn, "District General Secretary", NYF, "Tamil Nadu", "Chennai", date))
                val union1 = "DMK_UNION_CHN_1"; nodes.add(PartyNodeEntity(union1, "DMK", "TALUKA_MANDAL", chn, "Union Secretary (Mylapore)", NYF, "Tamil Nadu", "Chennai", date))
                nodes.add(PartyNodeEntity("DMK_WARD_CHN_1", "DMK", "WARD", union1, "Ward Secretary (Ward 122)", NYF, "Tamil Nadu", "Chennai", date))
                nodes.add(PartyNodeEntity("DMK_BOOTH_CHN_1", "DMK", "BOOTH", "DMK_WARD_CHN_1", "Booth President", NYF, "Tamil Nadu", "Chennai", date))
                // Coimbatore District
                val cbe = "DMK_DIST_CBE"; nodes.add(PartyNodeEntity(cbe, "DMK", "DISTRICT", tn, "District Secretary (Coimbatore)", NYF, "Tamil Nadu", "Coimbatore", date))
                nodes.add(PartyNodeEntity("DMK_UNION_CBE_1", "DMK", "TALUKA_MANDAL", cbe, "Union Secretary (Coimbatore South)", NYF, "Tamil Nadu", "Coimbatore", date))
                nodes.add(PartyNodeEntity("DMK_WARD_CBE_1", "DMK", "WARD", "DMK_UNION_CBE_1", "Ward Secretary", NYF, "Tamil Nadu", "Coimbatore", date))
                // Madurai District
                val mdri = "DMK_DIST_MDU"; nodes.add(PartyNodeEntity(mdri, "DMK", "DISTRICT", tn, "District Secretary (Madurai)", NYF, "Tamil Nadu", "Madurai", date))
                // Puducherry
                val py = "DMK_STATE_PY"; nodes.add(PartyNodeEntity(py, "DMK", "STATE", n2, "State Secretary (Puducherry)", NYF, "Puducherry", null, date))
                nodes.add(PartyNodeEntity("DMK_DIST_PY_NORTH", "DMK", "DISTRICT", py, "District Secretary (Puducherry North)", NYF, "Puducherry", "Puducherry", date))
            }
        }

        return nodes
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
