package com.omai.neocalc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omai.neocalc.calculator.CalculatorScreen
import com.omai.neocalc.calculator.CalculatorState
import com.omai.neocalc.calculator.press
import com.omai.neocalc.ui.theme.FirstTestAppTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test

/**
 * Precedence and brackets, exercised through the actual keys rather than through
 * the engine. The engine tests prove the arithmetic; these prove the keys the
 * user can see are connected to it.
 */
class CalculatorPrecedenceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun launch() {
        compose.setContent {
            FirstTestAppTheme(darkTheme = false) {
                var state by remember { mutableStateOf(CalculatorState()) }
                CalculatorScreen(state = state, onKey = { state = state.press(it) })
            }
        }
    }

    private fun tap(vararg labels: String) {
        labels.forEach { label ->
            when (label) {
                "(" -> compose.onNodeWithContentDescription("Open bracket").performClick()
                ")" -> compose.onNodeWithContentDescription("Close bracket").performClick()
                "+" -> compose.onNodeWithContentDescription("Plus").performClick()
                "×" -> compose.onNodeWithContentDescription("Multiply").performClick()
                "=" -> compose.onNodeWithContentDescription("Equals").performClick()
                else -> compose.onNodeWithText(label).performClick()
            }
        }
    }

    @Test
    fun multiplicationBindsTighterOnTheKeypad() {
        launch()
        tap("2", "+", "3", "×", "4", "=")
        compose.onNodeWithText("14").assertIsDisplayed()
    }

    @Test
    fun bracketsOverridePrecedenceOnTheKeypad() {
        launch()
        tap("(", "2", "+", "3", ")", "×", "4", "=")
        compose.onNodeWithText("20").assertIsDisplayed()
    }

    @Test
    fun theBracketDepthIndicatorAppears() {
        launch()
        tap("2", "×", "(")
        compose.onNodeWithContentDescription("1 open brackets").assertIsDisplayed()
    }

    @Test
    fun largeResultsAreGrouped() {
        launch()
        tap("9", "9", "9", "×", "9", "9", "9", "=")
        // 998,001 rather than 998001.
        compose.onNodeWithText("998,001").assertIsDisplayed()
    }
}
