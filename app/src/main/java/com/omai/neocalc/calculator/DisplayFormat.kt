package com.omai.neocalc.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * How a number is shown, as opposed to how it is stored.
 *
 * [CalculatorState.display] stays a plain machine-readable numeral, because the
 * engine parses it back on the next key press. Grouping separators and exponents
 * are a presentation concern, so they live here and are applied only on the way
 * to the screen. Keeping the two apart is what stops "1,234" from being read
 * back as an invalid number.
 */
object DisplayFormat {

    /** Digits a plain (non-exponent) rendering may occupy before it turns. */
    const val MAX_PLAIN_DIGITS = 12

    /** Significant digits kept when a value has to be shown in exponent form. */
    private const val SCIENTIFIC_DIGITS = 8

    /**
     * Beyond this the number is too large to write out in full on a phone, and
     * every calculator ever made switches to exponent notation here.
     */
    private val UPPER = BigDecimal("1E15")

    /** Below this a plain rendering would be all zeroes and no information. */
    private val LOWER = BigDecimal("1E-7")

    /**
     * Renders [raw] the way a calculator would: grouped thousands, no trailing
     * zeroes, and an exponent when the number would otherwise be unreadable.
     *
     * Anything that is not a number - an error message, a partially typed entry
     * like "0." - is returned untouched, because the user is mid-thought and
     * reformatting under them is worse than leaving it alone.
     */
    fun forDisplay(raw: String, locale: Locale = Locale.getDefault()): String {
        if (raw.isEmpty()) return raw
        // A trailing separator or a lone sign means the entry is still being
        // typed; formatting it now would delete the character just pressed.
        if (raw.endsWith(".") || raw == "-" || raw.isBlank()) return raw

        val value = raw.toBigDecimalOrNull() ?: return raw
        val magnitude = value.abs()

        if (magnitude.signum() != 0 && (magnitude >= UPPER || magnitude < LOWER)) {
            return scientific(value, locale)
        }

        val symbols = DecimalFormatSymbols.getInstance(locale)
        val stripped = value.stripTrailingZeros()
        val decimals = stripped.scale().coerceAtLeast(0)
        val pattern = if (decimals > 0) {
            "#,##0." + "#".repeat(decimals.coerceAtMost(MAX_PLAIN_DIGITS))
        } else {
            "#,##0"
        }
        return DecimalFormat(pattern, symbols).format(stripped)
    }

    /** "1.2345678e+21" - the form a calculator shows, not Java's default. */
    private fun scientific(value: BigDecimal, locale: Locale): String {
        val rounded = value.round(MathContext(SCIENTIFIC_DIGITS, RoundingMode.HALF_UP))
        val text = String.format(locale, "%.${SCIENTIFIC_DIGITS - 1}e", rounded)
        val (mantissa, exponent) = text.split("e", "E").let { it[0] to it[1] }
        val trimmed = mantissa.trimEnd('0').trimEnd('.', ',')
        val sign = if (exponent.startsWith("-")) "-" else "+"
        val digits = exponent.trimStart('+', '-').trimStart('0').ifEmpty { "0" }
        return "${trimmed}e$sign$digits"
    }

    /**
     * A font size that keeps [text] on one line.
     *
     * Rather than measuring, this steps down through a few sizes by length: the
     * display is a known width and the digits are a known font, so the result is
     * the same and it costs nothing per frame.
     */
    fun fontScaleFor(text: String): Float = when (text.length) {
        in 0..8 -> 1f
        9 -> 0.92f
        10 -> 0.84f
        11 -> 0.77f
        12 -> 0.71f
        13 -> 0.66f
        14 -> 0.61f
        15 -> 0.57f
        else -> 0.52f
    }
}
