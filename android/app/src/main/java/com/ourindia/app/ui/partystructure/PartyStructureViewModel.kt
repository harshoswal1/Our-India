package com.ourindia.app.ui.partystructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.data.leaders.LeaderUpdateDao
import com.ourindia.app.data.leaders.LeaderUpdateEntity
import com.ourindia.app.data.leaders.LeaderUpdateRepository
import com.ourindia.app.data.leaders.WikiLeaders
import com.ourindia.app.data.local.dao.PartyNodeDao
import com.ourindia.app.data.local.entity.PartyNodeEntity
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
    private val leaderUpdateDao: LeaderUpdateDao,
    private val leaderUpdates: LeaderUpdateRepository,
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

    /** Wikipedia readings by node id; applied over the seed in [toTreeNode]. */
    private var updates: Map<String, LeaderUpdateEntity> = emptyMap()

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
        viewModelScope.launch {
            leaderUpdateDao.observeAll().collect { rows ->
                updates = rows.associateBy { it.nodeId }
                if (rawEntitiesList.isNotEmpty()) rebuild()
            }
        }
        viewModelScope.launch {
            leaderUpdateDao.observeLastChecked().collect { d -> _uiState.update { it.copy(leadersCheckedAt = d) } }
        }
    }

    /** Checks Wikipedia now instead of waiting for the monthly job. Room re-emits on change. */
    fun refresh() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val result = leaderUpdates.check()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    userMessage = result.fold(
                        onSuccess = { n -> if (n == 0) "Checked Wikipedia — no leadership changes" else "Checked Wikipedia — $n change${if (n == 1) "" else "s"} found" },
                        onFailure = { "Couldn't reach Wikipedia — showing saved data" }
                    )
                )
            }
        }
    }

    fun setStructureView(view: StructureView) = _uiState.update { it.copy(structureView = view) }

    fun focusOrbit(unitId: String) = _uiState.update { it.copy(orbitUnitId = unitId) }

    /** Moves the orbit one level up; false when already at the top (back then leaves the party). */
    fun orbitUp(): Boolean {
        val s = _uiState.value
        if (s.structureView != StructureView.ORBIT || s.activeSubModule != WorkspaceSubModule.HIERARCHY) return false
        val parent = s.orbitUnitId?.let { orgTree.units[it]?.parentId } ?: return false
        _uiState.update { it.copy(orbitUnitId = parent) }
        return true
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
                orgTree = OrgTree.Empty,
                orbitUnitId = null,
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
                // Rows older than the current seed, fewer than 150, or none at all are replaced once per
                // process, so devices holding last release's stale names pick up the fix.
                val seed = PartySeedData.nodesFor(partyShortName)
                val needsSeed = seed.isNotEmpty() && (entities.size < 150 || entities.none { it.versionDate >= PartySeedData.SEED_DATE })
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
                orbitUnitId = orgTree.unitOf(nodeId)?.id ?: it.orbitUnitId,
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
        // The chart pans to the card; the orbit centres the unit, so also open the person.
        if (_uiState.value.structureView == StructureView.ORBIT) _uiState.update { it.copy(selectedNodeDetail = node) }
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
        _uiState.update {
            it.copy(focusedNodeId = deepest.id, focusRequest = it.focusRequest + 1, orbitUnitId = orgTree.unitOf(deepest.id)?.id ?: it.orbitUnitId)
        }
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
                orgTree = orgTree,
                // Keep the orbit where it was across data refreshes; fall back to the root.
                orbitUnitId = it.orbitUnitId?.takeIf { id -> id in orgTree.units } ?: orgTree.rootIds.firstOrNull(),
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
        val update = updates[id]
        val wikiHolder = WikiLeaders.override(holderName, update)
        val holder = wikiHolder ?: holderName
        val notFetched = holder.isBlank() || holder == WikiLeaders.VACANT || holder.contains("Not yet fetched", ignoreCase = true)
        val source = if (wikiHolder != null && update != null) {
            VerifiedSource(
                title = "Changed on Wikipedia: ${update.pageTitle}",
                authority = "Auto-checked, not yet verified by Our India",
                date = update.changedAt.orEmpty(),
                referenceUrl = "https://en.wikipedia.org/wiki/" + android.net.Uri.encode(update.pageTitle.replace(' ', '_'))
            )
        } else PartySeedData.sourceFor(id)
        return PartyTreeNode(
            id = id,
            partyName = partyName,
            level = levelEnum,
            parentId = parentId,
            roleTitle = roleTitle,
            holderName = holder,
            state = state,
            district = district,
            // Photo lookup matches by surname, so it is limited to the top two tiers
            // where the bundled names are unambiguous (a lower-tier "Gandhi" or "Stalin"
            // would otherwise get the wrong face).
            photoUrl = if (levelEnum.tier <= 2 && !notFetched) getLeaderPhotoUrl(holder) else null,
            // "Verified" means we cited a source ourselves; a Wikipedia change is shown, linked,
            // and marked pending until a release re-verifies it.
            verificationStatus = when {
                notFetched -> VerificationStatus.NotYetFetched
                wikiHolder != null -> VerificationStatus.PendingVerification
                source != null -> VerificationStatus.Verified
                else -> VerificationStatus.PendingVerification
            },
            source = source
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
            verifiedSources = listOfNotNull(node.source),
            verificationStatus = node.verificationStatus
        )
    }



    private suspend fun seedMockPartyData() {
        val seeds = generateSeedNodes(_uiState.value.selectedParty)
        partyNodeDao.deleteByParty(_uiState.value.selectedParty)
        partyNodeDao.insertAll(seeds)
    }

    private suspend fun seedMockPartyDataForParty(party: String) {
        val seeds = generateSeedNodes(party)
        partyNodeDao.deleteByParty(party)
        partyNodeDao.insertAll(seeds)
    }

    /**
     * Generates a comprehensive position-driven hierarchy of 220+ boxes for ANY party.
     * Includes National Apex Leadership, Frontal Wings/Morchas, State Units, District Committees,
     * Mandal/Taluka Executives, Ward Committees, and Polling Booth Committees / Panna Pramukhs.
     */
    private fun generateSeedNodes(party: String): List<PartyNodeEntity> {
        val date = "2026-08-01"
        val nodes = mutableListOf<PartyNodeEntity>()
        val posPlaceholder = "[Position Active]"

        val meta = PartyCatalog.getParty(party)
        val pName = meta.shortName
        val rootLeader = if (meta.president.isNotBlank() && meta.president != "N/A") meta.president else "${pName} National President"
        val primaryState = meta.primaryState ?: if (meta.classification == PartyClassification.NATIONAL) "Maharashtra" else "Delhi"
        val secondState = when (primaryState) {
            "Maharashtra" -> "Uttar Pradesh"
            "Uttar Pradesh" -> "Bihar"
            "Bihar" -> "Jharkhand"
            "Tamil Nadu" -> "Puducherry"
            "West Bengal" -> "Tripura"
            "Andhra Pradesh" -> "Telangana"
            "Telangana" -> "Andhra Pradesh"
            "Karnataka" -> "Kerala"
            "Kerala" -> "Tamil Nadu"
            "Punjab" -> "Haryana"
            "Haryana" -> "Punjab"
            "Rajasthan" -> "Madhya Pradesh"
            "Gujarat" -> "Maharashtra"
            "Odisha" -> "West Bengal"
            "Assam" -> "Meghalaya"
            "Meghalaya" -> "Assam"
            "Tripura" -> "Assam"
            "Goa" -> "Maharashtra"
            "Jammu & Kashmir" -> "Delhi"
            else -> "Delhi"
        }
        val thirdState = when {
            primaryState != "Karnataka" && secondState != "Karnataka" -> "Karnataka"
            primaryState != "Gujarat" && secondState != "Gujarat" -> "Gujarat"
            else -> "Madhya Pradesh"
        }

        // District names based on primary state
        val (dist1, dist2, dist3) = when (primaryState) {
            "Maharashtra" -> Triple("Mumbai", "Pune", "Nagpur")
            "Uttar Pradesh" -> Triple("Lucknow", "Varanasi", "Kanpur")
            "Bihar" -> Triple("Patna", "Gaya", "Muzaffarpur")
            "Tamil Nadu" -> Triple("Chennai", "Coimbatore", "Madurai")
            "West Bengal" -> Triple("Kolkata", "North 24 Parganas", "Howrah")
            "Andhra Pradesh" -> Triple("Amaravati", "Visakhapatnam", "Guntur")
            "Telangana" -> Triple("Hyderabad", "Secunderabad", "Warangal")
            "Karnataka" -> Triple("Bengaluru Urban", "Mysuru", "Hubballi")
            "Kerala" -> Triple("Thiruvananthapuram", "Kochi", "Kozhikode")
            "Punjab" -> Triple("Amritsar", "Ludhiana", "Jalandhar")
            "Haryana" -> Triple("Gurugram", "Faridabad", "Ambala")
            "Rajasthan" -> Triple("Jaipur", "Jodhpur", "Udaipur")
            "Gujarat" -> Triple("Ahmedabad", "Surat", "Vadodara")
            "Odisha" -> Triple("Bhubaneswar", "Cuttack", "Puri")
            "Jharkhand" -> Triple("Ranchi", "Jamshedpur", "Dhanbad")
            "Assam" -> Triple("Guwahati", "Dibrugarh", "Silchar")
            "Meghalaya" -> Triple("East Khasi Hills", "West Garo Hills", "Ri-Bhoi")
            "Goa" -> Triple("North Goa", "South Goa", "Panaji")
            "Jammu & Kashmir" -> Triple("Srinagar", "Jammu", "Anantnag")
            else -> Triple("Central District", "North District", "South District")
        }

        val dist4 = "Capital District"

        // Specific verified leaders for prominent parties
        val leaderLookup = when (pName.uppercase()) {
            "BJP" -> mapOf(
                "ROOT" to "J. P. Nadda",
                "PM" to "Narendra Modi",
                "WP" to "Narendra Modi",
                "VP1" to "Shivraj Singh Chouhan",
                "VP2" to "Vasundhara Raje",
                "VP3" to "Raman Singh",
                "VP4" to "Baijayant Panda",
                "GS_ORG" to "B. L. Santhosh",
                "GS1" to "Arun Singh",
                "GS2" to "Tarun Chugh",
                "GS3" to "Vinod Tawde",
                "GS4" to "Bandi Sanjay Kumar",
                "TREAS" to "Rajesh Agarwal",
                "ST1_PRES" to "Chandrashekhar Bawankule",
                "ST1_LEADER" to "Devendra Fadnavis",
                "ST2_PRES" to "Bhupendra Chaudhary",
                "ST2_LEADER" to "Yogi Adityanath",
                "ST3_PRES" to "C. R. Paatil",
                "D1_PRES" to "Ashish Shelar",
                "D2_PRES" to "Dhiraj Ghate",
                "D3_PRES" to "Jitendra Kukde"
            )
            "INC" -> mapOf(
                "ROOT" to "Mallikarjun Kharge",
                "PM" to "Rahul Gandhi",
                "WP" to "Rahul Gandhi",
                "VP1" to "Priyanka Gandhi Vadra",
                "VP2" to "Sachin Pilot",
                "VP3" to "Kumari Selja",
                "VP4" to "Mukul Wasnik",
                "GS_ORG" to "K. C. Venugopal",
                "GS1" to "Jairam Ramesh",
                "GS2" to "Randeep Surjewala",
                "GS3" to "Jitendra Singh",
                "GS4" to "Deepak Babaria",
                "TREAS" to "Ajay Maken",
                "ST1_PRES" to "Nana Patole",
                "ST1_LEADER" to "Prithviraj Chavan",
                "ST2_PRES" to "Ajay Rai",
                "ST3_PRES" to "D. K. Shivakumar",
                "D1_PRES" to "Varsha Gaikwad",
                "D2_PRES" to "Arvind Shinde",
                "D3_PRES" to "Vikas Thakre"
            )
            "AAP" -> mapOf(
                "ROOT" to "Arvind Kejriwal",
                "WP" to "Manish Sisodia",
                "VP1" to "Sanjay Singh",
                "VP2" to "Atishi",
                "VP3" to "Raghav Chadha",
                "GS_ORG" to "Sandeep Pathak",
                "GS1" to "Saurabh Bharadwaj",
                "GS2" to "Durgesh Pathak",
                "ST1_PRES" to "Gopal Rai",
                "ST2_PRES" to "Bhagwant Mann",
                "ST3_PRES" to "Isudan Gadhvi"
            )
            "TMC" -> mapOf(
                "ROOT" to "Mamata Banerjee",
                "WP" to "Abhishek Banerjee",
                "VP1" to "Sudip Bandyopadhyay",
                "VP2" to "Derek O'Brien",
                "GS_ORG" to "Abhishek Banerjee",
                "GS1" to "Kalyan Banerjee",
                "ST1_PRES" to "Subrata Bakshi"
            )
            "DMK" -> mapOf(
                "ROOT" to "M. K. Stalin",
                "WP" to "Duraimurugan",
                "VP1" to "Udhayanidhi Stalin",
                "VP2" to "T. R. Baalu",
                "GS_ORG" to "Duraimurugan",
                "GS1" to "Kanimozhi Karunanidhi",
                "ST1_PRES" to "M. K. Stalin"
            )
            "SP" -> mapOf(
                "ROOT" to "Akhilesh Yadav",
                "WP" to "Ram Gopal Yadav",
                "VP1" to "Shivpal Singh Yadav",
                "VP2" to "Dimple Yadav",
                "GS_ORG" to "Ram Gopal Yadav",
                "GS1" to "Awadhesh Prasad",
                "ST1_PRES" to "Naresh Uttam Patel"
            )
            "SHS" -> mapOf(
                "ROOT" to "Eknath Shinde",
                "WP" to "Shrikant Shinde",
                "VP1" to "Uday Samant",
                "VP2" to "Deepak Kesarkar",
                "GS_ORG" to "Rahul Shewale",
                "ST1_PRES" to "Eknath Shinde"
            )
            "SS(UBT)" -> mapOf(
                "ROOT" to "Uddhav Thackeray",
                "WP" to "Aaditya Thackeray",
                "VP1" to "Sanjay Raut",
                "VP2" to "Arvind Sawant",
                "GS_ORG" to "Anil Parab",
                "ST1_PRES" to "Uddhav Thackeray"
            )
            "NCP", "NCP(SP)" -> mapOf(
                "ROOT" to if (pName == "NCP") "Ajit Pawar" else "Sharad Pawar",
                "WP" to if (pName == "NCP") "Praful Patel" else "Supriya Sule",
                "VP1" to if (pName == "NCP") "Sunil Tatkare" else "Jayant Patil",
                "ST1_PRES" to if (pName == "NCP") "Sunil Tatkare" else "Jayant Patil"
            )
            "TDP" -> mapOf(
                "ROOT" to "N. Chandrababu Naidu",
                "WP" to "Nara Lokesh",
                "VP1" to "Kinjarapu Ram Mohan Naidu",
                "VP2" to "Pemmasani Chandrasekhar",
                "ST1_PRES" to "K. Atchannaidu"
            )
            "YSRCP" -> mapOf(
                "ROOT" to "Y. S. Jagan Mohan Reddy",
                "VP1" to "V. Vijayasai Reddy",
                "VP2" to "Y. V. Subba Reddy",
                "ST1_PRES" to "Y. S. Jagan Mohan Reddy"
            )
            "BJD" -> mapOf(
                "ROOT" to "Naveen Patnaik",
                "VP1" to "V. K. Pandian",
                "VP2" to "Sasmit Patra",
                "ST1_PRES" to "Naveen Patnaik"
            )
            else -> emptyMap()
        }

        fun nameFor(key: String, default: String): String = leaderLookup[key] ?: default

        // ═════════════════════════════════════════════════════════════════
        // 1. NATIONAL APEX LEADERSHIP (~45 boxes)
        // ═════════════════════════════════════════════════════════════════
        val rootId = "${pName}_NAT_ROOT"
        nodes.add(PartyNodeEntity(rootId, pName, "NATIONAL", null, "National President / Party Chief", nameFor("ROOT", rootLeader), null, null, date))

        val natWpId = "${pName}_NAT_WP"
        nodes.add(PartyNodeEntity(natWpId, pName, "NATIONAL_WORKING", rootId, "National Working President", nameFor("WP", posPlaceholder), null, null, date))

        // National Vice Presidents (6)
        val natVpTitles = listOf(
            "National Vice President (Senior)",
            "National Vice President (Policy & Research)",
            "National Vice President (Electoral Affairs)",
            "National Vice President (Public Outreach)",
            "National Vice President (Grassroots Cadre)",
            "National Vice President (Special Initiatives)"
        )
        for (i in 0 until 6) {
            val key = "VP${i + 1}"
            val fallback = if (i == 0) nameFor(key, posPlaceholder) else posPlaceholder
            nodes.add(PartyNodeEntity("${pName}_NAT_VP_${i + 1}", pName, "NATIONAL_VP", rootId, natVpTitles[i], nameFor(key, fallback), null, null, date))
        }

        // General Secretary (Organisation) - Sangathan Mantri
        val natGsOrgId = "${pName}_NAT_GS_ORG"
        nodes.add(PartyNodeEntity(natGsOrgId, pName, "NATIONAL_GS_ORG", rootId, "General Secretary (Organisation)", nameFor("GS_ORG", posPlaceholder), null, null, date))

        // National General Secretaries (8)
        val gsTitles = listOf(
            "National General Secretary (Parliamentary Affairs)",
            "National General Secretary (Media & Communications)",
            "National General Secretary (Western Region)",
            "National General Secretary (Northern Region)",
            "National General Secretary (Southern Region)",
            "National General Secretary (Eastern Region)",
            "National General Secretary (Training & Coordination)",
            "National General Secretary (Special Programs)"
        )
        for (i in 0 until 8) {
            val key = "GS${i + 1}"
            nodes.add(PartyNodeEntity("${pName}_NAT_GS_${i + 1}", pName, "NATIONAL_GS", rootId, gsTitles[i], nameFor(key, posPlaceholder), null, null, date))
        }

        // National Secretaries (10)
        val secTitles = listOf(
            "National Secretary (Youth Engagement)",
            "National Secretary (Women Empowerment)",
            "National Secretary (Farmer Welfare)",
            "National Secretary (OBC & Backward Classes)",
            "National Secretary (SC & Dalit Affairs)",
            "National Secretary (ST & Tribal Welfare)",
            "National Secretary (Minority Cell Coordination)",
            "National Secretary (Digital Outreach)",
            "National Secretary (Electoral Logistics)",
            "National Secretary (Discipline & Grievance)"
        )
        for (i in 0 until 10) {
            nodes.add(PartyNodeEntity("${pName}_NAT_SEC_${i + 1}", pName, "NATIONAL_SECRETARY", rootId, secTitles[i], posPlaceholder, null, null, date))
        }

        // National Joint Secretaries (4)
        for (i in 1..4) {
            nodes.add(PartyNodeEntity("${pName}_NAT_JT_SEC_${i}", pName, "NATIONAL_JOINT_SECRETARY", rootId, "National Joint Secretary ($i)", posPlaceholder, null, null, date))
        }

        // National Treasurers (2)
        nodes.add(PartyNodeEntity("${pName}_NAT_TREAS", pName, "NATIONAL_TREASURER", rootId, "National Treasurer", nameFor("TREAS", posPlaceholder), null, null, date))
        nodes.add(PartyNodeEntity("${pName}_NAT_JT_TREAS", pName, "NATIONAL_TREASURER", rootId, "National Joint Treasurer", posPlaceholder, null, null, date))

        // National Spokespersons (6)
        for (i in 1..6) {
            nodes.add(PartyNodeEntity("${pName}_NAT_SPOKES_${i}", pName, "NATIONAL", rootId, "National Spokesperson Panel ($i)", posPlaceholder, null, null, date))
        }

        // National Frontal Wings / Morchas (12 wings)
        val frontalWings = listOf(
            "Yuva Morcha / Youth Wing President",
            "Mahila Morcha / Women Wing President",
            "Kisan Morcha / Farmers Cell President",
            "OBC Morcha National Convener",
            "SC Morcha National Convener",
            "ST Morcha National Convener",
            "Minority Morcha National Convener",
            "IT & Social Media Department Head",
            "Legal & Human Rights Cell Convener",
            "Medical & Healthcare Cell Convener",
            "Vyapar / Traders Morcha Convener",
            "Shramik / Labour Cell Convener"
        )
        for (i in frontalWings.indices) {
            nodes.add(PartyNodeEntity("${pName}_NAT_WING_${i + 1}", pName, "NATIONAL_SECRETARY", rootId, frontalWings[i], posPlaceholder, null, null, date))
        }

        // ═════════════════════════════════════════════════════════════════
        // 2. STATE UNITS (3 States, ~53 boxes)
        // ═════════════════════════════════════════════════════════════════
        // ── State 1 (Primary Home State, ~25 boxes) ───────────────────────
        val st1Id = "${pName}_STATE_1"
        nodes.add(PartyNodeEntity(st1Id, pName, "STATE", rootId, "State President ($primaryState)", nameFor("ST1_PRES", posPlaceholder), primaryState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST1_WP", pName, "STATE_WORKING", st1Id, "State Working President", nameFor("ST1_LEADER", posPlaceholder), primaryState, null, date))

        for (i in 1..4) {
            nodes.add(PartyNodeEntity("${pName}_ST1_VP_${i}", pName, "STATE_VP", st1Id, "State Vice President ($i)", posPlaceholder, primaryState, null, date))
        }
        val st1GsOrgId = "${pName}_ST1_GS_ORG"
        nodes.add(PartyNodeEntity(st1GsOrgId, pName, "STATE_GS_ORG", st1Id, "State GS (Organisation)", posPlaceholder, primaryState, null, date))

        for (i in 1..4) {
            nodes.add(PartyNodeEntity("${pName}_ST1_GS_${i}", pName, "STATE_GS", st1Id, "State General Secretary ($i)", posPlaceholder, primaryState, null, date))
        }
        for (i in 1..6) {
            nodes.add(PartyNodeEntity("${pName}_ST1_SEC_${i}", pName, "STATE_SECRETARY", st1Id, "State Secretary ($i)", posPlaceholder, primaryState, null, date))
        }
        nodes.add(PartyNodeEntity("${pName}_ST1_TREAS", pName, "STATE_TREASURER", st1Id, "State Treasurer", posPlaceholder, primaryState, null, date))

        val stateWings = listOf("Youth Wing Chief", "Mahila Morcha Chief", "Kisan Cell Chief", "IT & Social Media Head", "Legal Cell Convener")
        for (i in stateWings.indices) {
            nodes.add(PartyNodeEntity("${pName}_ST1_WING_${i + 1}", pName, "STATE_SECRETARY", st1Id, "State ${stateWings[i]}", posPlaceholder, primaryState, null, date))
        }

        // ── State 2 (~16 boxes) ──────────────────────────────────────────
        val st2Id = "${pName}_STATE_2"
        nodes.add(PartyNodeEntity(st2Id, pName, "STATE", rootId, "State President ($secondState)", nameFor("ST2_PRES", posPlaceholder), secondState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST2_WP", pName, "STATE_WORKING", st2Id, "State Working President", nameFor("ST2_LEADER", posPlaceholder), secondState, null, date))

        for (i in 1..3) {
            nodes.add(PartyNodeEntity("${pName}_ST2_VP_${i}", pName, "STATE_VP", st2Id, "State Vice President ($i)", posPlaceholder, secondState, null, date))
        }
        nodes.add(PartyNodeEntity("${pName}_ST2_GS_ORG", pName, "STATE_GS_ORG", st2Id, "State GS (Organisation)", posPlaceholder, secondState, null, date))
        for (i in 1..3) {
            nodes.add(PartyNodeEntity("${pName}_ST2_GS_${i}", pName, "STATE_GS", st2Id, "State General Secretary ($i)", posPlaceholder, secondState, null, date))
        }
        for (i in 1..4) {
            nodes.add(PartyNodeEntity("${pName}_ST2_SEC_${i}", pName, "STATE_SECRETARY", st2Id, "State Secretary ($i)", posPlaceholder, secondState, null, date))
        }
        nodes.add(PartyNodeEntity("${pName}_ST2_TREAS", pName, "STATE_TREASURER", st2Id, "State Treasurer", posPlaceholder, secondState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST2_YOUTH", pName, "STATE_SECRETARY", st2Id, "State Youth Wing Chief", posPlaceholder, secondState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST2_MAHILA", pName, "STATE_SECRETARY", st2Id, "State Mahila Morcha Chief", posPlaceholder, secondState, null, date))

        // ── State 3 (~12 boxes) ──────────────────────────────────────────
        val st3Id = "${pName}_STATE_3"
        nodes.add(PartyNodeEntity(st3Id, pName, "STATE", rootId, "State President ($thirdState)", nameFor("ST3_PRES", posPlaceholder), thirdState, null, date))
        for (i in 1..2) {
            nodes.add(PartyNodeEntity("${pName}_ST3_VP_${i}", pName, "STATE_VP", st3Id, "State Vice President ($i)", posPlaceholder, thirdState, null, date))
        }
        nodes.add(PartyNodeEntity("${pName}_ST3_GS_ORG", pName, "STATE_GS_ORG", st3Id, "State GS (Organisation)", posPlaceholder, thirdState, null, date))
        for (i in 1..2) {
            nodes.add(PartyNodeEntity("${pName}_ST3_GS_${i}", pName, "STATE_GS", st3Id, "State General Secretary ($i)", posPlaceholder, thirdState, null, date))
        }
        for (i in 1..3) {
            nodes.add(PartyNodeEntity("${pName}_ST3_SEC_${i}", pName, "STATE_SECRETARY", st3Id, "State Secretary ($i)", posPlaceholder, thirdState, null, date))
        }
        nodes.add(PartyNodeEntity("${pName}_ST3_TREAS", pName, "STATE_TREASURER", st3Id, "State Treasurer", posPlaceholder, thirdState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST3_YOUTH", pName, "STATE_SECRETARY", st3Id, "State Youth Wing President", posPlaceholder, thirdState, null, date))
        nodes.add(PartyNodeEntity("${pName}_ST3_MAHILA", pName, "STATE_SECRETARY", st3Id, "State Mahila Morcha President", posPlaceholder, thirdState, null, date))

        // ═════════════════════════════════════════════════════════════════
        // 3. DISTRICT LEVEL (4 Districts, ~31 boxes)
        // ═════════════════════════════════════════════════════════════════
        // ── District 1 (Primary District, e.g. Mumbai/Pune/Chennai, ~13 boxes) ──
        val d1Id = "${pName}_DIST_1"
        nodes.add(PartyNodeEntity(d1Id, pName, "DISTRICT", st1Id, "District President ($dist1)", nameFor("D1_PRES", posPlaceholder), primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_D1_WP", pName, "DISTRICT_WORKING", d1Id, "District Working President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_D1_GS_ORG", pName, "DISTRICT_GS", d1Id, "District GS (Organisation)", posPlaceholder, primaryState, dist1, date))

        for (i in 1..2) {
            nodes.add(PartyNodeEntity("${pName}_D1_GS_${i}", pName, "DISTRICT_GS", d1Id, "District General Secretary ($i)", posPlaceholder, primaryState, dist1, date))
            nodes.add(PartyNodeEntity("${pName}_D1_VP_${i}", pName, "DISTRICT_WORKING", d1Id, "District Vice President ($i)", posPlaceholder, primaryState, dist1, date))
            nodes.add(PartyNodeEntity("${pName}_D1_SEC_${i}", pName, "DISTRICT_SECRETARY", d1Id, "District Secretary ($i)", posPlaceholder, primaryState, dist1, date))
        }
        nodes.add(PartyNodeEntity("${pName}_D1_TREAS", pName, "DISTRICT_SECRETARY", d1Id, "District Treasurer", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_D1_YOUTH", pName, "DISTRICT_SECRETARY", d1Id, "District Youth Wing President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_D1_MAHILA", pName, "DISTRICT_SECRETARY", d1Id, "District Mahila Morcha President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_D1_IT", pName, "DISTRICT_SECRETARY", d1Id, "District IT & Social Media Convener", posPlaceholder, primaryState, dist1, date))

        // ── District 2 (~8 boxes) ─────────────────────────────────────────
        val d2Id = "${pName}_DIST_2"
        nodes.add(PartyNodeEntity(d2Id, pName, "DISTRICT", st1Id, "District President ($dist2)", nameFor("D2_PRES", posPlaceholder), primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_GS_ORG", pName, "DISTRICT_GS", d2Id, "District GS (Organisation)", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_GS_1", pName, "DISTRICT_GS", d2Id, "District General Secretary", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_VP_1", pName, "DISTRICT_WORKING", d2Id, "District Vice President", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_SEC_1", pName, "DISTRICT_SECRETARY", d2Id, "District Secretary", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_TREAS", pName, "DISTRICT_SECRETARY", d2Id, "District Treasurer", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_YOUTH", pName, "DISTRICT_SECRETARY", d2Id, "District Youth Wing Chief", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_D2_MAHILA", pName, "DISTRICT_SECRETARY", d2Id, "District Mahila Morcha Chief", posPlaceholder, primaryState, dist2, date))

        // ── District 3 (~5 boxes) ─────────────────────────────────────────
        val d3Id = "${pName}_DIST_3"
        nodes.add(PartyNodeEntity(d3Id, pName, "DISTRICT", st1Id, "District President ($dist3)", nameFor("D3_PRES", posPlaceholder), primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_D3_GS", pName, "DISTRICT_GS", d3Id, "District General Secretary", posPlaceholder, primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_D3_VP", pName, "DISTRICT_WORKING", d3Id, "District Vice President", posPlaceholder, primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_D3_SEC", pName, "DISTRICT_SECRETARY", d3Id, "District Secretary", posPlaceholder, primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_D3_YOUTH", pName, "DISTRICT_SECRETARY", d3Id, "District Youth Chief", posPlaceholder, primaryState, dist3, date))

        // ── District 4 (under State 2, ~5 boxes) ──────────────────────────
        val d4Id = "${pName}_DIST_4"
        nodes.add(PartyNodeEntity(d4Id, pName, "DISTRICT", st2Id, "District President ($dist4)", posPlaceholder, secondState, dist4, date))
        nodes.add(PartyNodeEntity("${pName}_D4_GS", pName, "DISTRICT_GS", d4Id, "District General Secretary", posPlaceholder, secondState, dist4, date))
        nodes.add(PartyNodeEntity("${pName}_D4_VP", pName, "DISTRICT_WORKING", d4Id, "District Vice President", posPlaceholder, secondState, dist4, date))
        nodes.add(PartyNodeEntity("${pName}_D4_SEC", pName, "DISTRICT_SECRETARY", d4Id, "District Secretary", posPlaceholder, secondState, dist4, date))
        nodes.add(PartyNodeEntity("${pName}_D4_YOUTH", pName, "DISTRICT_SECRETARY", d4Id, "District Youth Chief", posPlaceholder, secondState, dist4, date))

        // ═════════════════════════════════════════════════════════════════
        // 4. TALUKA / MANDAL / BLOCK LEVEL (5 Mandals, ~25 boxes)
        // ═════════════════════════════════════════════════════════════════
        val m1Id = "${pName}_MANDAL_1"
        nodes.add(PartyNodeEntity(m1Id, pName, "TALUKA_MANDAL", d1Id, "Mandal President (Central Assembly Mandal)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_VP_1", pName, "TALUKA_MANDAL", m1Id, "Mandal Vice President (1)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_VP_2", pName, "TALUKA_MANDAL", m1Id, "Mandal Vice President (2)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_GS", pName, "TALUKA_GS", m1Id, "Mandal General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_SEC_1", pName, "TALUKA_GS", m1Id, "Mandal Secretary (1)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_SEC_2", pName, "TALUKA_GS", m1Id, "Mandal Secretary (2)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_YOUTH", pName, "TALUKA_GS", m1Id, "Mandal Youth Wing Incharge", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M1_MAHILA", pName, "TALUKA_GS", m1Id, "Mandal Mahila Morcha Incharge", posPlaceholder, primaryState, dist1, date))

        val m2Id = "${pName}_MANDAL_2"
        nodes.add(PartyNodeEntity(m2Id, pName, "TALUKA_MANDAL", d1Id, "Mandal President (East Assembly Mandal)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M2_VP", pName, "TALUKA_MANDAL", m2Id, "Mandal Vice President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M2_GS", pName, "TALUKA_GS", m2Id, "Mandal General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M2_SEC", pName, "TALUKA_GS", m2Id, "Mandal Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M2_YOUTH", pName, "TALUKA_GS", m2Id, "Mandal Youth Incharge", posPlaceholder, primaryState, dist1, date))

        val m3Id = "${pName}_MANDAL_3"
        nodes.add(PartyNodeEntity(m3Id, pName, "TALUKA_MANDAL", d1Id, "Mandal President (West Assembly Mandal)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M3_VP", pName, "TALUKA_MANDAL", m3Id, "Mandal Vice President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M3_GS", pName, "TALUKA_GS", m3Id, "Mandal General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M3_SEC", pName, "TALUKA_GS", m3Id, "Mandal Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_M3_YOUTH", pName, "TALUKA_GS", m3Id, "Mandal Youth Incharge", posPlaceholder, primaryState, dist1, date))

        val m4Id = "${pName}_MANDAL_4"
        nodes.add(PartyNodeEntity(m4Id, pName, "TALUKA_MANDAL", d2Id, "Mandal President (Rural Tehsil Mandal)", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_M4_VP", pName, "TALUKA_MANDAL", m4Id, "Mandal Vice President", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_M4_GS", pName, "TALUKA_GS", m4Id, "Mandal General Secretary", posPlaceholder, primaryState, dist2, date))
        nodes.add(PartyNodeEntity("${pName}_M4_SEC", pName, "TALUKA_GS", m4Id, "Mandal Secretary", posPlaceholder, primaryState, dist2, date))

        val m5Id = "${pName}_MANDAL_5"
        nodes.add(PartyNodeEntity(m5Id, pName, "TALUKA_MANDAL", d3Id, "Mandal President (Suburban Mandal)", posPlaceholder, primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_M5_GS", pName, "TALUKA_GS", m5Id, "Mandal General Secretary", posPlaceholder, primaryState, dist3, date))
        nodes.add(PartyNodeEntity("${pName}_M5_SEC", pName, "TALUKA_GS", m5Id, "Mandal Secretary", posPlaceholder, primaryState, dist3, date))

        // ═════════════════════════════════════════════════════════════════
        // 5. WARD / SHAKHA LEVEL (8 Wards, ~25 boxes)
        // ═════════════════════════════════════════════════════════════════
        val w1Id = "${pName}_WARD_101"
        nodes.add(PartyNodeEntity(w1Id, pName, "WARD", m1Id, "Ward Committee President (Ward 101)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W1_VP", pName, "WARD", w1Id, "Ward Vice President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W1_GS", pName, "WARD_GS", w1Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W1_SEC", pName, "WARD_GS", w1Id, "Ward Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W1_YOUTH", pName, "WARD_GS", w1Id, "Ward Youth Coordinator", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W1_MAHILA", pName, "WARD_GS", w1Id, "Ward Mahila Coordinator", posPlaceholder, primaryState, dist1, date))

        val w2Id = "${pName}_WARD_102"
        nodes.add(PartyNodeEntity(w2Id, pName, "WARD", m1Id, "Ward Committee President (Ward 102)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W2_VP", pName, "WARD", w2Id, "Ward Vice President", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W2_GS", pName, "WARD_GS", w2Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W2_SEC", pName, "WARD_GS", w2Id, "Ward Secretary", posPlaceholder, primaryState, dist1, date))

        val w3Id = "${pName}_WARD_103"
        nodes.add(PartyNodeEntity(w3Id, pName, "WARD", m1Id, "Ward Committee President (Ward 103)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W3_GS", pName, "WARD_GS", w3Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W3_SEC", pName, "WARD_GS", w3Id, "Ward Secretary", posPlaceholder, primaryState, dist1, date))

        val w4Id = "${pName}_WARD_104"
        nodes.add(PartyNodeEntity(w4Id, pName, "WARD", m1Id, "Ward Committee President (Ward 104)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W4_GS", pName, "WARD_GS", w4Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W4_SEC", pName, "WARD_GS", w4Id, "Ward Secretary", posPlaceholder, primaryState, dist1, date))

        val w5Id = "${pName}_WARD_105"
        nodes.add(PartyNodeEntity(w5Id, pName, "WARD", m1Id, "Ward Committee President (Ward 105)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W5_GS", pName, "WARD_GS", w5Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))

        val w201Id = "${pName}_WARD_201"
        nodes.add(PartyNodeEntity(w201Id, pName, "WARD", m2Id, "Ward Committee President (Ward 201)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W201_GS", pName, "WARD_GS", w201Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W201_SEC", pName, "WARD_GS", w201Id, "Ward Secretary", posPlaceholder, primaryState, dist1, date))

        val w202Id = "${pName}_WARD_202"
        nodes.add(PartyNodeEntity(w202Id, pName, "WARD", m2Id, "Ward Committee President (Ward 202)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W202_GS", pName, "WARD_GS", w202Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))

        val w203Id = "${pName}_WARD_203"
        nodes.add(PartyNodeEntity(w203Id, pName, "WARD", m2Id, "Ward Committee President (Ward 203)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_W203_GS", pName, "WARD_GS", w203Id, "Ward General Secretary", posPlaceholder, primaryState, dist1, date))

        // ═════════════════════════════════════════════════════════════════
        // 6. POLLING BOOTH LEVEL (~44 boxes)
        // ═════════════════════════════════════════════════════════════════
        // ── Booth 101 (8 boxes) ──────────────────────────────────────────
        val b101Id = "${pName}_BOOTH_101"
        nodes.add(PartyNodeEntity(b101Id, pName, "BOOTH", w1Id, "Booth Committee President (Booth 101)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_SEC", pName, "BOOTH", b101Id, "Booth Secretary (Booth 101)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_P1", pName, "BOOTH", b101Id, "Panna Pramukh (Page 1 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_P2", pName, "BOOTH", b101Id, "Panna Pramukh (Page 2 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_P3", pName, "BOOTH", b101Id, "Panna Pramukh (Page 3 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_YOUTH", pName, "BOOTH", b101Id, "Booth Youth Coordinator", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_MAHILA", pName, "BOOTH", b101Id, "Booth Mahila Coordinator", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B101_BLA", pName, "BOOTH", b101Id, "Booth Level Polling Agent (BLA-2)", posPlaceholder, primaryState, dist1, date))

        // ── Booth 102 (6 boxes) ──────────────────────────────────────────
        val b102Id = "${pName}_BOOTH_102"
        nodes.add(PartyNodeEntity(b102Id, pName, "BOOTH", w1Id, "Booth Committee President (Booth 102)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B102_SEC", pName, "BOOTH", b102Id, "Booth Secretary (Booth 102)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B102_P1", pName, "BOOTH", b102Id, "Panna Pramukh (Page 1 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B102_P2", pName, "BOOTH", b102Id, "Panna Pramukh (Page 2 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B102_YOUTH", pName, "BOOTH", b102Id, "Booth Youth Coordinator", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B102_MAHILA", pName, "BOOTH", b102Id, "Booth Mahila Coordinator", posPlaceholder, primaryState, dist1, date))

        // ── Booth 103 (4 boxes) ──────────────────────────────────────────
        val b103Id = "${pName}_BOOTH_103"
        nodes.add(PartyNodeEntity(b103Id, pName, "BOOTH", w1Id, "Booth Committee President (Booth 103)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B103_SEC", pName, "BOOTH", b103Id, "Booth Secretary (Booth 103)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B103_P1", pName, "BOOTH", b103Id, "Panna Pramukh (Page 1 Incharge)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B103_P2", pName, "BOOTH", b103Id, "Panna Pramukh (Page 2 Incharge)", posPlaceholder, primaryState, dist1, date))

        // ── Booth 104 (3 boxes) ──────────────────────────────────────────
        val b104Id = "${pName}_BOOTH_104"
        nodes.add(PartyNodeEntity(b104Id, pName, "BOOTH", w1Id, "Booth Committee President (Booth 104)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B104_SEC", pName, "BOOTH", b104Id, "Booth Secretary (Booth 104)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B104_P1", pName, "BOOTH", b104Id, "Panna Pramukh (Page 1 Incharge)", posPlaceholder, primaryState, dist1, date))

        // ── Booth 105 (2 boxes) ──────────────────────────────────────────
        val b105Id = "${pName}_BOOTH_105"
        nodes.add(PartyNodeEntity(b105Id, pName, "BOOTH", w1Id, "Booth Committee President (Booth 105)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B105_SEC", pName, "BOOTH", b105Id, "Booth Secretary (Booth 105)", posPlaceholder, primaryState, dist1, date))

        // ── Booth 106 (under Ward 102, 5 boxes) ──────────────────────────
        val b106Id = "${pName}_BOOTH_106"
        nodes.add(PartyNodeEntity(b106Id, pName, "BOOTH", w2Id, "Booth Committee President (Booth 106)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B106_SEC", pName, "BOOTH", b106Id, "Booth Secretary (Booth 106)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B106_P1", pName, "BOOTH", b106Id, "Panna Pramukh (Page 1)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B106_P2", pName, "BOOTH", b106Id, "Panna Pramukh (Page 2)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B106_YOUTH", pName, "BOOTH", b106Id, "Booth Youth Coordinator", posPlaceholder, primaryState, dist1, date))

        // ── Booth 107 (under Ward 102, 4 boxes) ──────────────────────────
        val b107Id = "${pName}_BOOTH_107"
        nodes.add(PartyNodeEntity(b107Id, pName, "BOOTH", w2Id, "Booth Committee President (Booth 107)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B107_SEC", pName, "BOOTH", b107Id, "Booth Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B107_P1", pName, "BOOTH", b107Id, "Panna Pramukh", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B107_MAHILA", pName, "BOOTH", b107Id, "Booth Mahila Coordinator", posPlaceholder, primaryState, dist1, date))

        // ── Booth 108 (under Ward 102, 3 boxes) ──────────────────────────
        val b108Id = "${pName}_BOOTH_108"
        nodes.add(PartyNodeEntity(b108Id, pName, "BOOTH", w2Id, "Booth Committee President (Booth 108)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B108_SEC", pName, "BOOTH", b108Id, "Booth Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B108_P1", pName, "BOOTH", b108Id, "Panna Pramukh", posPlaceholder, primaryState, dist1, date))

        // ── Booth 201 (under Ward 201, 5 boxes) ──────────────────────────
        val b201Id = "${pName}_BOOTH_201"
        nodes.add(PartyNodeEntity(b201Id, pName, "BOOTH", w201Id, "Booth Committee President (Booth 201)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B201_SEC", pName, "BOOTH", b201Id, "Booth Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B201_P1", pName, "BOOTH", b201Id, "Panna Pramukh (Page 1)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B201_P2", pName, "BOOTH", b201Id, "Panna Pramukh (Page 2)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B201_YOUTH", pName, "BOOTH", b201Id, "Booth Youth Coordinator", posPlaceholder, primaryState, dist1, date))

        // ── Booth 202 (under Ward 201, 4 boxes) ──────────────────────────
        val b202Id = "${pName}_BOOTH_202"
        nodes.add(PartyNodeEntity(b202Id, pName, "BOOTH", w201Id, "Booth Committee President (Booth 202)", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B202_SEC", pName, "BOOTH", b202Id, "Booth Secretary", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B202_P1", pName, "BOOTH", b202Id, "Panna Pramukh", posPlaceholder, primaryState, dist1, date))
        nodes.add(PartyNodeEntity("${pName}_B202_MAHILA", pName, "BOOTH", b202Id, "Booth Mahila Coordinator", posPlaceholder, primaryState, dist1, date))

        return nodes
    }
}
