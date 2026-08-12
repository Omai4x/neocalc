package com.omai.neocalc.smart

import com.omai.neocalc.calculator.ExpressionParser
import com.omai.neocalc.convert.Currencies
import com.omai.neocalc.convert.UnitCategory

/** What a phrase turned out to mean. */
sealed interface Understood {

    /** "300 dollars in naira" */
    data class Currency(val amount: Double, val from: String, val to: String?) : Understood

    /** "15 miles in km" */
    data class Units(
        val amount: Double,
        val category: UnitCategory,
        val fromIndex: Int,
        val toIndex: Int,
    ) : Understood

    /** "20% off 45" */
    data class Discount(val amount: Double, val percent: Double, val result: Double) : Understood

    /** "split 120 four ways" */
    data class Split(val amount: Double, val people: Int) : Understood

    /** Anything that is just arithmetic: "12*3.5". */
    data class Arithmetic(val expression: String, val value: Double) : Understood
}

/**
 * Turns a sentence into something the app can act on.
 *
 * Deliberately a hand-written matcher rather than anything clever: the set of
 * things people actually type at a calculator is small and highly patterned, and
 * a grammar this size is inspectable, instant, and works with no network.
 * Anything it does not recognise returns null, and the caller falls back to
 * treating the text as a plain expression.
 */
object NaturalInput {

    /** Words for currencies people type instead of the ISO code. */
    private val CURRENCY_WORDS = mapOf(
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD", "bucks" to "USD",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "sterling" to "GBP", "gbp" to "GBP", "quid" to "GBP",
        "yen" to "JPY", "jpy" to "JPY",
        "naira" to "NGN", "ngn" to "NGN",
        "rand" to "ZAR", "zar" to "ZAR",
        "rupee" to "INR", "rupees" to "INR", "inr" to "INR",
        "yuan" to "CNY", "renminbi" to "CNY", "cny" to "CNY",
        "franc" to "CHF", "francs" to "CHF", "chf" to "CHF",
        "peso" to "MXN", "pesos" to "MXN",
        "real" to "BRL", "reais" to "BRL",
        "shilling" to "KES", "shillings" to "KES",
        "cedi" to "GHS", "cedis" to "GHS",
        "bitcoin" to "BTC", "btc" to "BTC",
        "ether" to "ETH", "ethereum" to "ETH", "eth" to "ETH",
        "gold" to "XAU",
    )

    private val SYMBOLS = mapOf(
        '$' to "USD", '€' to "EUR", '£' to "GBP", '¥' to "JPY",
        '₦' to "NGN", '₹' to "INR", '₩' to "KRW",
    )

    /** Words and abbreviations for units, resolved against the unit tables. */
    private val UNIT_WORDS: Map<String, Pair<UnitCategory, Int>> = buildMap {
        UnitCategory.entries.forEach { category ->
            category.units.forEachIndexed { index, measure ->
                // Symbol, full name, and the plural people actually type.
                put(measure.symbol.lowercase(), category to index)
                put(measure.label.lowercase(), category to index)
                put(measure.label.lowercase() + "s", category to index)
            }
        }
        // A handful of everyday spellings the tables do not carry.
        put("kms", UnitCategory.Length to indexOf(UnitCategory.Length, "km"))
        put("miles", UnitCategory.Length to indexOf(UnitCategory.Length, "mi"))
        put("mile", UnitCategory.Length to indexOf(UnitCategory.Length, "mi"))
        put("feet", UnitCategory.Length to indexOf(UnitCategory.Length, "ft"))
        put("foot", UnitCategory.Length to indexOf(UnitCategory.Length, "ft"))
        put("inches", UnitCategory.Length to indexOf(UnitCategory.Length, "in"))
        put("pounds", UnitCategory.Mass to indexOf(UnitCategory.Mass, "lb"))
        put("kilos", UnitCategory.Mass to indexOf(UnitCategory.Mass, "kg"))
        put("celsius", UnitCategory.Temperature to indexOf(UnitCategory.Temperature, "°C"))
        put("fahrenheit", UnitCategory.Temperature to indexOf(UnitCategory.Temperature, "°F"))
    }

    private fun indexOf(category: UnitCategory, symbol: String) =
        category.units.indexOfFirst { it.symbol == symbol }.coerceAtLeast(0)

    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")
    private val WORD_NUMBERS = mapOf(
        "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    )

    fun parse(input: String?): Understood? {
        val text = input?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null

        discount(text)?.let { return it }
        split(text)?.let { return it }
        units(text)?.let { return it }
        currency(text)?.let { return it }

        // Falls through to plain arithmetic, which is what a calculator is for.
        return ExpressionParser.evaluate(text)?.let { Understood.Arithmetic(text, it) }
    }

    /** "20% off 45", "45 minus 20%" */
    private fun discount(text: String): Understood.Discount? {
        if ("%" !in text) return null
        if ("off" !in text && "discount" !in text) return null
        val numbers = NUMBER.findAll(text).map { normalise(it.value) }.toList()
        if (numbers.size < 2) return null
        // The percentage is whichever number carries the % sign.
        val percentIndex = NUMBER.findAll(text).indexOfFirst { match ->
            text.getOrNull(match.range.last + 1) == '%'
        }
        if (percentIndex < 0) return null
        val percent = numbers[percentIndex]
        val amount = numbers.filterIndexed { index, _ -> index != percentIndex }.firstOrNull()
            ?: return null
        return Understood.Discount(amount, percent, amount * (1 - percent / 100.0))
    }

    /** "split 120 four ways", "split 90 between 3" */
    private fun split(text: String): Understood.Split? {
        if (!text.startsWith("split") && "ways" !in text && "between" !in text) return null
        val numbers = NUMBER.findAll(text).map { normalise(it.value) }.toList()
        val worded = WORD_NUMBERS.entries.firstOrNull { it.key in text }?.value
        val amount = numbers.firstOrNull() ?: return null
        val people = worded ?: numbers.getOrNull(1)?.toInt() ?: return null
        return Understood.Split(amount, people.coerceIn(1, 50))
    }

    /** "15 miles in km", "180 cm to feet" */
    private fun units(text: String): Understood.Units? {
        val (left, right) = separator(text) ?: return null
        val amount = NUMBER.find(left)?.value?.let { normalise(it) } ?: return null
        val from = wordsOf(left).firstNotNullOfOrNull { UNIT_WORDS[it] } ?: return null
        val to = wordsOf(right).firstNotNullOfOrNull { UNIT_WORDS[it] } ?: return null
        // Cross-category requests ("5 kg in miles") are nonsense, not a default.
        if (from.first != to.first) return null
        return Understood.Units(amount, from.first, from.second, to.second)
    }

    /** "300 dollars in naira", "how much is $50 in eur", "£20" */
    private fun currency(text: String): Understood.Currency? {
        val split = separator(text)
        val left = split?.first ?: text
        val right = split?.second

        val amount = NUMBER.find(left)?.value?.let { normalise(it) } ?: return null
        val from = currencyIn(left) ?: return null
        val to = right?.let { currencyIn(it) }
        // Without a target this is still useful - the screen keeps its own.
        if (split != null && to == null) return null
        return Understood.Currency(amount, from, to)
    }

    private fun currencyIn(text: String): String? {
        text.forEach { character -> SYMBOLS[character]?.let { return it } }
        wordsOf(text).forEach { word ->
            CURRENCY_WORDS[word]?.let { return it }
            val upper = word.uppercase()
            if (word.length == 3 && Currencies.info(upper).region != null) return upper
        }
        return null
    }

    /** Splits on the word that means "convert into". */
    private fun separator(text: String): Pair<String, String>? {
        listOf(" in ", " to ", " into ", " as ", " = ", " -> ").forEach { word ->
            val index = text.indexOf(word)
            if (index > 0) return text.take(index) to text.substring(index + word.length)
        }
        return null
    }

    private fun wordsOf(text: String) =
        text.split(Regex("[^a-z°µåéA-Z]+")).filter { it.isNotBlank() }

    private fun normalise(raw: String) = raw.replace(',', '.').toDoubleOrNull() ?: 0.0
}
