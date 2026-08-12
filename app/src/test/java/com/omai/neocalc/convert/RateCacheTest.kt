package com.omai.neocalc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RateCacheTest {

    private val table = RateTable(
        base = "USD",
        date = "2026-08-11",
        rates = mapOf("EUR" to 0.9231, "GBP" to 0.7845, "JPY" to 147.22),
    )

    @Test
    fun `a table survives a round trip through storage`() {
        val cached = RateCache.decode(RateCache.encode(table, fetchedAt = 1_000L))
        assertEquals(table.base, cached!!.table.base)
        assertEquals(table.date, cached.table.date)
        assertEquals(table.rates, cached.table.rates)
        assertEquals(1_000L, cached.fetchedAt)
    }

    @Test
    fun `unreadable entries decode to no cache rather than throwing`() {
        assertNull(RateCache.decode(null))
        assertNull(RateCache.decode(""))
        assertNull(RateCache.decode("not json"))
        assertNull(RateCache.decode("""{"base":"USD"}"""))
        // A future format version must not be parsed as if it were this one.
        assertNull(RateCache.decode("""{"v":99,"base":"USD","rates":{},"fetchedAt":1}"""))
    }

    @Test
    fun `freshness is measured in whole days`() {
        val now = TimeUnit.DAYS.toMillis(10)
        assertTrue(CachedRates(table, now).isFresh(now))
        assertTrue(CachedRates(table, now - TimeUnit.HOURS.toMillis(23)).isFresh(now))
        assertFalse(CachedRates(table, now - TimeUnit.DAYS.toMillis(1)).isFresh(now))
        assertEquals(3, CachedRates(table, now - TimeUnit.DAYS.toMillis(3)).ageInDays(now))
    }

    @Test
    fun `a clock that jumps backwards does not produce a negative age`() {
        val now = TimeUnit.DAYS.toMillis(1)
        assertEquals(0, daysSince(now + TimeUnit.DAYS.toMillis(5), now))
    }
}
