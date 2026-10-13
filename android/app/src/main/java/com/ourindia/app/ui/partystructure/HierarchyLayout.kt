package com.ourindia.app.ui.partystructure

/**
 * Org-chart layout for the Structure tab.
 *
 * Positions are grouped into organisational units — National, each state, each district,
 * each mandal/ward/booth — and each unit is one card listing all of its office-bearers.
 * The old canvas laid out one card per position, which made BJP's chart ~16,000dp wide
 * (every VP, GS and secretary a separate leaf) and left a 360dp phone looking at a sliver.
 *
 * All sizes are dp. The canvas renders every card segment at exactly these heights, so
 * the connectors computed from them land on the cards that are drawn.
 */
object ChartMetrics {
    const val UNIT_WIDTH = 264f
    const val HEADER_HEIGHT = 40f
    const val HEAD_ROW_HEIGHT = 64f
    const val MEMBER_ROW_HEIGHT = 46f
    const val FOOTER_HEIGHT = 44f
    const val BOTTOM_PADDING = 8f
    const val H_GAP = 28f
    const val V_GAP = 64f
    const val MARGIN = 48f

    fun unitHeight(memberCount: Int, hasChildren: Boolean): Float =
        HEADER_HEIGHT + HEAD_ROW_HEIGHT +
            (memberCount - 1).coerceAtLeast(0) * MEMBER_ROW_HEIGHT +
            if (hasChildren) FOOTER_HEIGHT else BOTTOM_PADDING
}

/** A unit card: its head position plus every same-tier position beneath it. */
data class OrgUnit(
    val id: String,
    val title: String,
    val level: HierarchyLevel,
    val members: List<PartyTreeNode>,
    val parentId: String?,
    val childIds: List<String>,
    val depth: Int
) {
    val head: PartyTreeNode get() = members.first()
}

/** Units grouped from positions. Independent of collapse state, so it is built once per data change. */
class OrgTree(
    val units: Map<String, OrgUnit>,
    val rootIds: List<String>,
    private val unitOfNode: Map<String, String>
) {
    fun unitOf(nodeId: String): OrgUnit? = unitOfNode[nodeId]?.let(units::get)

    /** Unit ids from the root down to (excluding) [unitId]. */
    fun ancestors(unitId: String): List<String> =
        generateSequence(units[unitId]?.parentId) { units[it]?.parentId }.toList().asReversed()

    /** National and state cards open, everything below folded — readable on first load. */
    fun defaultCollapsed(): Set<String> =
        units.values.filter { it.depth >= 1 && it.childIds.isNotEmpty() }.mapTo(HashSet()) { it.id }

    companion object {
        val Empty = OrgTree(emptyMap(), emptyList(), emptyMap())
    }
}

data class PlacedUnit(
    val unit: OrgUnit,
    val x: Float,
    val y: Float,
    val height: Float,
    val collapsed: Boolean
) {
    val centerX: Float get() = x + ChartMetrics.UNIT_WIDTH / 2f
    val bottom: Float get() = y + height
}

data class HierarchyChart(val units: List<PlacedUnit>, val width: Float, val height: Float) {
    val byId: Map<String, PlacedUnit> by lazy { units.associateBy { it.unit.id } }

    companion object {
        val Empty = HierarchyChart(emptyList(), 0f, 0f)
    }
}

private val memberOrder = compareBy<PartyTreeNode>({ it.level.tier }, { it.roleTitle })

fun unitTitle(head: PartyTreeNode): String = when (head.level.groupLabel) {
    "National" -> "National"
    "State" -> head.state ?: "State"
    "District" -> head.district ?: head.state ?: "District"
    // "Ward 101 President (Bandra West)" -> "Bandra West"
    else -> head.roleTitle.substringAfter('(', "").substringBefore(')').trim()
        .ifEmpty { head.district ?: head.state ?: head.level.groupLabel }
}

fun buildOrgTree(nodes: List<PartyTreeNode>): OrgTree {
    val byId = nodes.associateBy { it.id }
    val kids = nodes.filter { it.parentId != null && it.parentId in byId }.groupBy { it.parentId!! }
    val roots = nodes.filter { it.parentId == null || it.parentId !in byId }.sortedWith(memberOrder)

    val units = LinkedHashMap<String, OrgUnit>()
    val unitOfNode = HashMap<String, String>()

    fun build(head: PartyTreeNode, parentUnitId: String?, depth: Int): String? {
        // Synced data can carry parentId cycles; a node already placed is never placed twice.
        if (head.id in unitOfNode) return null
        val members = mutableListOf<PartyTreeNode>()
        val childHeads = mutableListOf<PartyTreeNode>()
        fun absorb(n: PartyTreeNode) {
            if (n.id in unitOfNode) return
            members += n
            unitOfNode[n.id] = head.id
            kids[n.id].orEmpty().forEach { c ->
                if (c.level.groupLabel == head.level.groupLabel) absorb(c) else childHeads += c
            }
        }
        absorb(head)
        val childIds = childHeads
            .sortedWith(compareBy({ it.level.tier }, { unitTitle(it) }))
            .mapNotNull { build(it, head.id, depth + 1) }
        units[head.id] = OrgUnit(
            id = head.id,
            title = unitTitle(head),
            level = head.level,
            members = listOf(head) + members.drop(1).sortedWith(memberOrder),
            parentId = parentUnitId,
            childIds = childIds,
            depth = depth
        )
        return head.id
    }

    val rootIds = roots.mapNotNull { build(it, null, 0) }
    return OrgTree(units, rootIds, unitOfNode)
}

/** Top-down tidy layout: each card centred over its visible children, siblings never overlap. */
fun layoutOrgChart(tree: OrgTree, collapsed: Set<String>): HierarchyChart {
    if (tree.rootIds.isEmpty()) return HierarchyChart.Empty
    val m = ChartMetrics

    fun unit(id: String) = tree.units.getValue(id)
    fun visibleKids(id: String) = if (id in collapsed) emptyList() else unit(id).childIds

    val widths = HashMap<String, Float>()
    fun measure(id: String): Float = widths[id] ?: run {
        val k = visibleKids(id)
        val w = if (k.isEmpty()) m.UNIT_WIDTH
        else maxOf(m.UNIT_WIDTH, k.sumOf { measure(it).toDouble() }.toFloat() + m.H_GAP * (k.size - 1))
        widths[id] = w
        w
    }

    val placed = ArrayList<PlacedUnit>(tree.units.size)
    val xs = HashMap<String, Float>()
    fun place(id: String, left: Float, top: Float) {
        val u = unit(id)
        val h = m.unitHeight(u.members.size, u.childIds.isNotEmpty())
        val k = visibleKids(id)
        val x = if (k.isEmpty()) {
            left + (measure(id) - m.UNIT_WIDTH) / 2f
        } else {
            val kidsWidth = k.sumOf { measure(it).toDouble() }.toFloat() + m.H_GAP * (k.size - 1)
            var cursor = left + (measure(id) - kidsWidth) / 2f
            k.forEach {
                place(it, cursor, top + h + m.V_GAP)
                cursor += measure(it) + m.H_GAP
            }
            (xs.getValue(k.first()) + xs.getValue(k.last())) / 2f
        }
        xs[id] = x
        placed += PlacedUnit(u, x, top, h, collapsed = id in collapsed && u.childIds.isNotEmpty())
    }

    var left = m.MARGIN
    tree.rootIds.forEach {
        place(it, left, m.MARGIN)
        left += measure(it) + m.H_GAP * 2
    }
    return HierarchyChart(
        units = placed,
        width = left - m.H_GAP * 2 + m.MARGIN,
        height = placed.maxOf { it.bottom } + m.MARGIN
    )
}
