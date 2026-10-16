package com.ourindia.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.ourindia.app.ui.partystructure.HierarchyLevel
import com.ourindia.app.ui.partystructure.PartyCatalog
import com.ourindia.app.ui.partystructure.PartyOrbitView
import com.ourindia.app.ui.partystructure.PartySeedData
import com.ourindia.app.ui.partystructure.PartyTreeNode
import com.ourindia.app.ui.partystructure.buildOrgTree
import com.ourindia.app.ui.theme.OurIndiaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opens the orbit for every seeded party and drills one level in; a crash fails the test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OrbitRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun tree(party: String) = buildOrgTree(PartySeedData.nodesFor(party).map {
        PartyTreeNode(it.id, it.partyName, HierarchyLevel.fromString(it.level), it.parentId, it.roleTitle, it.holderName, it.state, it.district)
    })

    @Test
    fun orbitRendersForEveryParty() {
        // setContent may run once per test, so the party is swapped through state.
        var party by mutableStateOf(PartySeedData.parties.first())
        compose.setContent {
            val t = remember(party) { tree(party) }
            OurIndiaTheme {
                PartyOrbitView(t, t.rootIds.first(), PartyCatalog.getParty(party).color, null, {}, {}, Modifier.fillMaxSize())
            }
        }
        PartySeedData.parties.forEach { p ->
            party = p
            compose.waitForIdle()
        }
    }

    /** Every unit of every party in the centre, leaves included. */
    @Test
    @Config(qualifiers = "w360dp-h740dp-xxxhdpi")
    fun everyUnitRendersAsTheCentre() {
        val all = PartySeedData.parties.flatMap { p -> tree(p).let { t -> t.units.keys.map { Triple(p, t, it) } } }
        var i by mutableStateOf(0)
        compose.setContent {
            val (p, t, id) = all[i]
            OurIndiaTheme { PartyOrbitView(t, id, PartyCatalog.getParty(p).color, t.units[id]?.head?.id, {}, {}, Modifier.fillMaxSize()) }
        }
        all.indices.forEach { i = it; compose.waitForIdle() }
    }

    @Test
    fun drillingIntoAStateRenders() {
        val t = tree("BJP")
        val state = t.units.values.first { it.depth == 1 && it.childIds.isNotEmpty() }
        compose.setContent {
            OurIndiaTheme {
                PartyOrbitView(t, state.id, PartyCatalog.getParty("BJP").color, null, {}, {}, Modifier.fillMaxSize())
            }
        }
        compose.onAllNodesWithContentDescription(t.units.getValue(state.childIds.first()).title).onFirst().performClick()
        compose.waitForIdle()
    }
}
