package com.ourindia.app

import com.ourindia.app.ui.partystructure.ChartMetrics
import com.ourindia.app.ui.partystructure.HierarchyLevel
import com.ourindia.app.ui.partystructure.PartyTreeNode
import com.ourindia.app.ui.partystructure.buildOrgTree
import com.ourindia.app.ui.partystructure.layoutOrgChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchyLayoutTest {

    private fun node(id: String, level: HierarchyLevel, parent: String?, state: String? = null, district: String? = null) =
        PartyTreeNode(id, "BJP", level, parent, id, "Holder $id", state, district)

    private val nodes = listOf(
        node("pres", HierarchyLevel.NATIONAL, null),
        node("vp", HierarchyLevel.NATIONAL_VP, "pres"),
        node("gs", HierarchyLevel.NATIONAL_GS, "pres"),
        node("mh", HierarchyLevel.STATE, "pres", "Maharashtra"),
        node("mh_vp", HierarchyLevel.STATE_VP, "mh", "Maharashtra"),
        node("pune", HierarchyLevel.DISTRICT, "mh", "Maharashtra", "Pune"),
        node("mum", HierarchyLevel.DISTRICT, "mh", "Maharashtra", "Mumbai"),
        node("dl", HierarchyLevel.STATE, "pres", "Delhi"),
        // Cycle and orphan: must neither hang nor duplicate
        node("loop_a", HierarchyLevel.WARD, "loop_b"),
        node("loop_b", HierarchyLevel.WARD, "loop_a"),
        node("orphan", HierarchyLevel.DISTRICT, "missing", "Goa", "North Goa")
    )

    @Test
    fun sameTierPositionsShareOneCard() {
        val tree = buildOrgTree(nodes)
        val national = tree.units.getValue("pres")
        assertEquals(listOf("pres", "vp", "gs"), national.members.map { it.id })
        assertEquals(listOf("dl", "mh"), national.childIds.sorted())
        assertEquals(listOf("mh", "mh_vp"), tree.units.getValue("mh").members.map { it.id })
        assertEquals("mh", tree.unitOf("mh_vp")?.id)
        assertEquals(listOf("pres", "mh"), tree.ancestors("pune"))
        assertEquals(setOf("pres", "orphan"), tree.rootIds.toSet())
    }

    @Test
    fun manyChildrenWrapIntoRowsInsteadOfOneWideRow() {
        val many = listOf(node("root", HierarchyLevel.NATIONAL, null)) +
            (1..7).map { node("s$it", HierarchyLevel.STATE, "root", "State $it") }
        val chart = layoutOrgChart(buildOrgTree(many), emptySet())
        val kids = chart.units.filter { it.unit.id != "root" }
        val rows = kids.groupBy { it.y }
        assertEquals(listOf(3, 3, 1), rows.toSortedMap().values.map { it.size })
        assertTrue(chart.byId.getValue("root").trunkX != null)
        assertTrue(chart.width <= ChartMetrics.MAX_ROW_WIDTH + ChartMetrics.H_GAP + 2 * ChartMetrics.MARGIN + 0.5f)
        for (i in kids.indices) for (j in i + 1 until kids.size) {
            val a = kids[i]; val b = kids[j]
            val overlapX = a.x < b.x + ChartMetrics.UNIT_WIDTH && b.x < a.x + ChartMetrics.UNIT_WIDTH
            val overlapY = a.y < b.bottom && b.y < a.bottom
            assertFalse("${a.unit.id} overlaps ${b.unit.id}", overlapX && overlapY)
        }
    }

    @Test
    fun collapsingHidesDescendantsAndLayoutNeverOverlaps() {
        val tree = buildOrgTree(nodes)
        assertEquals(setOf("mh"), tree.defaultCollapsed())

        val folded = layoutOrgChart(tree, tree.defaultCollapsed())
        assertFalse(folded.byId.containsKey("pune"))
        assertTrue(folded.byId.getValue("mh").collapsed)

        val open = layoutOrgChart(tree, emptySet())
        assertTrue(open.byId.containsKey("pune") && open.byId.containsKey("mum"))
        val cards = open.units
        for (i in cards.indices) for (j in i + 1 until cards.size) {
            val a = cards[i]; val b = cards[j]
            val overlapX = a.x < b.x + ChartMetrics.UNIT_WIDTH && b.x < a.x + ChartMetrics.UNIT_WIDTH
            val overlapY = a.y < b.bottom && b.y < a.bottom
            assertFalse("${a.unit.id} overlaps ${b.unit.id}", overlapX && overlapY)
        }
        // Children sit below their parent, parent centred over them.
        val mh = open.byId.getValue("mh")
        val kids = listOf(open.byId.getValue("pune"), open.byId.getValue("mum")).sortedBy { it.x }
        assertTrue(kids.all { it.y > mh.bottom })
        assertEquals((kids.first().centerX + kids.last().centerX) / 2f, mh.centerX, 0.01f)
        assertTrue(cards.all { it.x >= 0f && it.x + ChartMetrics.UNIT_WIDTH <= open.width && it.bottom <= open.height })
    }
}
