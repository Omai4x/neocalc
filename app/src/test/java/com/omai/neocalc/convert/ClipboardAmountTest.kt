package com.omai.neocalc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardAmountTest {

    @Test
    fun `a symbol in front names the currency`() {
        val hit = ClipboardAmount.parse("$49.99")
        assertNotNull(hit)
        assertEquals(49.99, hit!!.amount, 1e-9)
        assertEquals("USD", hit.code)
    }

    @Test
    fun `a symbol after the number works too`() {
        val hit = ClipboardAmount.parse("1 234,50 €")
        assertNotNull(hit)
        assertEquals(1234.50, hit!!.amount, 1e-9)
        assertEquals("EUR", hit.code)
    }

    @Test
    fun `an ISO code anywhere in the text is enough`() {
        val hit = ClipboardAmount.parse("Total: NGN 25,000")
        assertNotNull(hit)
        assertEquals(25000.0, hit!!.amount, 1e-9)
        assertEquals("NGN", hit.code)
    }

    @Test
    fun `a bare number counts only when it is the whole clipboard`() {
        assertEquals(1500.0, ClipboardAmount.parse("1500")!!.amount, 1e-9)
        assertNull(ClipboardAmount.parse("1500")!!.code)
        // A sentence that merely contains a number is not a price.
        assertNull(ClipboardAmount.parse("meeting at 1500 hours in room 4"))
    }

    @Test
    fun `both decimal conventions are read correctly`() {
        assertEquals(1234.56, ClipboardAmount.normalise("1,234.56")!!, 1e-9)
        assertEquals(1234.56, ClipboardAmount.normalise("1.234,56")!!, 1e-9)
        assertEquals(1234.0, ClipboardAmount.normalise("1,234")!!, 1e-9)
        assertEquals(49.99, ClipboardAmount.normalise("49.99")!!, 1e-9)
    }

    @Test
    fun `nothing on the clipboard means no suggestion`() {
        assertNull(ClipboardAmount.parse(null))
        assertNull(ClipboardAmount.parse(""))
        assertNull(ClipboardAmount.parse("   "))
        assertNull(ClipboardAmount.parse("hello world"))
        assertNull(ClipboardAmount.parse("$0.00"))
    }

    @Test
    fun `a long paste is ignored rather than scanned for prices`() {
        assertNull(ClipboardAmount.parse("word ".repeat(60) + "$10"))
    }
}
