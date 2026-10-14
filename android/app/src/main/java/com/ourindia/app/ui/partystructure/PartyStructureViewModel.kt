package com.ourindia.app.ui.partystructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.data.repository.PoliticalSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PartyStructureViewModel @Inject constructor(
    private val partyNodeDao: PartyNodeDao,
    private val syncRepository: PoliticalSyncRepository,
    private val locationStore: LocationStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartyStructureUiState(partiesWithData = SEEDED_PARTIES))
    val uiState: StateFlow<PartyStructureUiState> = _uiState.asStateFlow()

    private var rawEntitiesList: List<PartyNodeEntity> = emptyList()
    private var orgTree: OrgTree = OrgTree.Empty

    // One live Room observation at a time. Previously every party switch and every
    // Refresh tap started another collector without cancelling the old one; Room
    // re-emits all of them on any table write, so a stale party's rows could land
    // in the chart after the new party's.
    private var partyJob: Job? = null

    // Seed each bundled party at most once per process. Without this guard an empty
    // query re-seeded on every emission of itself and spun forever.
    private val seedAttempted = mutableSetOf<String>()

    /** Node to focus once the party's rows have loaded (deep link from Home). */
    private var pendingLocate: String? = null

    init {
        // A fix taken on Home or Report also filters Leaders, and vice versa.
        viewModelScope.launch {
            locationStore.location.collect { loc ->
                if (loc != _uiState.value.resolvedLocation) applyLocation(loc)
            }
        }
        viewModelScope.launch {
            partyNodeDao.observePartyNames().collect { names ->
                _uiState.update { it.copy(partiesWithData = names.toSet() + SEEDED_PARTIES) }
            }
        }
        viewModelScope.launch { syncRepository.performDeltaSync() }
    }

    /** Pulls the latest changes from Supabase. Room re-emits the open party if rows changed. */
    fun refresh() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val result = runCatching { syncRepository.performDeltaSync() }.getOrElse { Result.failure(it) }
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    userMessage = if (result.isSuccess) "Up to date" else "Couldn't reach the server — showing saved data"
                )
            }
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // ── Navigation & Flow ────────────────────────────────────────────

    fun onSelectPartyFromGrid(partyShortName: String) {
        orgTree = OrgTree.Empty
        rawEntitiesList = emptyList()
        _uiState.update {
            it.copy(
                selectedParty = partyShortName,
                currentScreen = FeatureScreen.WORKSPACE,
                activeSubModule = WorkspaceSubModule.HIERARCHY,
                searchQuery = "",
                searchResults = emptyList(),
                allNodes = emptyList(),
                chart = HierarchyChart.Empty,
                collapsedUnitIds = emptySet(),
                selectedNodeDetail = null,
                focusedNodeId = null,
                // The location filter is shared across parties; a map pick is not.
                selectedState = it.resolvedLocation?.state,
                selectedDistrict = it.resolvedLocation?.district,
                isLoading = true
            )
        }
        partyJob?.cancel()
        partyJob = viewModelScope.launch {
            partyNodeDao.observeByParty(partyShortName).collect { entities ->
                // Rows older than the current seed (or none at all) are replaced once per
                // process, so devices holding last release's stale names pick up the fix.
                val seed = PartySeedData.nodesFor(partyShortName)
                val needsSeed = seed.isNotEmpty() && entities.none { it.versionDate >= PartySeedData.SEED_DATE }
                if (needsSeed && seedAttempted.add(partyShortName)) {
                    partyNodeDao.replaceStaleSeed(partyShortName, PartySeedData.SEED_DATE, seed) // re-emits
                } else {
                    rawEntitiesList = entities
                    rebuild()
                }
            }
        }
    }

    /** Opens a party's workspace, optionally focused on one post. */
    fun openParty(partyShortName: String, nodeId: String?) {
        val state = _uiState.value
        if (state.currentScreen == FeatureScreen.PARTY_SELECTOR || state.selectedParty != partyShortName) {
            onSelectPartyFromGrid(partyShortName)
        }
        if (nodeId == null) return
        if (orgTree.unitOf(nodeId) != null) locateInHierarchy(nodeId) else pendingLocate = nodeId
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
        expandPathTo(nodeId)
        _uiState.update {
            it.copy(
                currentScreen = FeatureScreen.WORKSPACE,
                activeSubModule = WorkspaceSubModule.HIERARCHY,
                focusedNodeId = nodeId,
                focusRequest = it.focusRequest + 1,
                selectedProfile = null,
                selectedNodeDetail = null
            )
        }
    }

    // ── Search ───────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchResults = search(it.allNodes, query)) }
    }

    fun onSearchResultChosen(node: PartyTreeNode) {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        locateInHierarchy(node.id)
    }

    private fun search(nodes: List<PartyTreeNode>, query: String): List<PartyTreeNode> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return nodes
            .filter { n ->
                // The "Not yet fetched" placeholder is not a name; matching it would hit every vacancy.
                listOfNotNull(n.holderName.takeUnless { n.isNotFetched }, n.roleTitle, n.state, n.district)
                    .any { it.contains(q, ignoreCase = true) }
            }
            .sortedWith(compareBy({ it.isNotFetched }, { it.level.tier }))
            .take(20)
    }

    // ── Hierarchy Chart Interactions ─────────────────────────────────

    fun onNodeClicked(node: PartyTreeNode) {
        _uiState.update { it.copy(selectedNodeDetail = node) }
    }

    fun dismissNodeDetail() {
        _uiState.update { it.copy(selectedNodeDetail = null) }
    }

    fun toggleUnit(unitId: String) {
        val current = _uiState.value.collapsedUnitIds
        relayout(if (unitId in current) current - unitId else current + unitId)
    }

    fun setAllExpanded(expanded: Boolean) {
        relayout(if (expanded) emptySet() else orgTree.defaultCollapsed())
    }

    private fun relayout(collapsed: Set<String>) {
        _uiState.update { it.copy(collapsedUnitIds = collapsed, chart = layoutOrgChart(orgTree, collapsed)) }
    }

    private fun expandPathTo(nodeId: String) {
        val unit = orgTree.unitOf(nodeId) ?: return
        val path = orgTree.ancestors(unit.id).toSet()
        val current = _uiState.value.collapsedUnitIds
        if (current.any { it in path }) relayout(current - path)
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
     * Updates shared location state across Hierarchy, Map, and Analytics,
     * then focuses the chart on the deepest position covering that place.
     */
    fun onLocationResolved(location: ResolvedLocation?) {
        locationStore.location.value = location
        applyLocation(location)
    }

    private fun applyLocation(location: ResolvedLocation?) {
        _uiState.update {
            it.copy(
                resolvedLocation = location,
                selectedState = location?.state,
                selectedDistrict = location?.district,
                focusedNodeId = if (location == null) null else it.focusedNodeId,
                isLocationLoading = false
            )
        }
        val state = location?.state ?: return

        // Walk from booth up to state; first level with a match in this place wins.
        val nodes = _uiState.value.allNodes
        val deepest = LOCAL_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
            nodes.firstOrNull { n ->
                n.level == level &&
                    n.state.equals(state, ignoreCase = true) &&
                    (location.district == null || n.district.isNullOrEmpty() ||
                        n.district.equals(location.district, ignoreCase = true))
            }
        } ?: return

        expandPathTo(deepest.id)
        _uiState.update { it.copy(focusedNodeId = deepest.id, focusRequest = it.focusRequest + 1) }
    }

    // ── State Computation ────────────────────────────────────────────

    private fun rebuild() {
        val firstLoad = orgTree.units.isEmpty()
        val nodes = rawEntitiesList.map { it.toTreeNode() }
        orgTree = buildOrgTree(nodes)
        val collapsed = if (firstLoad) orgTree.defaultCollapsed() else _uiState.value.collapsedUnitIds
        _uiState.update {
            it.copy(
                allNodes = nodes,
                chart = layoutOrgChart(orgTree, collapsed),
                collapsedUnitIds = collapsed,
                stateGeoDataList = calculateStateGeoData(rawEntitiesList),
                searchResults = search(nodes, it.searchQuery),
                isLoading = false
            )
        }
        pendingLocate?.let { id ->
            if (orgTree.unitOf(id) != null) {
                pendingLocate = null
                locateInHierarchy(id)
            }
        }
    }

    private fun PartyNodeEntity.toTreeNode(): PartyTreeNode {
        val levelEnum = HierarchyLevel.fromString(level)
        val notFetched = holderName.isBlank() || holderName.contains("Not yet fetched", ignoreCase = true)
        return PartyTreeNode(
            id = id,
            partyName = partyName,
            level = levelEnum,
            parentId = parentId,
            roleTitle = roleTitle,
            holderName = holderName,
            state = state,
            district = district,
            // Photo lookup matches by surname, so it is limited to the top two tiers
            // where the bundled names are unambiguous (a lower-tier "Gandhi" or "Stalin"
            // would otherwise get the wrong face).
            photoUrl = if (levelEnum.tier <= 2 && !notFetched) getLeaderPhotoUrl(holderName) else null,
            // "Verified" means a cited source exists, not merely that a name is present.
            verificationStatus = when {
                notFetched -> VerificationStatus.NotYetFetched
                PartySeedData.sourceFor(id) != null -> VerificationStatus.Verified
                else -> VerificationStatus.PendingVerification
            }
        )
    }

    companion object {
        /** Parties with a bundled seed hierarchy, keyed by catalog short name. */
        val SEEDED_PARTIES: Set<String> get() = PartySeedData.parties

        private val LOCAL_LEVEL_PRIORITY = listOf(
            HierarchyLevel.BOOTH, HierarchyLevel.WARD_GS, HierarchyLevel.WARD,
            HierarchyLevel.TALUKA_GS, HierarchyLevel.TALUKA_MANDAL,
            HierarchyLevel.CONSTITUENCY,
            HierarchyLevel.DISTRICT_SECRETARY, HierarchyLevel.DISTRICT_GS,
            HierarchyLevel.DISTRICT_WORKING, HierarchyLevel.DISTRICT,
            HierarchyLevel.STATE_TREASURER, HierarchyLevel.STATE_SECRETARY,
            HierarchyLevel.STATE_GS_ORG, HierarchyLevel.STATE_GS,
            HierarchyLevel.STATE_VP, HierarchyLevel.STATE_WORKING, HierarchyLevel.STATE
        )
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
                leaderCount = stateEntities.size,
                districtCount = districtGroups.size.coerceAtLeast(1),
                topLeaderName = topLeader.holderName,
                topLeaderRole = topLeader.roleTitle,
                representationScore = (stateEntities.size.toFloat() / maxLeaders).coerceIn(0.2f, 1.0f),
                districts = districts
            )
        }.sortedByDescending { it.leaderCount }
    }

    /**
     * Profile copy exists only for leaders whose career facts are well documented.
     * Everyone else gets an honest "not on record" profile rather than filler — the old
     * fallback gave every named holder a made-up degree and "Verified" source badges.
     */
    private fun generatePoliticianProfile(node: PartyTreeNode): PoliticianProfile {
        val name = node.holderName
        val (bio, education, milestones) = when {
            name.contains("Narendra Modi", true) -> Triple(
                "Prime Minister of India since 2014 and the BJP's parliamentary leader. Chief Minister of Gujarat from 2001 to 2014. Member of Parliament for Varanasi.",
                "MA in Political Science, Gujarat University",
                listOf(
                    TimelineMilestone("2001", "Chief Minister of Gujarat", "Became CM of Gujarat; held the office until 2014.", "Appointment"),
                    TimelineMilestone("2014", "Prime Minister", "Led the BJP to a Lok Sabha majority.", "Election"),
                    TimelineMilestone("2019", "Re-elected", "Second consecutive term.", "Election"),
                    TimelineMilestone("2024", "Third term", "Sworn in for a third consecutive term.", "Election")
                )
            )
            name.contains("Nitin Nabin", true) -> Triple(
                "National President of the BJP since 20 January 2026 — at 45 the youngest in the party's history and its first from Bihar. A Bihar MLA since 2006 and formerly National General Secretary of the BJP's youth wing (BJYM).",
                "Not on record",
                listOf(
                    TimelineMilestone("2006", "Elected MLA in Bihar", "First elected to the Bihar Legislative Assembly.", "Election"),
                    TimelineMilestone("2025", "National Working President", "Appointed the BJP's National Working President while a Bihar minister.", "Appointment"),
                    TimelineMilestone("2026", "National President", "Elected unopposed on 20 January, succeeding J. P. Nadda.", "Election")
                )
            )
            name.contains("Kharge", true) -> Triple(
                "President of the Indian National Congress since October 2022.",
                "BA and LLB, Gulbarga (now Kalaburagi)",
                listOf(
                    TimelineMilestone("1972", "Elected MLA from Gurmitkal", "Won nine consecutive Karnataka assembly elections from the seat.", "Election"),
                    TimelineMilestone("2009", "Union Minister", "Minister in the UPA government.", "Appointment"),
                    TimelineMilestone("2022", "Congress President", "First non-Gandhi party president in 24 years.", "Election")
                )
            )
            name.contains("Kejriwal", true) -> Triple(
                "National Convenor of the Aam Aadmi Party. Former Indian Revenue Service officer; Chief Minister of Delhi 2013–14 and 2015–24.",
                "B.Tech in Mechanical Engineering, IIT Kharagpur",
                listOf(
                    TimelineMilestone("2006", "Ramon Magsaysay Award", "Emergent Leadership, for work on the right to information.", "Milestone"),
                    TimelineMilestone("2012", "Founded the Aam Aadmi Party", "Launched from the India Against Corruption movement.", "Milestone"),
                    TimelineMilestone("2015", "Delhi Assembly victory", "AAP won 67 of 70 seats.", "Election")
                )
            )
            name.contains("Sharad Pawar", true) -> Triple(
                "Founder and National President of the Nationalist Congress Party (Sharadchandra Pawar). Four-time Chief Minister of Maharashtra.",
                "B.Com., Brihan Maharashtra College of Commerce, Pune",
                listOf(
                    TimelineMilestone("1967", "First elected to the Maharashtra Assembly", "Elected from Baramati.", "Election"),
                    TimelineMilestone("1991", "Union Defence Minister", "Defence Minister in the Narasimha Rao government.", "Appointment"),
                    TimelineMilestone("1999", "Founded the NCP", "Formed the Nationalist Congress Party after leaving the Congress.", "Milestone"),
                    TimelineMilestone("2004", "Union Agriculture Minister", "Held agriculture in the UPA government until 2014.", "Appointment")
                )
            )
            name.contains("Mamata", true) -> Triple(
                "Founder and Chairperson of the All India Trinamool Congress. Chief Minister of West Bengal from 2011 until the party's defeat in May 2026.",
                "Law graduate, Jogesh Chandra Chaudhuri Law College, Kolkata",
                listOf(
                    TimelineMilestone("1984", "Elected to the Lok Sabha", "Won Jadavpur.", "Election"),
                    TimelineMilestone("1998", "Founded the TMC", "Formed the All India Trinamool Congress.", "Milestone"),
                    TimelineMilestone("2011", "Chief Minister of West Bengal", "Ended 34 years of Left Front rule.", "Election"),
                    TimelineMilestone("2026", "Out of office", "The BJP won the West Bengal assembly election.", "Election")
                )
            )
            name.contains("M. K. Stalin", true) -> Triple(
                "President of the DMK. Chief Minister of Tamil Nadu from 2021 until the party's defeat in May 2026.",
                "Presidency College, Chennai",
                listOf(
                    TimelineMilestone("1989", "Elected to the Tamil Nadu Assembly", "First elected from Thousand Lights.", "Election"),
                    TimelineMilestone("2018", "DMK President", "Elected president after M. Karunanidhi's death.", "Appointment"),
                    TimelineMilestone("2021", "Chief Minister", "Led the DMK back to power.", "Election"),
                    TimelineMilestone("2026", "Out of office", "TVK won the most seats; C. Joseph Vijay became Chief Minister.", "Election")
                )
            )
            else -> Triple(
                "No biography on record for this post-holder yet.",
                "Not on record",
                emptyList()
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
            photoUrl = node.photoUrl,
            bio = bio,
            education = education,
            timeline = milestones,
            keyAchievements = emptyList(),
            verifiedSources = listOfNotNull(PartySeedData.sourceFor(node.id)),
            verificationStatus = node.verificationStatus
        )
    }


}
