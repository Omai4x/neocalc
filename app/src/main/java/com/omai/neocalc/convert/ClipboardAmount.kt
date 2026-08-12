package com.omai.neocalc.convert

/**
 * An amount spotted on the clipboard: the number, plus the currency it was
 * written in when the text said so.
 */
data class DetectedAmount(
    val amount: Double,
    val code: String?,
    /** The original fragment, so the suggestion chip can echo what was seen. */
    val text: String,
)

/**
 * Recognises prices copied from somewhere else - "$49.99", "1 234,50 €",
 * "NGN 25,000". Pure text work, so the whole thing is unit-testable without a
 * clipboard, an Activity, or a device.
 */
object ClipboardAmount {

    /** Symbols worth recognising: the ones people actually copy prices in. */
    private val SYMBOLS = mapOf(
        '$' to "USD",
        '€' to "EUR",
        '£' to "GBP",
        '¥' to "JPY",
        '₦' to "NGN",
        '₹' to "INR",
        '₩' to "KRW",
        '₽' to "RUB",
        '₺' to "TRY",
        '₴' to "UAH",
        '฿' to "THB",
        'R' to "ZAR", // only as a prefix immediately before digits, e.g. R199
    )

    /** Clipboards hold whole articles; only the first line or so can be a price. */
    private const val MAX_LENGTH = 120

    // The grouped alternative requires at least one group ("+", not "*"), or it
    // would match just the first three digits of a plain "1500" and then treat
    // the rest of the text as trailing noise.
    private val NUMBER = Regex("""\d{1,3}(?:[ ,.]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?""")
    private val CODE = Regex("""\b([A-Z]{3})\b""")

    /**
     * Returns the amount [text] appears to hold, or null when it holds none -
     * which is the common case, so the caller can treat null as "stay quiet".
     */
    fun parse(text: String?): DetectedAmount? {
        val trimmed = text?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
            ?: return null

        val match = NUMBER.find(trimmed) ?: return null
        val amount = normalise(match.value) ?: return null
        if (amount == 0.0) return null

        // A bare number is only interesting if that is *all* the clipboard holds;
        // otherwise every copied sentence containing a year would trigger a chip.
        val code = currencyIn(trimmed, match.range)
        if (code == null && trimmed != match.value) return null

        return DetectedAmount(amount = amount, code = code, text = trimmed)
    }

    /**
     * Reads "1,234.56" and "1.234,56" alike. The last separator with 1-2 trailing
     * digits is the decimal point; anything else is a thousands separator.
     */
    internal fun normalise(raw: String): Double? {
        val cleaned = raw.replace(" ", "")
        val lastSeparator = cleaned.lastIndexOfAny(charArrayOf('.', ','))
        if (lastSeparator == -1) return cleaned.toDoubleOrNull()
        val decimals = cleaned.length - lastSeparator - 1
        return if (decimals in 1..2 && cleaned.count { it == '.' || it == ',' } >= 1) {
            val whole = cleaned.substring(0, lastSeparator).filter { it.isDigit() }
            val fraction = cleaned.substring(lastSeparator + 1)
            "$whole.$fraction".toDoubleOrNull()
        } else {
            cleaned.filter { it.isDigit() }.toDoubleOrNull()
        }
    }

    /** An ISO code anywhere in the text, else a symbol sitting against the number. */
    private fun currencyIn(text: String, number: IntRange): String? {
        CODE.find(text)?.groupValues?.get(1)?.let { candidate ->
            if (Currencies.info(candidate).region != null || candidate.startsWith("X")) {
                return candidate
            }
        }
        val before = text.take(number.first).trimEnd()
        val after = text.substring(minOf(number.last + 1, text.length)).trimStart()
        // 'R' is a letter, so it only counts glued to the digits (R199, not "R 199").
        before.lastOrNull()?.let { symbol ->
            SYMBOLS[symbol]?.let { code ->
                if (symbol != 'R' || before.length == text.take(number.first).length) return code
            }
        }
        after.firstOrNull()?.let { symbol ->
            if (symbol != 'R') SYMBOLS[symbol]?.let { return it }
        }
        return null
    }
}
