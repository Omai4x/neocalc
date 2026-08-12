package com.omai.neocalc.convert

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Whole days between two instants, never negative - a device clock that moves
 * backwards must not make cached rates look like they came from the future.
 */
internal fun daysSince(then: Long, now: Long = System.currentTimeMillis()): Int =
    TimeUnit.MILLISECONDS.toDays((now - then).coerceAtLeast(0)).toInt()

/** A rate table together with the moment it was actually fetched. */
data class CachedRates(val table: RateTable, val fetchedAt: Long) {

    /** Whole days since the fetch - what the staleness caption counts in. */
    fun ageInDays(now: Long = System.currentTimeMillis()): Int = daysSince(fetchedAt, now)

    /**
     * Reference rates are published once a working day, so anything fetched today
     * is current. Past that the UI says how old the numbers are rather than
     * presenting them as live.
     */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = ageInDays(now) < 1
}

/**
 * Last-known rates on disk, keyed by base currency.
 *
 * Without this a cold start with no network shows nothing at all - the worst
 * moment for a converter to be useless is exactly when you're abroad or on a
 * plane. Yesterday's rate is wrong by a fraction of a percent; no rate is wrong
 * by everything.
 */
object RateCache {

    private const val PREFS = "neocalc.rates"
    private const val KEY_PREFIX = "table_"
    private const val VERSION = 1

    /** Bases worth keeping; beyond this the oldest entries are dropped. */
    private const val MAX_BASES = 8

    fun save(
        context: Context,
        table: RateTable,
        now: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_PREFIX + table.base, encode(table, now))

        // Each base is a separate ~160-entry table; a user who tries many bases
        // would otherwise grow this file without limit.
        val keys = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (keys.size >= MAX_BASES) {
            keys.map { it to decode(prefs.getString(it, null))?.fetchedAt }
                .sortedBy { it.second ?: 0L }
                .take(keys.size - MAX_BASES + 1)
                .forEach { (key, _) -> if (key != KEY_PREFIX + table.base) editor.remove(key) }
        }
        editor.apply()
    }

    fun load(context: Context, base: String): CachedRates? =
        decode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + base, null),
        )

    /** Any cached table at all, newest first - what the widget falls back to. */
    fun loadAny(context: Context): CachedRates? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { load(context, it.removePrefix(KEY_PREFIX)) }
            .maxByOrNull { it.fetchedAt }

    /**
     * Hand-rolled JSON rather than a serialisation library: the shape is three
     * fields and a flat map, and the project stays dependency-free.
     */
    internal fun encode(table: RateTable, fetchedAt: Long): String = JSONObject().apply {
        put("v", VERSION)
        put("base", table.base)
        put("date", table.date)
        put("fetchedAt", fetchedAt)
        put("rates", JSONObject(table.rates.mapValues { it.value }))
    }.toString()

    /** Null for anything unreadable - a corrupt entry must degrade to "no cache". */
    internal fun decode(json: String?): CachedRates? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            if (root.optInt("v") != VERSION) return null
            val rates = root.getJSONObject("rates")
            CachedRates(
                table = RateTable(
                    base = root.getString("base"),
                    date = root.optString("date"),
                    rates = buildMap {
                        rates.keys().forEach { code -> put(code, rates.getDouble(code)) }
                    },
                ),
                fetchedAt = root.getLong("fetchedAt"),
            )
        }.getOrNull()
    }
}
