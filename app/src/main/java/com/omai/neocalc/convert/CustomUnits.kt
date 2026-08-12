package com.omai.neocalc.convert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A unit the user invented - "1 crate = 24 bottles", "1 plot = 464.5 m²".
 *
 * Stored as a factor against another unit the app already knows, which is what
 * makes a personal unit usable everywhere the built-in ones are: once a crate is
 * defined in terms of bottles, converting crates to anything bottles can reach
 * is the same arithmetic as any other conversion.
 */
data class CustomUnit(
    val label: String,
    val symbol: String,
    val category: UnitCategory,
    /** How many of the category's base unit one of these is worth. */
    val factor: Double,
) {
    fun toMeasure() = Measure(label, symbol, factor)
}

object CustomUnits {

    private const val PREFS = "neocalc.units"
    private const val KEY = "custom"

    /** Enough to be useful, few enough that the picker stays readable. */
    const val MAX = 24

    fun all(context: Context): List<CustomUnit> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun forCategory(context: Context, category: UnitCategory): List<CustomUnit> =
        all(context).filter { it.category == category }

    /**
     * Adds a unit defined as [amount] of an existing [reference] measure - the
     * way people actually describe one ("a crate is 24 bottles"), rather than
     * making them work out a factor against the SI base themselves.
     */
    fun add(
        context: Context,
        label: String,
        symbol: String,
        category: UnitCategory,
        amount: Double,
        reference: Measure,
    ): List<CustomUnit> {
        val trimmedLabel = label.trim().ifBlank { return all(context) }
        val trimmedSymbol = symbol.trim().ifBlank { trimmedLabel.take(3) }
        if (!amount.isFinite() || amount <= 0.0) return all(context)
        // Offsets would make this ambiguous ("2 °C worth of X" means nothing),
        // so a reference with one is rejected rather than silently mishandled.
        if (reference.offset != 0.0) return all(context)

        val unit = CustomUnit(
            label = trimmedLabel,
            symbol = trimmedSymbol,
            category = category,
            factor = amount * reference.factor,
        )
        val updated = (all(context).filterNot { it.label.equals(trimmedLabel, true) } + unit)
            .takeLast(MAX)
        save(context, updated)
        return updated
    }

    /** Used by an import, which supplies a whole list at once. */
    fun replaceAll(context: Context, units: List<CustomUnit>): List<CustomUnit> {
        val capped = units.takeLast(MAX)
        save(context, capped)
        return capped
    }

    fun remove(context: Context, unit: CustomUnit): List<CustomUnit> {
        val updated = all(context).filterNot { it.label == unit.label && it.category == unit.category }
        save(context, updated)
        return updated
    }

    private fun save(context: Context, units: List<CustomUnit>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(units))
            .apply()
    }

    internal fun encode(units: List<CustomUnit>): String = JSONArray().apply {
        units.forEach { unit ->
            put(
                JSONObject().apply {
                    put("label", unit.label)
                    put("symbol", unit.symbol)
                    put("category", unit.category.name)
                    put("factor", unit.factor)
                },
            )
        }
    }.toString()

    /** A corrupt or outdated entry is dropped, never fatal. */
    internal fun decode(json: String?): List<CustomUnit> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val category = runCatching { UnitCategory.valueOf(item.getString("category")) }
                    .getOrNull() ?: return@mapNotNull null
                val factor = item.getDouble("factor")
                if (!factor.isFinite() || factor <= 0.0) return@mapNotNull null
                CustomUnit(
                    label = item.getString("label"),
                    symbol = item.optString("symbol", item.getString("label").take(3)),
                    category = category,
                    factor = factor,
                )
            }
        }.getOrDefault(emptyList())
    }
}
