package com.omai.neocalc.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun run(vararg keys: Key): CalculatorState =
        keys.fold(CalculatorState()) { state, key -> state.press(key) }

    private fun digits(number: String): List<Key> = number.map { char ->
        if (char == '.') Key.Decimal else Key.Digit(char.digitToInt())
    }

    /** Types a number, entering a leading minus the way the keypad does. */
    private fun keysFor(number: String): List<Key> =
        if (number.startsWith("-")) digits(number.drop(1)) + Key.ToggleSign else digits(number)

    private fun eval(left: String, operator: Operator, right: String): String =
        (keysFor(left) + Key.Op(operator) + keysFor(right) + Key.Equals)
            .fold(CalculatorState()) { state, key -> state.press(key) }
            .display

    @Test
    fun `starts at zero`() {
        assertEquals("0", CalculatorState().display)
    }

    @Test
    fun `leading zero is replaced`() {
        assertEquals("7", run(Key.Digit(0), Key.Digit(7)).display)
    }

    @Test
    fun `four operations`() {
        assertEquals("5", eval("2", Operator.Add, "3"))
        assertEquals("-1", eval("2", Operator.Subtract, "3"))
        assertEquals("6", eval("2", Operator.Multiply, "3"))
        assertEquals("2.5", eval("5", Operator.Divide, "2"))
    }

    @Test
    fun `decimal arithmetic is exact`() {
        assertEquals("0.3", eval("0.1", Operator.Add, "0.2"))
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        val state = run(
            Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3),
            Key.Op(Operator.Multiply), Key.Digit(4), Key.Equals,
        )
        // 2 + 3 × 4 is 14. It used to answer 20, which disagreed with the
        // expression parser the text fields use for the very same input.
        assertEquals("14", state.display)
    }

    @Test
    fun `equal precedence still folds left to right`() {
        assertEquals(
            "3",
            run(
                Key.Digit(8), Key.Op(Operator.Subtract), Key.Digit(3),
                Key.Op(Operator.Subtract), Key.Digit(2), Key.Equals,
            ).display,
        )
        assertEquals(
            "2",
            run(
                Key.Digit(8), Key.Op(Operator.Divide), Key.Digit(2),
                Key.Op(Operator.Divide), Key.Digit(2), Key.Equals,
            ).display,
        )
    }

    @Test
    fun `power is right associative`() {
        val state = run(
            Key.Digit(2), Key.Op(Operator.Power), Key.Digit(3),
            Key.Op(Operator.Power), Key.Digit(2), Key.Equals,
        )
        assertEquals("512", state.display)
    }

    @Test
    fun `brackets override precedence`() {
        val state = run(
            Key.OpenParen, Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3),
            Key.CloseParen, Key.Op(Operator.Multiply), Key.Digit(4), Key.Equals,
        )
        assertEquals("20", state.display)
    }

    @Test
    fun `brackets nest`() {
        // 2 × (3 + (4 - 1)) = 12
        val state = run(
            Key.Digit(2), Key.Op(Operator.Multiply),
            Key.OpenParen, Key.Digit(3), Key.Op(Operator.Add),
            Key.OpenParen, Key.Digit(4), Key.Op(Operator.Subtract), Key.Digit(1),
            Key.CloseParen, Key.CloseParen, Key.Equals,
        )
        assertEquals("12", state.display)
        assertEquals(0, state.openBrackets)
    }

    @Test
    fun `equals closes any brackets left open`() {
        val state = run(
            Key.Digit(2), Key.Op(Operator.Multiply),
            Key.OpenParen, Key.Digit(3), Key.Op(Operator.Add), Key.Digit(4),
            Key.Equals,
        )
        assertEquals("14", state.display)
        assertEquals(0, state.openBrackets)
    }

    @Test
    fun `a stray closing bracket is ignored`() {
        val state = run(Key.Digit(7), Key.CloseParen)
        assertEquals("7", state.display)
        assertEquals(0, state.openBrackets)
    }

    @Test
    fun `the open bracket count is reported for the indicator`() {
        val state = run(
            Key.Digit(2), Key.Op(Operator.Multiply), Key.OpenParen,
            Key.Digit(3), Key.Op(Operator.Add), Key.OpenParen,
        )
        assertEquals(2, state.openBrackets)
    }

    @Test
    fun `the keypad and the expression parser now agree`() {
        // The point of the whole change: one app, one answer.
        val keypad = run(
            Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3),
            Key.Op(Operator.Multiply), Key.Digit(4), Key.Equals,
        ).display
        assertEquals(ExpressionParser.evaluate("2+3*4")!!.toInt().toString(), keypad)
    }

    @Test
    fun `pressing two operators in a row swaps the operator`() {
        val state = run(
            Key.Digit(8), Key.Op(Operator.Add), Key.Op(Operator.Multiply),
            Key.Digit(2), Key.Equals,
        )
        assertEquals("16", state.display)
    }

    @Test
    fun `repeated equals repeats the last operation`() {
        var state = run(Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3), Key.Equals)
        assertEquals("5", state.display)
        state = state.press(Key.Equals)
        assertEquals("8", state.display)
        state = state.press(Key.Equals)
        assertEquals("11", state.display)
    }

    @Test
    fun `digit after equals starts a new entry`() {
        val state = run(Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3), Key.Equals, Key.Digit(9))
        assertEquals("9", state.display)
    }

    @Test
    fun `only one decimal point per entry`() {
        assertEquals("1.5", run(Key.Digit(1), Key.Decimal, Key.Digit(5), Key.Decimal).display)
    }

    @Test
    fun `decimal with no leading digit`() {
        assertEquals("0.5", run(Key.Decimal, Key.Digit(5)).display)
    }

    @Test
    fun `backspace removes the last character`() {
        assertEquals("12", run(Key.Digit(1), Key.Digit(2), Key.Digit(3), Key.Backspace).display)
        assertEquals("0", run(Key.Digit(3), Key.Backspace).display)
    }

    @Test
    fun `toggle sign flips both ways`() {
        assertEquals("-4", run(Key.Digit(4), Key.ToggleSign).display)
        assertEquals("4", run(Key.Digit(4), Key.ToggleSign, Key.ToggleSign).display)
        assertEquals("0", run(Key.ToggleSign).display)
    }

    @Test
    fun `percent of a pending addition uses the accumulator`() {
        val state = run(
            Key.Digit(1), Key.Digit(0), Key.Digit(0),
            Key.Op(Operator.Add), Key.Digit(1), Key.Digit(0), Key.Percent, Key.Equals,
        )
        assertEquals("110", state.display)
    }

    @Test
    fun `bare percent divides by one hundred`() {
        assertEquals("0.5", run(Key.Digit(5), Key.Digit(0), Key.Percent).display)
    }

    @Test
    fun `percent starts a fresh entry so equals does not replay the last operation`() {
        val state = run(Key.Digit(2), Key.Op(Operator.Add), Key.Digit(3), Key.Equals)
        assertEquals("5", state.display)
        val percent = state.press(Key.Percent)
        assertEquals("0.05", percent.display)
        // Without clearing lastOperation this would replay "+ 3" and give 3.05.
        assertEquals("0.05", percent.press(Key.Equals).display)
    }

    @Test
    fun `toggle sign after an operator survives the next digit`() {
        val state = run(Key.Digit(5), Key.Op(Operator.Add), Key.ToggleSign)
        assertEquals("-5", state.display)
        // '±' marks the display as an entry, so a digit appends rather than replaces.
        assertEquals("-53", state.press(Key.Digit(3)).display)
    }

    @Test
    fun `divide by zero reports an error and clears on the next key`() {
        val error = run(Key.Digit(5), Key.Op(Operator.Divide), Key.Digit(0), Key.Equals)
        assertEquals(CalcError.DivideByZero, error.error)
        val recovered = error.press(Key.Digit(7))
        assertEquals("7", recovered.display)
    }

    @Test
    fun `clear resets everything`() {
        val state = run(Key.Digit(9), Key.Op(Operator.Add), Key.Digit(9), Key.Clear)
        assertEquals(CalculatorState(), state)
    }

    @Test
    fun `entry length is capped`() {
        val state = "1234567890123456".fold(CalculatorState()) { acc, char ->
            acc.press(Key.Digit(char.digitToInt()))
        }
        assertEquals("123456789012", state.display)
    }

    @Test
    fun `expression shows the pending operation`() {
        val state = run(Key.Digit(1), Key.Digit(2), Key.Op(Operator.Multiply))
        assertEquals("12 ×", state.expression)
        assertEquals("", state.press(Key.Digit(7)).press(Key.Equals).expression)
    }

    @Test
    fun `results avoid scientific notation`() {
        assertEquals("0.0000001", eval("0.001", Operator.Multiply, "0.0001"))
    }

    // --- Scientific functions -------------------------------------------------

    private fun applied(entry: String, function: UnaryFunction): CalculatorState =
        (keysFor(entry) + Key.Func(function)).fold(CalculatorState()) { s, k -> s.press(k) }

    @Test
    fun `trigonometry works in degrees by default`() {
        assertEquals("0.5", applied("30", UnaryFunction.Sin).display)
        assertEquals("1", applied("0", UnaryFunction.Cos).display)
        assertEquals("1", applied("45", UnaryFunction.Tan).display)
    }

    @Test
    fun `trigonometry follows the angle mode`() {
        val radians = CalculatorState().press(Key.ToggleAngleMode)
        assertEquals(AngleMode.Radians, radians.angleMode)
        val sinOfZero = digits("0").fold(radians) { s, k -> s.press(k) }
            .press(Key.Func(UnaryFunction.Sin))
        assertEquals("0", sinOfZero.display)
    }

    @Test
    fun `tangent is undefined at ninety degrees`() {
        assertEquals(CalcError.InvalidInput, applied("90", UnaryFunction.Tan).error)
    }

    @Test
    fun `inverse trigonometry rejects out of range input`() {
        assertEquals("30", applied("0.5", UnaryFunction.Asin).display)
        assertEquals(CalcError.InvalidInput, applied("2", UnaryFunction.Asin).error)
    }

    @Test
    fun `logarithms reject non positive input`() {
        assertEquals("2", applied("100", UnaryFunction.Log10).display)
        assertEquals("3", applied("1000", UnaryFunction.Log10).display)
        assertEquals("0", applied("1", UnaryFunction.Ln).display)
        // e itself round-trips; a hand-typed truncation of e would not.
        val lnOfE = CalculatorState().press(Key.Const(Constant.E))
            .press(Key.Func(UnaryFunction.Ln))
        assertEquals("1", lnOfE.display)
        assertEquals(CalcError.InvalidInput, applied("0", UnaryFunction.Ln).error)
        assertEquals(CalcError.InvalidInput, applied("-1", UnaryFunction.Log10).error)
    }

    @Test
    fun `roots powers and reciprocals`() {
        assertEquals("3", applied("9", UnaryFunction.Sqrt).display)
        assertEquals("81", applied("9", UnaryFunction.Square).display)
        assertEquals("0.25", applied("4", UnaryFunction.Reciprocal).display)
        assertEquals(CalcError.InvalidInput, applied("-9", UnaryFunction.Sqrt).error)
        assertEquals(CalcError.DivideByZero, applied("0", UnaryFunction.Reciprocal).error)
    }

    @Test
    fun `power keeps integer exponents exact`() {
        assertEquals("1024", eval("2", Operator.Power, "10"))
        assertEquals("0.125", eval("2", Operator.Power, "-3"))
        assertEquals("3", eval("9", Operator.Power, "0.5"))
    }

    @Test
    fun `factorial is exact and bounded`() {
        assertEquals("1", applied("0", UnaryFunction.Factorial).display)
        assertEquals("120", applied("5", UnaryFunction.Factorial).display)
        // 18! is the widest factorial the display can show without rounding.
        assertEquals("6402373705728000", applied("18", UnaryFunction.Factorial).display)
        assertEquals(CalcError.Overflow, applied("19", UnaryFunction.Factorial).error)
        assertEquals(CalcError.InvalidInput, applied("2.5", UnaryFunction.Factorial).error)
        assertEquals(CalcError.InvalidInput, applied("-3", UnaryFunction.Factorial).error)
    }

    @Test
    fun `constants load as a fresh entry`() {
        val pi = CalculatorState().press(Key.Const(Constant.Pi))
        assertTrue(pi.display.startsWith("3.14159265"))
        assertEquals("5", pi.press(Key.Digit(5)).display.take(1))
    }

    // --- Memory ---------------------------------------------------------------

    @Test
    fun `memory accumulates and recalls`() {
        var state = run(Key.Digit(7), Key.Mem(MemoryOp.Add))
        assertTrue(state.hasMemory)
        state = state.press(Key.Digit(3)).press(Key.Mem(MemoryOp.Add))
        assertEquals("10", format(state.memory))
        assertEquals("10", state.press(Key.Mem(MemoryOp.Recall)).display)
        state = state.press(Key.Digit(4)).press(Key.Mem(MemoryOp.Subtract))
        assertEquals("6", format(state.memory))
    }

    @Test
    fun `a digit replaces a computed value instead of appending to it`() {
        // sin 30 = 0.5; typing 3 means 3, not 0.53.
        assertEquals("3", applied("30", UnaryFunction.Sin).press(Key.Digit(3)).display)
        // …but the computed value still folds as an operand.
        val folded = applied("30", UnaryFunction.Sin)
            .press(Key.Op(Operator.Add)).press(Key.Digit(1)).press(Key.Equals)
        assertEquals("1.5", folded.display)
    }

    @Test
    fun `memory survives clear but not memory clear`() {
        val stored = run(Key.Digit(9), Key.Mem(MemoryOp.Add), Key.Clear)
        assertEquals("9", format(stored.memory))
        assertEquals("0", stored.display)
        assertTrue(!stored.press(Key.Mem(MemoryOp.Clear)).hasMemory)
    }

    @Test
    fun `angle mode survives clear and errors`() {
        val radians = CalculatorState().press(Key.ToggleAngleMode)
        assertEquals(AngleMode.Radians, radians.press(Key.Clear).angleMode)
        val errored = radians.press(Key.Digit(1)).press(Key.Op(Operator.Divide))
            .press(Key.Digit(0)).press(Key.Equals)
        assertEquals(AngleMode.Radians, errored.press(Key.Digit(1)).angleMode)
    }

    // --- History labelling ----------------------------------------------------

    @Test
    fun `pending evaluation describes what equals would do`() {
        val pending = run(Key.Digit(1), Key.Digit(2), Key.Op(Operator.Multiply), Key.Digit(7))
        assertEquals("12 × 7", pending.pendingEvaluation())

        val repeat = pending.press(Key.Equals)
        assertEquals("84 × 7", repeat.pendingEvaluation())

        assertEquals(null, CalculatorState().pendingEvaluation())
    }
}
