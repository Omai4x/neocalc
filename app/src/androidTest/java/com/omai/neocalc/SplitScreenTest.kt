package com.omai.neocalc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import com.omai.neocalc.split.SplitScreen
import com.omai.neocalc.ui.theme.FirstTestAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * The split screen, driven the way a person would drive it. These are the
 * assertions unit tests cannot make: that the arithmetic actually reaches the
 * screen, and that the controls are wired to the state they claim to change.
 */
class SplitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun launch() {
        compose.setContent {
            FirstTestAppTheme(darkTheme = false) { SplitScreen() }
        }
    }

    @Test
    fun anEvenSplitReachesTheScreen() {
        launch()
        compose.onNodeWithText("Bill total").performTextInput("90")
        // Two people and no tip by default would be 45 each; the default tip is
        // 10%, so 99 across two.
        compose.onNodeWithText("0%").performClick()
        compose.onNodeWithText("45.00").assertIsDisplayed()
    }

    @Test
    fun addingAPersonChangesEachShare() {
        launch()
        compose.onNodeWithText("Bill total").performTextInput("90")
        compose.onNodeWithText("0%").performClick()
        compose.onNodeWithContentDescription("One more person").performClick()
        compose.onNodeWithText("30.00").assertIsDisplayed()
    }

    @Test
    fun aTipIsAddedToTheTotal() {
        launch()
        compose.onNodeWithText("Bill total").performTextInput("100")
        compose.onNodeWithText("20%").performClick()
        // 100 + 20% = 120, split two ways.
        compose.onNodeWithText("60.00").assertIsDisplayed()
    }

    @Test
    fun itemisedModeShowsOneLinePerPerson() {
        launch()
        compose.onNodeWithText("By item").performClick()
        compose.onNodeWithText("Person 1").assertIsDisplayed()
        compose.onNodeWithText("Add a person").assertIsDisplayed()
    }

    @Test
    fun theAmountFieldAcceptsAnExpression() {
        launch()
        compose.onNodeWithText("Bill total").performTextInput("40+50")
        compose.onNodeWithText("0%").performClick()
        compose.onNodeWithText("45.00").assertIsDisplayed()
    }

    @Test
    fun roundingUpGivesEveryoneTheSameFigure() {
        launch()
        compose.onNodeWithText("Bill total").performTextInput("101")
        compose.onNodeWithText("0%").performClick()
        compose.onNodeWithText("Round up").performClick()
        // 101 across two rounds to 51 each rather than 50.50 / 50.50.
        compose.onAllNodesWithText("51.00")[0].assertIsDisplayed()
    }
}
