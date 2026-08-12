package com.omai.neocalc.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BillSplitTest {

    private fun sum(shares: List<BigDecimal>) =
        shares.fold(BigDecimal.ZERO) { acc, value -> acc + value }

    @Test
    fun `an even split is even`() {
        val result = Bill.split(amount = 90.0, people = 3)
        assertEquals(3, result.shares.size)
        assertEquals(BigDecimal("30.00"), result.perPerson)
        assertFalse(result.uneven)
    }

    @Test
    fun `the odd penny is handed out, never lost`() {
        // 100 / 3 is 33.33 three times, which is 99.99. Somebody pays 33.34.
        val result = Bill.split(amount = 100.0, people = 3)
        assertEquals(result.total, sum(result.shares))
        assertTrue(result.uneven)
        assertEquals(BigDecimal("33.34"), result.shares.first())
        assertEquals(BigDecimal("33.33"), result.shares.last())
    }

    @Test
    fun `shares never differ by more than a penny`() {
        listOf(1, 2, 3, 4, 5, 6, 7, 9, 11, 13).forEach { people ->
            listOf(10.0, 33.33, 99.99, 100.0, 1234.56).forEach { amount ->
                val result = Bill.split(amount, tipPercent = 12.5, people = people)
                assertEquals("$amount / $people", result.total, sum(result.shares))
                val spread = result.shares.max() - result.shares.min()
                assertTrue(spread <= BigDecimal("0.01"))
            }
        }
    }

    @Test
    fun `tip and tax are applied to the right base`() {
        val plain = Bill.split(amount = 100.0, taxPercent = 10.0, tipPercent = 10.0)
        assertEquals(BigDecimal("10.00"), plain.tax)
        assertEquals(BigDecimal("10.00"), plain.tip)
        assertEquals(BigDecimal("120.00"), plain.total)

        val onTax = Bill.split(
            amount = 100.0,
            taxPercent = 10.0,
            tipPercent = 10.0,
            tipOnTax = true,
        )
        assertEquals(BigDecimal("11.00"), onTax.tip)
        assertEquals(BigDecimal("121.00"), onTax.total)
    }

    @Test
    fun `rounding up gives everyone the same figure and grows the tip`() {
        val result = Bill.split(amount = 100.0, people = 3, roundUp = true)
        assertFalse(result.uneven)
        assertEquals(BigDecimal("34.00"), result.perPerson)
        assertEquals(BigDecimal("102.00"), result.total)
        // The extra two pounds land in the tip, not in thin air.
        assertEquals(BigDecimal("2.00"), result.tip)
        assertEquals(result.total, sum(result.shares))
    }

    @Test
    fun `an itemised split charges each person for what they had`() {
        val result = Bill.splitByItems(listOf(10.0, 20.0, 30.0), tipPercent = 10.0)
        assertEquals(BigDecimal("66.00"), result.total)
        assertEquals(result.total, sum(result.shares))
        assertEquals(BigDecimal("11.00"), result.shares[0])
        assertEquals(BigDecimal("22.00"), result.shares[1])
        assertEquals(BigDecimal("33.00"), result.shares[2])
    }

    @Test
    fun `nonsense input produces zeroes rather than an exception`() {
        val result = Bill.split(amount = Double.NaN, people = 0)
        assertEquals(BigDecimal("0.00"), result.total)
        assertEquals(1, result.shares.size)
    }
}
