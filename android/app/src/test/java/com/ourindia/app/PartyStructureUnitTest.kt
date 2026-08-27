package com.ourindia.app

import com.ourindia.app.ui.partystructure.HierarchyLevel
import com.ourindia.app.ui.partystructure.PartyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyStructureUnitTest {

    @Test
    fun partyCatalog_containsExpectedNationalParties() {
        val bjp = PartyCatalog.getParty("BJP")
        assertEquals("Bharatiya Janata Party", bjp.name)
        assertEquals("🪷", bjp.symbolEmoji)
        assertEquals(240, bjp.nationalSeats)

        val inc = PartyCatalog.getParty("INC")
        assertEquals("Indian National Congress", inc.name)
        assertEquals("✋", inc.symbolEmoji)
        assertEquals(99, inc.nationalSeats)

        val aap = PartyCatalog.getParty("AAP")
        assertEquals("Aam Aadmi Party", aap.name)

        val ncp = PartyCatalog.getParty("NCP")
        assertNotNull(ncp)
        assertEquals("NCP", ncp.shortName)
    }

    @Test
    fun partyCatalog_fallbackReturnsValidMetadata() {
        val unknown = PartyCatalog.getParty("XYZ_PARTY")
        assertEquals("XYZ_PARTY", unknown.shortName)
        assertEquals("🏛️", unknown.symbolEmoji)
    }

    @Test
    fun hierarchyLevel_parsesCorrectly() {
        assertEquals(HierarchyLevel.NATIONAL, HierarchyLevel.fromString("NATIONAL"))
        assertEquals(HierarchyLevel.STATE, HierarchyLevel.fromString("state"))
        assertEquals(HierarchyLevel.DISTRICT, HierarchyLevel.fromString("District"))
        assertEquals(HierarchyLevel.WARD, HierarchyLevel.fromString("WARD"))
        assertEquals(HierarchyLevel.NATIONAL, HierarchyLevel.fromString("UNKNOWN_LEVEL"))
    }

    @Test
    fun hierarchyLevel_tierOrderIsCorrect() {
        assertTrue(HierarchyLevel.NATIONAL.tier < HierarchyLevel.STATE.tier)
        assertTrue(HierarchyLevel.STATE.tier < HierarchyLevel.DISTRICT.tier)
        assertTrue(HierarchyLevel.DISTRICT.tier < HierarchyLevel.WARD.tier)
    }
}
