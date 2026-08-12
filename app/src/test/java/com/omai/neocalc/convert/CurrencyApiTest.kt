package com.omai.neocalc.convert

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Parsing only - no network. These pin the two response shapes so a provider
 * changing its JSON is caught here rather than as an empty picker on a device.
 *
 * org.json in android.jar is a stub for local unit tests: every method throws
 * "not mocked". Adding `testImplementation("org.json:json:20240303")` puts a real
 * implementation on the test classpath and switches these on. Until then they
 * skip rather than fail, so the suite stays honest instead of red for a reason
 * that has nothing to do with the code under test.
 */
class CurrencyApiTest {

    @Before
    fun requireRealJsonImplementation() {
        val available = runCatching { JSONObject("""{"a":"b"}""").optString("a") == "b" }
            .getOrDefault(false)
        assumeTrue("org.json is stubbed in local unit tests - see class docs", available)
    }

    private val primaryBody = """
        {
          "result": "success",
          "base_code": "USD",
          "time_last_update_utc": "Tue, 11 Aug 2026 00:02:31 +0000",
          "rates": { "USD": 1, "EUR": 0.9142, "JPY": 148.31, "NGN": 1587.5 }
        }
    """.trimIndent()

    private val frankfurterBody = """
        {
          "amount": 1.0,
          "base": "USD",
          "date": "2026-08-11",
          "rates": { "EUR": 0.9142, "GBP": 0.7801 }
        }
    """.trimIndent()

    @Test
    fun `parses the wide provider`() {
        val table = CurrencyApi.parsePrimary(primaryBody, "USD")
        assertEquals("USD", table.base)
        assertEquals("Tue, 11 Aug 2026", table.date)
        assertEquals(0.9142, table.rateFor("EUR")!!, 1e-9)
        assertEquals(1587.5, table.rateFor("NGN")!!, 1e-9)
    }

    @Test
    fun `parses the fallback provider`() {
        val table = CurrencyApi.parseFrankfurter(frankfurterBody, "USD")
        assertEquals("USD", table.base)
        assertEquals("2026-08-11", table.date)
        assertEquals(0.7801, table.rateFor("GBP")!!, 1e-9)
    }

    @Test
    fun `a table always quotes its own base as one`() {
        val table = CurrencyApi.parseFrankfurter(frankfurterBody, "USD")
        assertEquals(1.0, table.rateFor("USD")!!, 1e-12)
        assertTrue(table.currencies.contains("USD"))
    }

    @Test
    fun `unknown codes have no rate`() {
        assertNull(CurrencyApi.parseFrankfurter(frankfurterBody, "USD").rateFor("XYZ"))
    }

    @Test
    fun `a failed result is rejected so the fallback can take over`() {
        val body = """{"result":"error","error-type":"unsupported-code"}"""
        val failure = runCatching { CurrencyApi.parsePrimary(body, "USD") }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `currency list is offered before the first fetch`() {
        assertTrue(CurrencyApi.FALLBACK_CURRENCIES.size > 150)
        assertTrue(CurrencyApi.FALLBACK_CURRENCIES.contains("NGN"))
        assertEquals(
            CurrencyApi.FALLBACK_CURRENCIES.size,
            CurrencyApi.FALLBACK_CURRENCIES.toSet().size,
        )
    }
}
