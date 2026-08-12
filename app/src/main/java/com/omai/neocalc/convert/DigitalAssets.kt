package com.omai.neocalc.convert

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Crypto and precious metals, folded into the same rate table as fiat.
 *
 * The fiat providers do not quote these, so they come from a second keyless
 * source and are merged in. Once merged they are ordinary currency codes: the
 * picker, the board, the widget and the trend all work on them unchanged.
 *
 * Gold is quoted through PAX Gold, a token redeemable for one troy ounce of
 * London Good Delivery gold, so XAU here tracks the spot price of an ounce. It
 * is a proxy, not a bullion desk quote - close enough to be useful, and labelled
 * so nobody mistakes it for a dealing price. Silver is deliberately absent: no
 * keyless source quotes it reliably, and a wrong number is worse than none.
 */
object DigitalAssets {

    private const val ENDPOINT = "https://api.coingecko.com/api/v3/simple/price"

    /** ISO-style code → the provider's id for it. */
    private val ASSETS = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "XRP" to "ripple",
        "SOL" to "solana",
        "ADA" to "cardano",
        "DOGE" to "dogecoin",
        "LTC" to "litecoin",
        "XAU" to "pax-gold",
    )

    val CODES: List<String> get() = ASSETS.keys.toList()

    fun isAsset(code: String) = code in ASSETS

    /**
     * How many of [code] one unit of [base] buys, for every supported asset.
     *
     * The provider quotes the other way round - the price of one coin in fiat -
     * so each value is inverted here to match the direction a rate table uses.
     */
    suspend fun fetch(base: String): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        runCatching {
            val ids = ASSETS.values.joinToString(",")
            val body = CurrencyApi.get(
                "$ENDPOINT?ids=$ids&vs_currencies=${base.lowercase()}",
            )
            parse(body, base)
        }
    }

    /** `{"bitcoin":{"usd":64210.5}, …}` */
    internal fun parse(body: String, base: String): Map<String, Double> {
        val json = JSONObject(body)
        val key = base.lowercase()
        return ASSETS.mapNotNull { (code, id) ->
            val price = json.optJSONObject(id)?.optDouble(key, Double.NaN) ?: Double.NaN
            // A price of zero would invert to infinity and poison the table.
            if (price.isNaN() || price <= 0.0) null else code to 1.0 / price
        }.toMap()
    }

    /** Display names, since these are not in the ISO 4217 table. */
    fun nameOf(code: String): String = when (code) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "XRP" -> "XRP"
        "SOL" -> "Solana"
        "ADA" -> "Cardano"
        "DOGE" -> "Dogecoin"
        "LTC" -> "Litecoin"
        "XAU" -> "Gold (troy ounce)"
        else -> code
    }

    fun glyphOf(code: String): String = when (code) {
        "BTC" -> "₿"
        "ETH" -> "Ξ"
        "XAU" -> "Au"
        else -> "◈"
    }
}
