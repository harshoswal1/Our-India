package com.ourindia.app

import com.ourindia.app.data.leaders.LeaderUpdateRepository
import com.ourindia.app.data.leaders.WikiLeaders
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.ui.partystructure.OrbitMetrics
import com.ourindia.app.ui.partystructure.PartySeedData
import com.ourindia.app.ui.partystructure.orbitSlots
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderUpdatesTest {

    @Test
    fun readsInfoboxLikeTheLivePages() {
        // Shapes copied from en.wikipedia.org infoboxes on 2026-10-16.
        val bjp = "| name = BJP\n| president = [[Nitin Nabin]]<ref>{{Cite news|title=Nitin Nabin elected}}</ref>\n| general_secretary = {{clist"
        assertEquals("Nitin Nabin", WikiLeaders.infoboxValue(bjp, listOf("president")))
        assertEquals("Sunny Joseph", WikiLeaders.infoboxValue("| president = [[Sunny Joseph (politician)|Sunny Joseph]]", listOf("president")))
        assertEquals("Archana Gupta", WikiLeaders.infoboxValue("| president = Dr Archana Gupta", listOf("president")))
        assertEquals(WikiLeaders.VACANT, WikiLeaders.infoboxValue("| president = TBA", listOf("president")))
        // A blank row is "unknown", not a resignation.
        assertNull(WikiLeaders.infoboxValue("| president = \n| headquarters = Delhi", listOf("president")))
        // AAP has no "convener" row; the next field is used.
        assertEquals("Arvind Kejriwal", WikiLeaders.infoboxValue("| leader = [[Arvind Kejriwal]]", listOf("convener", "leader")))
    }

    @Test
    fun matchesTheSamePersonAcrossSpellings() {
        assertTrue(WikiLeaders.sameName("Harshwardhan Sapkal", "Harshwardhan Vasantrao Sapkal"))
        assertTrue(WikiLeaders.sameName("Dilli Ram Thapa", "D.R.Thapa"))
        assertTrue(WikiLeaders.sameName("M. A. Baby", "M.A. Baby"))
        assertFalse(WikiLeaders.sameName("Nitin Nabin", "J. P. Nadda"))
    }

    @Test
    fun onlyAChangeOnWikipediaOverridesTheSeed() {
        // INC Sikkim, 2026-10-16: seed says Gopal Chettri, an untouched-since-May page says
        // Nirnay Chhetri. A mere difference must not replace the sourced seed.
        val first = WikiLeaders.next(null, "INC_STATE_SK", "Sikkim Pradesh Congress Committee", "Nirnay Chhetri", "2026-10-16")
        assertNull(WikiLeaders.override("Gopal Chettri", first))

        val same = WikiLeaders.next(first, "INC_STATE_SK", first.pageTitle, "Nirnay Chhetri", "2026-11-16")
        assertNull(same.changedAt)

        val changed = WikiLeaders.next(same, "INC_STATE_SK", first.pageTitle, "New President", "2026-12-16")
        assertEquals("New President", WikiLeaders.override("Gopal Chettri", changed))
        // Once a release ships the same name in the seed, the seed wins again.
        assertNull(WikiLeaders.override("New President", changed))

        // Vandalism reverted: back to the baseline means nothing changed.
        val reverted = WikiLeaders.next(changed, "INC_STATE_SK", first.pageTitle, "Nirnay Chhetri", "2027-01-16")
        assertNull(WikiLeaders.override("Gopal Chettri", reverted))
    }

    @Test
    fun redirectsToTheNationalPageAreNotTakenForAStateUnit() {
        val nodes = listOf(
            PartyNodeEntity("BJP_NAT_PRES", "BJP", "NATIONAL", null, "National President", "Nitin Nabin", null, null, "x"),
            PartyNodeEntity("BJP_STATE_AN", "BJP", "STATE", "BJP_NAT_PRES", "State President (Andaman & Nicobar)", "Anil Kumar Tiwari", "Andaman & Nicobar", null, "x"),
            PartyNodeEntity("INC_STATE_MP", "INC", "STATE", "INC_NAT_PRES", "PCC President (Madhya Pradesh)", "Jitu Patwari", "Madhya Pradesh", null, "x")
        )
        val targets = WikiLeaders.targets(nodes).associateBy { it.nodeId }
        assertEquals("Bharatiya Janata Party, Andaman and Nicobar Islands", targets.getValue("BJP_STATE_AN").title)
        assertEquals("Madhya Pradesh Congress Committee", targets.getValue("INC_STATE_MP").title)

        // Live API answer: the Andaman title redirects to the national party page.
        val api = """{"query":{"redirects":[{"from":"Bharatiya Janata Party, Andaman and Nicobar Islands","to":"Bharatiya Janata Party"}],
            "pages":[{"title":"Bharatiya Janata Party","revisions":[{"slots":{"main":{"content":"| president = [[Nitin Nabin]]"}}}]}]}}"""
        val t = targets.getValue("BJP_STATE_AN")
        val (resolved, _) = LeaderUpdateRepository.parseQuery(Json.parseToJsonElement(api).jsonObject, listOf(t.title)).getValue(t.title)
        assertFalse("guard must reject $resolved", resolved.contains(t.mustContain!!, ignoreCase = true))

        // Every seeded party maps without crashing, and national heads come first-class.
        val all = WikiLeaders.targets(PartySeedData.parties.flatMap(PartySeedData::nodesFor))
        assertTrue(all.any { it.nodeId == "BJP_NAT_PRES" } && all.size > 50)
        assertEquals(all.size, all.map { it.nodeId }.distinct().size)
    }

    @Test
    fun orbitRingsHoldEveryUnitWithoutOverfilling() {
        listOf(0, 1, 7, 36, 78).forEach { n ->
            val slots = orbitSlots(n)
            assertEquals(n, slots.size)
            slots.groupBy { it.ring }.forEach { (ring, s) -> assertTrue("ring $ring over capacity for n=$n", s.size <= OrbitMetrics.capacity(ring)) }
        }
        // BJP's 36 state units fit in three rings, not one crammed circle.
        assertEquals(3, orbitSlots(36).maxOf { it.ring } + 1)

        // Disc codes never repeat within one ring: initials once gave Maharashtra and Manipur both "MA".
        assertEquals("MH", com.ourindia.app.ui.partystructure.unitCode("Maharashtra"))
        assertEquals("MN", com.ourindia.app.ui.partystructure.unitCode("Manipur"))
        PartySeedData.parties.forEach { p ->
            val tree = com.ourindia.app.ui.partystructure.buildOrgTree(PartySeedData.nodesFor(p).map {
                com.ourindia.app.ui.partystructure.PartyTreeNode(it.id, it.partyName, com.ourindia.app.ui.partystructure.HierarchyLevel.fromString(it.level), it.parentId, it.roleTitle, it.holderName, it.state, it.district)
            })
            tree.units.values.forEach { u ->
                val codes = com.ourindia.app.ui.partystructure.unitCodes(u.childIds.map { tree.units.getValue(it).title })
                assertEquals("$p/${u.title}: $codes", codes.size, codes.toSet().size)
            }
        }
    }
}
