package com.omai.neocalc.convert

import android.content.Context

/**
 * The small amount of currency state worth remembering between launches: which
 * codes the user pinned, which ones they reached for lately, and the pair they
 * were last converting (which is also what the home-screen widget shows).
 */
object ConvertPrefs {

    private const val PREFS = "neocalc.convert"
    private const val KEY_FAVOURITES = "favourites"
    private const val KEY_RECENTS = "recents"
    private const val KEY_FROM = "pair_from"
    private const val KEY_TO = "pair_to"

    /** Enough for a board that still fits on a phone screen without scrolling. */
    const val MAX_FAVOURITES = 6

    /** Short by design: a recents list you have to read is not a shortcut. */
    const val MAX_RECENTS = 5

    /**
     * What a first-time user sees pinned. The world's most traded currencies, so
     * the board and the modal are useful before anyone has configured anything.
     */
    val DEFAULT_FAVOURITES = listOf("USD", "EUR", "GBP", "JPY")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favourites(context: Context): List<String> =
        prefs(context).getString(KEY_FAVOURITES, null)
            ?.let(::decodeList)
            ?: DEFAULT_FAVOURITES

    /**
     * Toggles a pin and returns the new set. Capped rather than errored: pinning a
     * seventh currency drops the oldest pin, which is friendlier than refusing.
     */
    fun toggleFavourite(context: Context, code: String): List<String> {
        val current = favourites(context)
        val updated = if (code in current) {
            current - code
        } else {
            (current + code).takeLast(MAX_FAVOURITES)
        }
        prefs(context).edit().putString(KEY_FAVOURITES, encodeList(updated)).apply()
        return updated
    }

    /** Used by an import, which supplies a whole list rather than one toggle. */
    fun replaceFavourites(context: Context, codes: List<String>): List<String> {
        val cleaned = codes.filter { it.length == 3 }.distinct().takeLast(MAX_FAVOURITES)
        prefs(context).edit().putString(KEY_FAVOURITES, encodeList(cleaned)).apply()
        return cleaned
    }

    fun recents(context: Context): List<String> =
        prefs(context).getString(KEY_RECENTS, null)?.let(::decodeList).orEmpty()

    /** Most-recently-used first, no duplicates. */
    fun recordRecent(context: Context, code: String): List<String> {
        val updated = (listOf(code) + recents(context).filterNot { it == code })
            .take(MAX_RECENTS)
        prefs(context).edit().putString(KEY_RECENTS, encodeList(updated)).apply()
        return updated
    }

    /** The pair the widget renders, refreshed every time the user changes one. */
    fun savePair(context: Context, from: String, to: String) {
        prefs(context).edit()
            .putString(KEY_FROM, from)
            .putString(KEY_TO, to)
            .apply()
    }

    fun pair(context: Context): Pair<String, String> = with(prefs(context)) {
        (getString(KEY_FROM, null) ?: "USD") to (getString(KEY_TO, null) ?: "EUR")
    }

    // Comma-separated rather than a StringSet: order is the whole point of a
    // recents list, and SharedPreferences string sets are unordered.
    internal fun encodeList(codes: List<String>): String = codes.joinToString(",")

    internal fun decodeList(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.length == 3 }
}
