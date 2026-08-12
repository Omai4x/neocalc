package com.omai.neocalc.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionParserTest {

    private fun eval(text: String) = ExpressionParser.evaluate(text)

    @Test
    fun `plain numbers pass straight through`() {
        assertEquals(100.0, eval("100")!!, 1e-9)
        assertEquals(0.5, eval("0.5")!!, 1e-9)
        assertEquals(-42.0, eval("-42")!!, 1e-9)
    }

    @Test
    fun `arithmetic respects precedence`() {
        assertEquals(14.0, eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, eval("(2+3)*4")!!, 1e-9)
        assertEquals(25.0, eval("(80+20)/4")!!, 1e-9)
        assertEquals(42.0, eval("12*3.5")!!, 1e-9)
    }

    @Test
    fun `power is right-associative`() {
        assertEquals(512.0, eval("2^3^2")!!, 1e-9)
    }

    @Test
    fun `calculator glyphs and keyboard glyphs mean the same thing`() {
        assertEquals(eval("6*7"), eval("6×7"))
        assertEquals(eval("6/2"), eval("6÷2"))
        assertEquals(eval("6-2"), eval("6−2"))
        assertEquals(42.0, eval("6x7")!!, 1e-9)
    }

    @Test
    fun `postfix percent is a hundredth`() {
        assertEquals(0.2, eval("20%")!!, 1e-9)
        assertEquals(110.0, eval("100+10%*100")!!, 1e-9)
    }

    @Test
    fun `commas group digits rather than splitting the number`() {
        assertEquals(1234.5, eval("1,234.5")!!, 1e-9)
        assertEquals(2469.0, eval("1,234+1,235")!!, 1e-9)
        // A space, unlike a comma, separates tokens - "1 234" is two numbers
        // side by side, which is no more valid than "2 3".
        assertNull(eval("1 234.5"))
    }

    @Test
    fun `malformed input is null rather than a guess`() {
        assertNull(eval(""))
        assertNull(eval("   "))
        assertNull(eval("2+"))
        assertNull(eval("(2+3"))
        assertNull(eval("2++"))
        assertNull(eval("abc"))
        assertNull(eval("2 3"))
    }

    @Test
    fun `division by zero is not a number`() {
        assertNull(eval("1/0"))
    }

    @Test
    fun `isExpression only fires when there is arithmetic to do`() {
        assertFalse(ExpressionParser.isExpression("100"))
        assertFalse(ExpressionParser.isExpression("-42"))
        assertFalse(ExpressionParser.isExpression("0.5"))
        assertTrue(ExpressionParser.isExpression("2+2"))
        assertTrue(ExpressionParser.isExpression("10-2"))
        assertTrue(ExpressionParser.isExpression("6×7"))
    }
}
