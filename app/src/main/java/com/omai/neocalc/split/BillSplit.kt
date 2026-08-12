package com.omai.neocalc.split

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A bill worked out to the penny.
 *
 * The hard part of splitting a bill is not the division - it is that money has a
 * smallest unit. 100.00 split three ways is 33.33 three times, which is 99.99;
 * somebody has to pay the extra penny. [BillSplit] makes that explicit rather
 * than letting a rounding error quietly go missing.
 */
data class BillSplit(
    /** What each person owes, in order. Sums exactly to [total]. */
    val shares: List<BigDecimal>,
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val tip: BigDecimal,
    val total: BigDecimal,
) {
    /** True when the split was not exactly even and somebody pays a fraction more. */
    val uneven: Boolean get() = shares.distinct().size > 1

    val perPerson: BigDecimal get() = shares.firstOrNull() ?: BigDecimal.ZERO
}

object Bill {

    /** Money is worked to two decimals everywhere; the UI never sees more. */
    private const val SCALE = 2

    /**
     * @param amount the bill before tax and tip
     * @param taxPercent applied to [amount]
     * @param tipPercent applied to [amount], or to amount+tax when [tipOnTax]
     * @param people how many ways to split, at least one
     * @param roundUp round each share up to the next whole unit, the "just make
     *   it easy" mode people actually use when settling in cash
     */
    fun split(
        amount: Double,
        taxPercent: Double = 0.0,
        tipPercent: Double = 0.0,
        people: Int = 1,
        tipOnTax: Boolean = false,
        roundUp: Boolean = false,
    ): BillSplit {
        val heads = people.coerceAtLeast(1)
        val subtotal = money(amount)
        val tax = percentOf(subtotal, taxPercent)
        val tipBase = if (tipOnTax) subtotal + tax else subtotal
        val tip = percentOf(tipBase, tipPercent)
        val total = subtotal + tax + tip

        if (roundUp) {
            // Everyone pays the same rounded-up amount; the surplus is a bigger
            // tip, which is exactly what happens at a table in real life.
            val each = total
                .divide(BigDecimal(heads), 0, RoundingMode.CEILING)
                .setScale(SCALE)
            val rounded = each.multiply(BigDecimal(heads))
            return BillSplit(
                shares = List(heads) { each },
                subtotal = subtotal,
                tax = tax,
                tip = rounded - subtotal - tax,
                total = rounded,
            )
        }

        // Floor every share, then hand the leftover pennies out one at a time.
        // This is the only distribution that both sums exactly and never differs
        // by more than a penny between people.
        val base = total.divide(BigDecimal(heads), SCALE, RoundingMode.DOWN)
        val distributed = base.multiply(BigDecimal(heads))
        val pennies = (total - distributed)
            .movePointRight(SCALE)
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
        val penny = BigDecimal.ONE.movePointLeft(SCALE)

        return BillSplit(
            shares = List(heads) { index -> if (index < pennies) base + penny else base },
            subtotal = subtotal,
            tax = tax,
            tip = tip,
            total = total,
        )
    }

    /**
     * An itemised split: each entry is what one person ordered. Tax and tip are
     * shared in proportion to what each person's items came to, which is the
     * fair reading of "we'll split it by what we had".
     */
    fun splitByItems(
        items: List<Double>,
        taxPercent: Double = 0.0,
        tipPercent: Double = 0.0,
        tipOnTax: Boolean = false,
    ): BillSplit {
        if (items.isEmpty()) return split(0.0)
        val subtotal = money(items.sum())
        val tax = percentOf(subtotal, taxPercent)
        val tipBase = if (tipOnTax) subtotal + tax else subtotal
        val tip = percentOf(tipBase, tipPercent)
        val total = subtotal + tax + tip

        if (subtotal.signum() == 0) return split(0.0, people = items.size)

        val shares = items.map { item ->
            money(item)
                .multiply(total)
                .divide(subtotal, SCALE, RoundingMode.DOWN)
        }
        // Rounding down every share leaves a remainder; it goes to the largest
        // order, which is the least surprising place to put it.
        val remainder = total - shares.fold(BigDecimal.ZERO) { acc, value -> acc + value }
        val biggest = items.indices.maxByOrNull { items[it] } ?: 0

        return BillSplit(
            shares = shares.mapIndexed { index, value ->
                if (index == biggest) value + remainder else value
            },
            subtotal = subtotal,
            tax = tax,
            tip = tip,
            total = total,
        )
    }

    private fun money(value: Double): BigDecimal =
        BigDecimal(value.takeIf { it.isFinite() } ?: 0.0).setScale(SCALE, RoundingMode.HALF_UP)

    private fun percentOf(value: BigDecimal, percent: Double): BigDecimal =
        value.multiply(BigDecimal(percent / 100.0)).setScale(SCALE, RoundingMode.HALF_UP)
}
