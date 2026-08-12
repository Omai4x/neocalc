package com.omai.neocalc.calculator

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * The saver addresses its fields by position, so a transposed index would only
 * show up as corrupted state after process death. These round trips pin it down.
 */
class CalculatorStateSaverTest {

    private val scope = SaverScope { true }

    private fun roundTrip(state: CalculatorState): CalculatorState {
        val saved = with(CalculatorStateSaver) { scope.save(state) }
        return CalculatorStateSaver.restore(checkNotNull(saved))!!
    }

    @Test
    fun `initial state survives`() {
        assertEquals(CalculatorState(), roundTrip(CalculatorState()))
    }

    @Test
    fun `entry in progress survives`() {
        val state = CalculatorState().press(Key.Digit(1)).press(Key.Decimal).press(Key.Digit(5))
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `pending operation survives`() {
        val state = CalculatorState()
            .press(Key.Digit(1)).press(Key.Digit(2))
            .press(Key.Op(Operator.Multiply))
            .press(Key.Digit(7))
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `repeatable last operation survives`() {
        val state = CalculatorState()
            .press(Key.Digit(2)).press(Key.Op(Operator.Add)).press(Key.Digit(3))
            .press(Key.Equals)
        assertEquals(state, roundTrip(state))
        // The restored state must still be able to repeat "+ 3".
        assertEquals("8", roundTrip(state).press(Key.Equals).display)
    }

    @Test
    fun `error state survives`() {
        val state = CalculatorState()
            .press(Key.Digit(5)).press(Key.Op(Operator.Divide)).press(Key.Digit(0))
            .press(Key.Equals)
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `every field is carried across`() {
        // Set by hand rather than via key presses, so a field the presses never
        // populate can't quietly go missing.
        val state = CalculatorState(
            display = "-4.25",
            // A nested pending stack with an open bracket, which is the shape
            // most likely to be dropped by a lazy saver.
            stack = listOf(
                Pending(BigDecimal("17.5"), Operator.Subtract),
                Pending(BigDecimal("2"), Operator.Multiply),
            ),
            brackets = listOf(1),
            entering = true,
            lastOperation = Operator.Divide to BigDecimal("3.125"),
            error = null,
            memory = BigDecimal("-2.5"),
            angleMode = AngleMode.Radians,
        )
        assertEquals(state, roundTrip(state))
    }
}
