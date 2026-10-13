package com.ourindia.app

import com.ourindia.app.ui.partystructure.PartyCatalog
import com.ourindia.app.ui.partystructure.PartySeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySeedDataTest {

    @Test
    fun everyCatalogPartyHasAStructure() {
        PartyCatalog.parties.forEach { party ->
            assertTrue("${party.shortName} has no seed", PartySeedData.nodesFor(party.shortName).isNotEmpty())
        }
    }

    @Test
    fun everyNamedHolderIsCitedAndEveryTreeIsWellFormed() {
        val allIds = mutableSetOf<String>()
        PartySeedData.parties.forEach { party ->
            val nodes = PartySeedData.nodesFor(party)
            val ids = nodes.map { it.id }.toSet()
            assertEquals("$party: one root", 1, nodes.count { it.parentId == null })
            nodes.forEach { n ->
                assertTrue("duplicate id ${n.id}", allIds.add(n.id))
                assertEquals(party, n.partyName)
                assertEquals(PartySeedData.SEED_DATE, n.versionDate)
                n.parentId?.let { assertTrue("${n.id}: dangling parent $it", it in ids) }
                if (!n.holderName.contains("Not yet fetched")) {
                    val source = PartySeedData.sourceFor(n.id)
                    assertNotNull("${n.id} (${n.holderName}) has no source", source)
                    assertTrue("${n.id}: source must be an https link", source!!.referenceUrl!!.startsWith("https://"))
                }
            }
        }
    }
}
