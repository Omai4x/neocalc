package com.omai.neocalc.convert

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Daily reference rates, keyed by currency code, relative to [base].
 * [date] is the day the rates were published, not the day they were fetched.
 */
data class RateTable(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
) {
    /** A table always quotes its own base as 1, which the API leaves implicit. */
    fun rateFor(code: String): Double? = if (code == base) 1.0 else rates[code]

    val currencies: List<String> get() = (rates.keys + base).sorted()
}

/** One daily close in a rate history, [date] as ISO `yyyy-MM-dd`. */
data class RatePoint(val date: String, val rate: Double)

/**
 * A pair's recent history, reduced to what a sparkline needs to draw itself and
 * what a caption needs to describe it.
 */
data class RateTrend(val points: List<RatePoint>) {
    val first: Double get() = points.first().rate
    val last: Double get() = points.last().rate
    val low: Double get() = points.minOf { it.rate }
    val high: Double get() = points.maxOf { it.rate }

    /** Percent change across the window; 0 when the pair is pegged and flat. */
    val changePercent: Double
        get() = if (first == 0.0) 0.0 else (last - first) / first * 100.0

    /**
     * Vertical position of [rate] in the window, 0 at the low and 1 at the high.
     * A flat series has no range to normalise against, so it draws down the
     * middle rather than dividing by zero.
     */
    fun normalise(rate: Double): Float {
        val range = high - low
        return if (range == 0.0) 0.5f else ((rate - low) / range).toFloat()
    }

    companion object {
        /** Two points is the minimum that can be a line rather than a dot. */
        fun of(points: List<RatePoint>): RateTrend? =
            if (points.size >= 2) RateTrend(points) else null
    }
}

/**
 * Live exchange rates, from two keyless providers so there is nothing to
 * provision and no secret to keep out of version control.
 *
 * Primary is open.er-api.com, which quotes ~160 currencies - the full ISO 4217
 * set including minor and pegged ones. Frankfurter (the ECB's ~30 majors) is the
 * fallback: narrower, but an official source, so a failure of the wide provider
 * degrades to fewer currencies rather than to none.
 *
 * Deliberately built on [HttpURLConnection] and org.json - both are part of the
 * platform, so live rates cost the project zero new dependencies.
 */
object CurrencyApi {

    private const val PRIMARY = "https://open.er-api.com/v6/latest"
    private const val FALLBACK_HOST = "https://api.frankfurter.app"
    private const val FALLBACK = "$FALLBACK_HOST/latest"
    private const val TIMEOUT_MS = 10_000

    /** Shown before the first successful fetch, so the picker is never empty. */
    val FALLBACK_CURRENCIES = listOf(
        "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN",
        "BAM", "BBD", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BRL",
        "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP", "CNY",
        "COP", "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP",
        "ERN", "ETB", "EUR", "FJD", "FKP", "FOK", "GBP", "GEL", "GGP", "GHS",
        "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK", "HTG", "HUF",
        "IDR", "ILS", "IMP", "INR", "IQD", "IRR", "ISK", "JEP", "JMD", "JOD",
        "JPY", "KES", "KGS", "KHR", "KID", "KMF", "KRW", "KWD", "KYD", "KZT",
        "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD",
        "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MYR", "MZN",
        "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK",
        "PHP", "PKR", "PLN", "PYG", "QAR", "RON", "RSD", "RUB", "RWF", "SAR",
        "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SOS", "SRD", "SSP",
        "STN", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD",
        "TVD", "TWD", "TZS", "UAH", "UGX", "USD", "UYU", "UZS", "VES", "VND",
        "VUV", "WST", "XAF", "XCD", "XCG", "XDR", "XOF", "XPF", "YER", "ZAR",
        "ZMW", "ZWL",
    )

    /**
     * Tries the wide provider first and falls back to the ECB set. Only if both
     * fail does this report failure, which is what makes a flaky network degrade
     * gracefully instead of emptying the picker.
     */
    suspend fun fetchRates(base: String): Result<RateTable> = withContext(Dispatchers.IO) {
        runCatching { parsePrimary(get("$PRIMARY/$base"), base) }
            .recoverCatching { parseFrankfurter(get("$FALLBACK?from=$base"), base) }
            .map { table ->
                // Crypto and gold come from a different provider and are folded
                // in here, so everything downstream sees one table of codes and
                // never has to know which source a rate came from. A failure
                // costs those codes, not the whole fetch.
                val assets = DigitalAssets.fetch(table.base).getOrNull().orEmpty()
                if (assets.isEmpty()) table else table.copy(rates = table.rates + assets)
            }
    }

    /**
     * Daily closes for one pair over the last [days], oldest first.
     *
     * Only the ECB provider offers history, and only for its ~30 majors, so an
     * exotic pair simply fails here - the sparkline is an enhancement and the
     * caller is expected to hide it rather than surface an error.
     */
    suspend fun fetchSeries(
        base: String,
        quote: String,
        days: Int = 30,
    ): Result<List<RatePoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$FALLBACK_HOST/${daysAgo(days)}..?from=$base&to=$quote"
            parseSeries(get(url), quote).also {
                if (it.size < 2) error("Not enough history for $base/$quote")
            }
        }
    }

    /**
     * ISO date [days] before today. Calendar rather than java.time because
     * minSdk is 23 and the project carries no desugaring configuration.
     */
    private fun daysAgo(days: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    /** frankfurter.app: `{"rates":{"2026-08-10":{"EUR":0.91}, …}}` */
    internal fun parseSeries(body: String, quote: String): List<RatePoint> {
        val rates = JSONObject(body).getJSONObject("rates")
        return rates.keys().asSequence()
            .mapNotNull { day ->
                val value = rates.getJSONObject(day).optDouble(quote, Double.NaN)
                if (value.isNaN()) null else RatePoint(day, value)
            }
            // The API returns an object, whose key order is not guaranteed; the
            // chart depends on chronology, and ISO dates sort lexicographically.
            .sortedBy { it.date }
            .toList()
    }

    internal fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Rate service returned ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** open.er-api.com: `{"result":"success","base_code":…,"rates":{…}}` */
    internal fun parsePrimary(body: String, requestedBase: String): RateTable {
        val json = JSONObject(body)
        if (json.optString("result") != "success") {
            error("Rate service reported ${json.optString("result")}")
        }
        return RateTable(
            base = json.optString("base_code", requestedBase),
            // "Tue, 11 Aug 2026 00:02:31 +0000" - the day is the useful part.
            date = json.optString("time_last_update_utc")
                .split(" ")
                .take(4)
                .joinToString(" ")
                .ifBlank { "" },
            rates = json.getJSONObject("rates").toDoubleMap(),
        )
    }

    /** frankfurter.app: `{"base":…,"date":"2026-08-11","rates":{…}}` */
    internal fun parseFrankfurter(body: String, requestedBase: String): RateTable {
        val json = JSONObject(body)
        return RateTable(
            base = json.optString("base", requestedBase),
            date = json.optString("date", ""),
            rates = json.getJSONObject("rates").toDoubleMap(),
        )
    }

    private fun JSONObject.toDoubleMap(): Map<String, Double> = buildMap {
        keys().forEach { code -> put(code, getDouble(code)) }
    }
}
