package com.ourindia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ourindia.app.data.lawyer.LegalKnowledge
import com.ourindia.app.data.lawyer.tidyLegalText
import com.ourindia.app.ui.lawyer.LawyerScreen
import com.ourindia.app.ui.lawyer.LawyerViewModel
import com.ourindia.app.ui.theme.OurIndiaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xxhdpi")
class LawyerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun modelMarkdownBecomesPlainText() {
        val raw = "## Your rights\n* You can ask for **bail**\n- Carry ID\nNo *stray* stars"
        val out = tidyLegalText(raw)
        assertEquals("**Your rights**\n• You can ask for **bail**\n• Carry ID\nNo stray stars", out)
    }

    @Test
    fun quickListHasNoDuplicatesAndCorrectedCitations() {
        val ipc = LegalKnowledge.ipcBnsList.map { it.ipcSection }
        assertEquals(ipc.size, ipc.distinct().size)
        // CrPC 41 became BNSS 35; the first draft cited "41 BNSS".
        assertFalse(LegalKnowledge.citizenRights.any { "41 BNSS" in it.legalProvision })
        assertTrue(LegalKnowledge.ipcBnsList.single { it.ipcSection == "420" }.bnsSection == "318(4)")
    }

    /** Builds without a Groq key: every tab must still open, and the offline tabs must work. */
    @Test
    fun everyTabOpensWithoutAKey() {
        val vm = LawyerViewModel()
        compose.setContent { OurIndiaTheme { LawyerScreen(onClose = {}, viewModel = vm) } }
        compose.onAllNodesWithText("AI answers aren't set up", substring = true).onFirst().assertIsDisplayed()

        compose.onNodeWithText("Sections").performClick()
        compose.onAllNodesWithText("e.g. 420 or theft", substring = true).onFirst().performTextInput("420")
        compose.waitForIdle()
        compose.onAllNodesWithText("BNS 318(4)").onFirst().assertIsDisplayed()

        compose.onNodeWithText("Draft").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rights").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
