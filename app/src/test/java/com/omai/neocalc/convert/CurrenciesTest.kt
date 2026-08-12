package com.omai.neocalc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrenciesTest {

    @Test
    fun `flag is built from the region code`() {
        assertEquals("🇺🇸", Currencies.info("USD").flag)
        assertEquals("🇯🇵", Currencies.info("JPY").flag)
        assertEquals("🇪🇺", Currencies.info("EUR").flag)
    }

    @Test
    fun `supranational codes fall back to a neutral flag`() {
        assertEquals("🏳️", Currencies.info("XOF").flag)
    }

    @Test
    fun `an unknown code still yields a usable entry`() {
        val info = Currencies.info("ZZZ")
        assertEquals("ZZZ", info.code)
        assertEquals("ZZZ", info.name)
        assertEquals("🏳️", info.flag)
    }

    @Test
    fun `every advertised currency has display metadata`() {
        val missing = CurrencyApi.FALLBACK_CURRENCIES
            .filter { Currencies.info(it).name == it }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `search matches code, name and region`() {
        val gbp = Currencies.info("GBP")
        assertTrue(gbp.matches("gbp"))
        assertTrue(gbp.matches("pound"))
        assertTrue(gbp.matches("GB"))
        assertFalse(gbp.matches("yen"))
    }

    @Test
    fun `a blank query matches everything`() {
        assertTrue(Currencies.info("USD").matches(""))
        assertTrue(Currencies.info("USD").matches("   "))
    }
}
