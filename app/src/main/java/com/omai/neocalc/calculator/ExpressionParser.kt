package com.omai.neocalc.calculator

/**
 * Evaluates a whole infix expression in one go - "12*3.5", "(80+20)/4".
 *
 * The keypad engine in CalculatorEngine.kt is press-by-press and immediate, which
 * is right for a calculator but useless for a text field where the user types the
 * expression first and it is judged afterwards. This is that second reading: a
 * small recursive-descent parser, no dependency, no state.
 *
 * Deliberately permissive about which glyphs mean what, because the same text may
 * arrive typed on a soft keyboard (`*`, `/`, `-`) or pasted out of the calculator
 * display (`×`, `÷`, `−`).
 */
object ExpressionParser {

    /**
     * Glyphs that can only be arithmetic. The ASCII hyphen is deliberately
     * absent: a leading one is a sign, not an operator, so it is checked
     * separately in [isExpression].
     */
    private const val OPERATOR_GLYPHS = "+*/^()\u00D7\u00F7\u2212\u2013\u2014%"

    /** True when the text is more than a plain number, so the UI can show a preview. */
    fun isExpression(text: String): Boolean =
        text.any { it in OPERATOR_GLYPHS } ||
            // A minus that isn't the leading sign is a subtraction.
            text.trim().drop(1).contains('-')

    /** The value of [text], or null if it is empty, malformed, or not finite. */
    fun evaluate(text: String): Double? {
        val tokens = tokenize(text) ?: return null
        if (tokens.isEmpty()) return null
        val parser = Cursor(tokens)
        val value = parser.expression() ?: return null
        if (!parser.atEnd) return null
        return value.takeIf { it.isFinite() }
    }

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Symbol(val char: Char) : Token
    }

    /** Normalises the alternative glyphs, then splits into numbers and symbols. */
    private fun tokenize(text: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val s = text.trim()
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == '_' -> i++

                c.isDigit() || c == '.' -> {
                    val start = i
                    // Commas are consumed as *part* of the number, not skipped
                    // between tokens: skipping would turn "1,234" into two
                    // adjacent numbers, which is a parse error rather than 1234.
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
                    val number = s.substring(start, i).replace(",", "").toDoubleOrNull()
                        ?: return null
                    tokens += Token.Number(number)
                }

                else -> {
                    val symbol = when (c) {
                        '+' -> '+'
                        '-', '\u2212', '\u2013', '\u2014' -> '-'
                        '*', '×', 'x', 'X' -> '*'
                        '/', '÷' -> '/'
                        '^' -> '^'
                        '%' -> '%'
                        '(' -> '('
                        ')' -> ')'
                        else -> return null
                    }
                    tokens += Token.Symbol(symbol)
                    i++
                }
            }
        }
        return tokens
    }

    private class Cursor(private val tokens: List<Token>) {
        private var index = 0

        val atEnd: Boolean get() = index >= tokens.size

        private fun peek(): Token? = tokens.getOrNull(index)

        private fun takeSymbol(vararg chars: Char): Char? {
            val token = peek()
            if (token is Token.Symbol && token.char in chars) {
                index++
                return token.char
            }
            return null
        }

        fun expression(): Double? {
            var left = term() ?: return null
            while (true) {
                val op = takeSymbol('+', '-') ?: return left
                val right = term() ?: return null
                left = if (op == '+') left + right else left - right
            }
        }

        private fun term(): Double? {
            var left = unary() ?: return null
            while (true) {
                val op = takeSymbol('*', '/') ?: return left
                val right = unary() ?: return null
                // Division by zero yields infinity, which evaluate() rejects, so
                // "1/0" reports "not a number" rather than a bogus result.
                left = if (op == '*') left * right else left / right
            }
        }

        private fun unary(): Double? {
            val sign = takeSymbol('-', '+')
            val value = power() ?: return null
            return if (sign == '-') -value else value
        }

        /** Right-associative, so 2^3^2 is 512 - the usual reading. */
        private fun power(): Double? {
            val base = primary() ?: return null
            if (takeSymbol('^') == null) return base
            val exponent = unary() ?: return null
            return Math.pow(base, exponent)
        }

        private fun primary(): Double? {
            val value = when (val token = peek()) {
                is Token.Number -> {
                    index++
                    token.value
                }

                is Token.Symbol -> {
                    if (token.char != '(') return null
                    index++
                    val inner = expression() ?: return null
                    if (takeSymbol(')') == null) return null
                    inner
                }

                null -> return null
            }
            // Postfix percent: "20%" is 0.2, so "5%" of nothing still parses.
            return if (takeSymbol('%') != null) value / 100.0 else value
        }
    }
}
