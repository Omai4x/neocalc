package com.omai.neocalc.smart

import com.omai.neocalc.convert.UnitCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalInputTest {

    @Test
    fun `a currency phrase names both sides`() {
        val result = NaturalInput.parse("300 dollars in naira")
        assertTrue(result is Understood.Currency)
        result as Understood.Currency
        assertEquals(300.0, result.amount, 1e-9)
        assertEquals("USD", result.from)
        assertEquals("NGN", result.to)
    }

    @Test
    fun `symbols count as naming a currency`() {
        val result = NaturalInput.parse("how much is \$50 in eur") as Understood.Currency
        assertEquals(50.0, result.amount, 1e-9)
        assertEquals("USD", result.from)
        assertEquals("EUR", result.to)
    }

    @Test
    fun `a bare amount with a currency still parses, with no target`() {
        val result = NaturalInput.parse("£20") as Understood.Currency
        assertEquals(20.0, result.amount, 1e-9)
        assertEquals("GBP", result.from)
        assertNull(result.to)
    }

    @Test
    fun `unit phrases resolve to a category and two units`() {
        val result = NaturalInput.parse("15 miles in km") as Understood.Units
        assertEquals(UnitCategory.Length, result.category)
        assertEquals(15.0, result.amount, 1e-9)
        assertEquals("mi", UnitCategory.Length.units[result.fromIndex].symbol)
        assertEquals("km", UnitCategory.Length.units[result.toIndex].symbol)
    }

    @Test
    fun `a cross-category request is refused rather than guessed at`() {
        // "5 kg in miles" is not a conversion; falling back to arithmetic is
        // wrong too, so this must not come back as Units.
        val result = NaturalInput.parse("5 kg in miles")
        assertTrue(result !is Understood.Units)
    }

    @Test
    fun `discounts are worked out`() {
        val result = NaturalInput.parse("20% off 45") as Understood.Discount
        assertEquals(45.0, result.amount, 1e-9)
        assertEquals(20.0, result.percent, 1e-9)
        assertEquals(36.0, result.result, 1e-9)
    }

    @Test
    fun `splits read both digits and words`() {
        val digits = NaturalInput.parse("split 120 between 3") as Understood.Split
        assertEquals(120.0, digits.amount, 1e-9)
        assertEquals(3, digits.people)

        val worded = NaturalInput.parse("split 120 four ways") as Understood.Split
        assertEquals(4, worded.people)
    }

    @Test
    fun `plain arithmetic falls through to the parser`() {
        val result = NaturalInput.parse("12*3.5") as Understood.Arithmetic
        assertEquals(42.0, result.value, 1e-9)
    }

    @Test
    fun `nothing usable returns null`() {
        assertNull(NaturalInput.parse(null))
        assertNull(NaturalInput.parse(""))
        assertNull(NaturalInput.parse("   "))
        assertNull(NaturalInput.parse("hello there"))
    }
}
