package com.omai.neocalc.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RateTrendTest {

    private val points = listOf(
        RatePoint("2026-07-01", 0.90),
        RatePoint("2026-07-02", 0.95),
        RatePoint("2026-07-03", 0.99),
    )

    @Test
    fun `a trend summarises its window`() {
        val trend = RateTrend.of(points)!!
        assertEquals(0.90, trend.first, 1e-9)
        assertEquals(0.99, trend.last, 1e-9)
        assertEquals(0.90, trend.low, 1e-9)
        assertEquals(0.99, trend.high, 1e-9)
        assertEquals(10.0, trend.changePercent, 1e-9)
    }

    @Test
    fun `normalise puts the low at the bottom and the high at the top`() {
        val trend = RateTrend.of(points)!!
        assertEquals(0f, trend.normalise(0.90), 1e-6f)
        assertEquals(1f, trend.normalise(0.99), 1e-6f)
    }

    @Test
    fun `a pegged pair draws down the middle instead of dividing by zero`() {
        val flat = RateTrend.of(
            listOf(RatePoint("2026-07-01", 1.0), RatePoint("2026-07-02", 1.0)),
        )!!
        assertEquals(0.5f, flat.normalise(1.0), 1e-6f)
        assertEquals(0.0, flat.changePercent, 1e-9)
    }

    @Test
    fun `a single point is not a line`() {
        assertNull(RateTrend.of(listOf(RatePoint("2026-07-01", 1.0))))
        assertNull(RateTrend.of(emptyList()))
        assertNotNull(RateTrend.of(points))
    }
}
