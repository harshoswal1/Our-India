package com.ourindia.app

import androidx.room.Room
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.leaders.LeaderUpdateRepository
import com.ourindia.app.data.local.OurIndiaDatabase
import com.ourindia.app.ui.partystructure.PartyStructureScreen
import com.ourindia.app.ui.partystructure.PartyStructureViewModel
import com.ourindia.app.ui.theme.OurIndiaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real Leaders flow: pick a party, land in the orbit, step into a state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LeadersScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun openingAPartyShowsTheOrbit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OurIndiaDatabase::class.java).allowMainThreadQueries().build()
        // As on a phone that already got a fix on Home: the orbit opens on the local unit.
        val here = LocationStore().apply {
            location.value = com.ourindia.app.data.geo.ResolvedLocation(state = "Maharashtra", district = "Pune", latitude = 18.52, longitude = 73.85)
        }
        val vm = PartyStructureViewModel(db.partyNodeDao(), db.leaderUpdateDao(), LeaderUpdateRepository(db.leaderUpdateDao()), here)
        compose.setContent {
            OurIndiaTheme { PartyStructureScreen(resolver = LocalGeographicResolver(context), viewModel = vm) }
        }
        compose.runOnIdle { vm.onSelectPartyFromGrid("BJP") }
        // Room answers on its own threads; ViewModel collectors run on the paused main looper.
        compose.waitUntil(20_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.uiState.value.let { it.orbitUnitId != null && !it.isLoading }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Maharashtra").onFirst().performClick()
        compose.waitForIdle()
        db.close()
    }
}
