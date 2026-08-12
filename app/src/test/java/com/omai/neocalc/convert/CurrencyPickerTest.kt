package com.omai.neocalc.convert

import com.omai.neocalc.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sectioning rule behind the search modal - no Compose involved. */
class CurrencyPickerTest {

    private val codes = listOf("USD", "EUR", "GBP", "JPY", "NGN", "ZAR")

    @Test
    fun `browsing shows pinned, then recent, then everything`() {
        val sections = buildSections(
            codes = codes,
            query = "",
            favourites = listOf("EUR", "GBP"),
            recents = listOf("NGN", "EUR"),
        )
        assertEquals(
            listOf(R.string.currency_pinned, R.string.currency_recent, R.string.currency_all),
            sections.map { it.titleRes },
        )
        assertEquals(listOf("EUR", "GBP"), sections[0].entries.map { it.code })
        // EUR is pinned already, so it must not repeat under Recent.
        assertEquals(listOf("NGN"), sections[1].entries.map { it.code })
        assertEquals(codes, sections[2].entries.map { it.code })
    }

    @Test
    fun `searching flattens to a single ranked list`() {
        val sections = buildSections(codes, query = "pound", favourites = listOf("EUR"), recents = emptyList())
        assertEquals(1, sections.size)
        assertEquals(R.string.currency_all, sections[0].titleRes)
        assertEquals(listOf("GBP"), sections[0].entries.map { it.code })
    }

    @Test
    fun `no matches produces no sections, which is what drives the empty state`() {
        assertTrue(buildSections(codes, "qqq", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `pins and recents the provider no longer quotes are dropped`() {
        val sections = buildSections(
            codes = codes,
            query = "",
            favourites = listOf("EUR", "XYZ"),
            recents = listOf("ABC"),
        )
        assertEquals(listOf("EUR"), sections[0].entries.map { it.code })
        assertEquals(
            listOf(R.string.currency_pinned, R.string.currency_all),
            sections.map { it.titleRes },
        )
    }

    @Test
    fun `an empty favourites list simply has no pinned section`() {
        val sections = buildSections(codes, "", emptyList(), emptyList())
        assertEquals(listOf(R.string.currency_all), sections.map { it.titleRes })
    }
}
