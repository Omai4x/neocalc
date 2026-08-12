package com.omai.neocalc.calculator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omai.neocalc.ui.theme.FirstTestAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Covers the wiring between the keypad table and the engine: the unit tests press
 * [Key] values directly, so a mislabelled key would pass every one of them.
 */
class CalculatorScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent() {
        rule.setContent {
            FirstTestAppTheme {
                CalculatorScreen()
            }
        }
    }

    private fun press(vararg labels: String) {
        labels.forEach { rule.onNodeWithContentDescription(it).performClick() }
    }

    private fun assertResult(text: String) {
        rule.onNodeWithContentDescription("Result $text").assertIsDisplayed()
    }

    @Test
    fun everyDigitKeyEntersItsOwnDigit() {
        setContent()
        press("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        assertResult("1234567890")
    }

    @Test
    fun operatorKeysAreWiredToTheirOperations() {
        setContent()
        press("7", "Multiply", "8", "Equals")
        assertResult("56")

        press("Clear")
        press("9", "Minus", "4", "Equals")
        assertResult("5")

        press("Clear")
        press("6", "Plus", "7", "Equals")
        assertResult("13")

        press("Clear")
        press("9", "Divide", "2", "Equals")
        assertResult("4.5")
    }

    @Test
    fun decimalToggleSignAndBackspaceAreWired() {
        setContent()
        press("1", "Decimal point", "2", "5")
        assertResult("1.25")

        press("Backspace")
        assertResult("1.2")

        press("Toggle sign")
        assertResult("-1.2")
    }

    @Test
    fun percentKeyIsWired() {
        setContent()
        press("5", "0", "Percent")
        assertResult("0.5")
    }

    @Test
    fun clearResetsTheDisplay() {
        setContent()
        press("4", "2", "Clear")
        assertResult("0")
    }

    @Test
    fun divideByZeroShowsTheErrorMessage() {
        setContent()
        press("5", "Divide", "0", "Equals")
        rule.onNodeWithContentDescription("Can't divide by zero").assertIsDisplayed()
    }
}
