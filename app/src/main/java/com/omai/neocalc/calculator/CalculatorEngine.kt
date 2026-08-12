package com.omai.neocalc.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A key the user can press on the keypad. */
sealed interface Key {
    data class Digit(val value: Int) : Key
    data class Op(val operator: Operator) : Key
    data class Func(val function: UnaryFunction) : Key
    data class Const(val constant: Constant) : Key
    data class Mem(val op: MemoryOp) : Key
    data object Decimal : Key
    data object Equals : Key
    data object Clear : Key
    data object Backspace : Key
    data object ToggleSign : Key
    data object Percent : Key
    data object ToggleAngleMode : Key
    data object OpenParen : Key
    data object CloseParen : Key
}

enum class Operator(val symbol: String, val precedence: Int) {
    Add("+", 1),
    Subtract("−", 1),
    Multiply("×", 2),
    Divide("÷", 2),
    // Right-associative, which is why it never folds against itself below.
    Power("^", 3),
    ;

    /**
     * Whether a pending [this] should be resolved before [next] is accepted.
     * Equal precedence folds for the left-associative operators and does not
     * for Power, which is what makes 2^3^2 read as 2^(3^2).
     */
    fun foldsBefore(next: Operator): Boolean =
        if (this == Power && next == Power) false else precedence >= next.precedence
}

/** Functions that apply immediately to whatever is on the display. */
enum class UnaryFunction(val label: String) {
    Sin("sin"), Cos("cos"), Tan("tan"),
    Asin("sin⁻¹"), Acos("cos⁻¹"), Atan("tan⁻¹"),
    Ln("ln"), Log10("log"), Exp("eˣ"), TenPow("10ˣ"),
    Sqrt("√"), Square("x²"), Reciprocal("1/x"), Factorial("n!"),
}

enum class Constant(val label: String, val value: Double) {
    Pi("π", Math.PI), E("e", Math.E)
}

enum class MemoryOp { Add, Subtract, Recall, Clear }

enum class AngleMode { Degrees, Radians }

/** Why a calculation failed. The wording lives in the UI layer. */
enum class CalcError { DivideByZero, InvalidInput, Overflow }

/**
 * Calculator state. [display] is always the text to show; everything else tracks
 * what a following key press should do.
 *
 * [stack] holds the operands and operators still waiting to be resolved, and
 * [entering] tells us whether [display] is a number the user is still typing (so
 * digits append) or a computed result (so digits start a fresh entry).
 */
/** One operand waiting on the left of an unresolved operator. */
data class Pending(val value: BigDecimal, val operator: Operator)

data class CalculatorState(
    val display: String = "0",
    /**
     * Operands and operators still waiting, innermost last.
     *
     * A single accumulator can only ever fold left to right, which is how this
     * calculator used to answer 20 for 2 + 3 × 4. A stack lets a lower-precedence
     * operator wait while a higher-precedence one resolves, so the keypad now
     * agrees with the expression parser used by the text fields.
     */
    val stack: List<Pending> = emptyList(),
    /**
     * How many operands were on the stack when each open bracket was typed, so a
     * closing bracket knows exactly how far to fold back.
     */
    val brackets: List<Int> = emptyList(),
    val entering: Boolean = false,
    /**
     * True when [display] holds a value produced by a function, constant, percent
     * or memory recall. Such a value counts as the operand for a following
     * operator (so [entering] is also true), but a digit should *replace* it
     * rather than append to it - "sin 30" then "3" is 3, not 0.53.
     */
    val computed: Boolean = false,
    /** Operand + operator of the last '=' so repeated '=' repeats the operation. */
    val lastOperation: Pair<Operator, BigDecimal>? = null,
    val error: CalcError? = null,
    /** Survives AC and error recovery; only MC clears it. */
    val memory: BigDecimal = BigDecimal.ZERO,
    val angleMode: AngleMode = AngleMode.Degrees,
) {
    /** The expression shown above the display, e.g. "12 × ( 3 +". */
    val expression: String
        get() = buildString {
            stack.forEachIndexed { index, pending ->
                // An open bracket sits immediately before the operand that
                // followed it, which is where the user typed it.
                if (index in brackets) append("( ")
                append(format(pending.value))
                append(' ')
                append(pending.operator.symbol)
                append(' ')
            }
            if (stack.size in brackets) append("( ")
        }.trim()

    val hasMemory: Boolean get() = memory.signum() != 0

    /** Unclosed brackets, so the UI can show a depth indicator. */
    val openBrackets: Int get() = brackets.size
}

/** How many digits the user may type into a single entry. */
private const val MAX_DIGITS = 12

/**
 * How many digits a *computed* result may show. Results are allowed to be wider
 * than what the user can type, so that e.g. 999999999999 × 9 stays exact instead
 * of being rounded back to the entry width; past this we round rather than
 * render an unreadable number.
 */
private const val MAX_RESULT_DIGITS = 16

/**
 * 18! is 6402373705728000 - 16 digits, the widest result [format] will show
 * without rounding. Allowing 19! would silently display a rounded value, so
 * anything past this is reported as an overflow instead.
 */
private const val MAX_FACTORIAL = 18

private val MATH_CONTEXT = MathContext(MAX_RESULT_DIGITS, RoundingMode.HALF_UP)

/** Transcendental results are rounded to this, which also hides float noise. */
private val TRANSCENDENTAL_CONTEXT = MathContext(MAX_DIGITS, RoundingMode.HALF_UP)

private val HUNDRED = BigDecimal(100)
private val ONE_EIGHTY = BigDecimal(180)
private val NINETY = BigDecimal(90)

fun CalculatorState.press(key: Key): CalculatorState {
    // Any key but AC dismisses an error and starts over, keeping memory and mode.
    val state = if (error != null && key !is Key.Clear) clearedEntry() else this
    return when (key) {
        is Key.Digit -> state.inputDigit(key.value)
        is Key.Op -> state.applyOperator(key.operator)
        is Key.Func -> state.applyFunction(key.function)
        is Key.Const -> state.inputConstant(key.constant)
        is Key.Mem -> state.applyMemory(key.op)
        Key.Decimal -> state.inputDecimal()
        Key.Equals -> state.equals()
        Key.Clear -> state.clearedEntry()
        Key.Backspace -> state.backspace()
        Key.ToggleSign -> state.toggleSign()
        Key.Percent -> state.percent()
        Key.OpenParen -> state.openBracket()
        Key.CloseParen -> state.closeBracket()
        Key.ToggleAngleMode -> state.copy(
            angleMode = if (state.angleMode == AngleMode.Degrees) {
                AngleMode.Radians
            } else {
                AngleMode.Degrees
            },
        )
    }
}

/** Clears the working entry but deliberately keeps memory and angle mode. */
private fun CalculatorState.clearedEntry() =
    CalculatorState(memory = memory, angleMode = angleMode)

/**
 * Loads a value into the display as a fresh entry - used when reusing a result
 * from the history tape.
 */
fun CalculatorState.enterValue(value: String): CalculatorState = copy(
    display = value,
    entering = true,
    computed = true,
    error = null,
    lastOperation = null,
)

/**
 * Describes what pressing '=' would evaluate, e.g. "12 × 7", or null when '='
 * would do nothing. Used to label history entries.
 */
fun CalculatorState.pendingEvaluation(): String? = when {
    error != null -> null
    stack.isNotEmpty() -> (expression + " " + display).trim()
    lastOperation != null -> "$display ${lastOperation.first.symbol} ${format(lastOperation.second)}"
    else -> null
}

private fun CalculatorState.inputDigit(digit: Int): CalculatorState {
    if (!entering || computed) {
        return copy(
            display = digit.toString(),
            entering = true,
            computed = false,
            lastOperation = null,
        )
    }
    if (display == "0") return copy(display = digit.toString())
    if (display.count { it.isDigit() } >= MAX_DIGITS) return this
    return copy(display = display + digit)
}

private fun CalculatorState.inputDecimal(): CalculatorState = when {
    !entering || computed ->
        copy(display = "0.", entering = true, computed = false, lastOperation = null)

    display.contains('.') -> this
    else -> copy(display = "$display.")
}

private fun CalculatorState.inputConstant(constant: Constant): CalculatorState = copy(
    display = format(fromDouble(constant.value) ?: BigDecimal.ZERO),
    entering = true,
    computed = true,
    lastOperation = null,
)

private fun CalculatorState.backspace(): CalculatorState {
    if (!entering) return this
    val trimmed = display.dropLast(1)
    return when {
        trimmed.isEmpty() || trimmed == "-" ->
            copy(display = "0", entering = false, computed = false)

        else -> copy(display = trimmed, computed = false)
    }
}

/**
 * Negates the current entry. [entering] is set so the sign survives: without it a
 * '±' pressed straight after an operator would be silently dropped by the next
 * digit, which replaces a display the user isn't considered to be typing into.
 */
private fun CalculatorState.toggleSign(): CalculatorState = when {
    display == "0" -> this
    display.startsWith("-") -> copy(display = display.removePrefix("-"), entering = true)
    else -> copy(display = "-$display", entering = true)
}

/**
 * Percent reads the current entry as a fraction. With a pending +/− it is a
 * percentage *of the accumulator* (100 + 10% = 110), otherwise a plain /100.
 */
private fun CalculatorState.percent(): CalculatorState {
    val current = currentValue()
    val pending = stack.lastOrNull()
    val result = when (pending?.operator) {
        Operator.Add, Operator.Subtract ->
            pending.value.multiply(current, MATH_CONTEXT).divide(HUNDRED, MATH_CONTEXT)

        else -> current.divide(HUNDRED, MATH_CONTEXT)
    }
    // Like typing a digit, this starts a fresh entry, so a following '=' must not
    // replay the operation that produced the value we just turned into a percent.
    return copy(display = format(result), entering = true, computed = true, lastOperation = null)
}

private fun CalculatorState.applyMemory(op: MemoryOp): CalculatorState = when (op) {
    // M+ / M− leave the display alone but end the entry, so the next digit starts fresh.
    MemoryOp.Add -> copy(memory = memory.add(currentValue(), MATH_CONTEXT), entering = false)
    MemoryOp.Subtract ->
        copy(memory = memory.subtract(currentValue(), MATH_CONTEXT), entering = false)

    MemoryOp.Recall ->
        copy(display = format(memory), entering = true, computed = true, lastOperation = null)
    MemoryOp.Clear -> copy(memory = BigDecimal.ZERO)
}

/**
 * Folds everything on the stack that binds at least as tightly as [operator],
 * down to the current bracket, then pushes the new operator.
 *
 * This is the whole of precedence: "3 ×" waits when "+" arrives, because × binds
 * tighter and must be resolved first; "3 +" folds when "+" arrives, because
 * addition is left-associative.
 */
private fun CalculatorState.applyOperator(operator: Operator): CalculatorState {
    // Pressing another operator without typing an operand just swaps the operator.
    if (stack.isNotEmpty() && !entering) {
        return copy(
            stack = stack.dropLast(1) + stack.last().copy(operator = operator),
            lastOperation = null,
        )
    }

    val floor = brackets.lastOrNull() ?: 0
    var value = currentValue()
    var pending = stack
    while (pending.size > floor && pending.last().operator.foldsBefore(operator)) {
        val top = pending.last()
        value = compute(top.value, value, top.operator)
            ?: return errorState(failureFor(top.operator, value))
        pending = pending.dropLast(1)
    }

    return copy(
        display = format(value),
        stack = pending + Pending(value, operator),
        entering = false,
        computed = false,
        lastOperation = null,
    )
}

/**
 * Opens a bracket. The current entry is discarded rather than kept, because
 * "5 (" means nothing: a bracket always starts a fresh sub-expression.
 */
private fun CalculatorState.openBracket(): CalculatorState = copy(
    display = "0",
    brackets = brackets + stack.size,
    entering = false,
    computed = false,
    lastOperation = null,
)

/** Folds back to the matching open bracket. A stray ')' is simply ignored. */
private fun CalculatorState.closeBracket(): CalculatorState {
    val floor = brackets.lastOrNull() ?: return this
    var value = currentValue()
    var pending = stack
    while (pending.size > floor) {
        val top = pending.last()
        value = compute(top.value, value, top.operator)
            ?: return errorState(failureFor(top.operator, value))
        pending = pending.dropLast(1)
    }
    return copy(
        display = format(value),
        stack = pending,
        brackets = brackets.dropLast(1),
        entering = true,
        computed = true,
        lastOperation = null,
    )
}

private fun CalculatorState.equals(): CalculatorState {
    // Nothing pending: replay the previous operation, so '=' repeats.
    if (stack.isEmpty()) {
        val (operator, right) = lastOperation ?: return copy(entering = false)
        val result = compute(currentValue(), right, operator)
            ?: return errorState(failureFor(operator, right))
        return copy(display = format(result), entering = false, computed = false)
    }

    // Unclosed brackets are closed implicitly, which is what every calculator
    // does and saves the user from hunting for the right number of ')'.
    var value = currentValue()
    var pending = stack
    val last = pending.last()
    while (pending.isNotEmpty()) {
        val top = pending.last()
        value = compute(top.value, value, top.operator)
            ?: return errorState(failureFor(top.operator, value))
        pending = pending.dropLast(1)
    }
    return copy(
        display = format(value),
        stack = emptyList(),
        brackets = emptyList(),
        entering = false,
        computed = false,
        // Repeating '=' replays only the outermost operation, as it always has.
        lastOperation = last.operator to currentValue(),
    )
}

/** Applies a function to the display straight away, as a scientific calculator does. */
private fun CalculatorState.applyFunction(fn: UnaryFunction): CalculatorState {
    val x = currentValue()
    val result: BigDecimal = when (fn) {
        UnaryFunction.Sin -> fromDouble(sin(toRadians(x))) ?: return invalid()
        UnaryFunction.Cos -> fromDouble(cos(toRadians(x))) ?: return invalid()

        UnaryFunction.Tan -> {
            // tan is undefined at 90° + 180k; in degrees we can say so exactly.
            if (angleMode == AngleMode.Degrees &&
                x.remainder(ONE_EIGHTY).abs().compareTo(NINETY) == 0
            ) {
                return invalid()
            }
            fromDouble(tan(toRadians(x))) ?: return invalid()
        }

        UnaryFunction.Asin -> {
            if (x.abs() > BigDecimal.ONE) return invalid()
            fromDouble(fromRadians(asin(x.toDouble()))) ?: return invalid()
        }

        UnaryFunction.Acos -> {
            if (x.abs() > BigDecimal.ONE) return invalid()
            fromDouble(fromRadians(acos(x.toDouble()))) ?: return invalid()
        }

        UnaryFunction.Atan -> fromDouble(fromRadians(atan(x.toDouble()))) ?: return invalid()

        UnaryFunction.Ln -> {
            if (x.signum() <= 0) return invalid()
            fromDouble(ln(x.toDouble())) ?: return invalid()
        }

        UnaryFunction.Log10 -> {
            if (x.signum() <= 0) return invalid()
            fromDouble(log10(x.toDouble())) ?: return invalid()
        }

        UnaryFunction.Exp -> fromDouble(exp(x.toDouble())) ?: return overflow()
        UnaryFunction.TenPow -> fromDouble(Math.pow(10.0, x.toDouble())) ?: return overflow()

        UnaryFunction.Sqrt -> {
            if (x.signum() < 0) return invalid()
            fromDouble(sqrt(x.toDouble())) ?: return invalid()
        }

        UnaryFunction.Square -> x.multiply(x, MATH_CONTEXT)

        UnaryFunction.Reciprocal -> {
            if (x.signum() == 0) return errorState(CalcError.DivideByZero)
            BigDecimal.ONE.divide(x, MATH_CONTEXT)
        }

        UnaryFunction.Factorial -> factorial(x) ?: return factorialFailure(x)
    }
    return copy(display = format(result), entering = true, computed = true, lastOperation = null)
}

private fun CalculatorState.toRadians(value: BigDecimal): Double = when (angleMode) {
    AngleMode.Degrees -> Math.toRadians(value.toDouble())
    AngleMode.Radians -> value.toDouble()
}

private fun CalculatorState.fromRadians(value: Double): Double = when (angleMode) {
    AngleMode.Degrees -> Math.toDegrees(value)
    AngleMode.Radians -> value
}

/** Exact for 0..[MAX_FACTORIAL]; null for anything else (negative, fractional, too big). */
private fun factorial(x: BigDecimal): BigDecimal? {
    val stripped = x.stripTrailingZeros()
    if (stripped.scale() > 0) return null
    val n = try {
        stripped.intValueExact()
    } catch (_: ArithmeticException) {
        return null
    }
    if (n < 0 || n > MAX_FACTORIAL) return null
    var result = BigDecimal.ONE
    for (i in 2..n) result = result.multiply(BigDecimal(i))
    return result
}

/** Separates "too big" from "not a whole number" so the message can be specific. */
private fun CalculatorState.factorialFailure(x: BigDecimal): CalculatorState {
    val stripped = x.stripTrailingZeros()
    val whole = stripped.scale() <= 0
    return errorState(if (whole && stripped.signum() > 0) CalcError.Overflow else CalcError.InvalidInput)
}

private fun compute(left: BigDecimal, right: BigDecimal, operator: Operator): BigDecimal? =
    when (operator) {
        Operator.Add -> left.add(right, MATH_CONTEXT)
        Operator.Subtract -> left.subtract(right, MATH_CONTEXT)
        Operator.Multiply -> left.multiply(right, MATH_CONTEXT)
        Operator.Divide -> if (right.signum() == 0) null else left.divide(right, MATH_CONTEXT)
        Operator.Power -> power(left, right)
    }

/**
 * Integer exponents stay exact via [BigDecimal.pow]; fractional ones fall back to
 * doubles, which is also where a negative base becomes undefined.
 */
private fun power(left: BigDecimal, right: BigDecimal): BigDecimal? {
    val exponent = right.stripTrailingZeros()
    if (exponent.scale() <= 0) {
        val n = try {
            exponent.intValueExact()
        } catch (_: ArithmeticException) {
            return null
        }
        if (n !in -999..999) return null
        if (left.signum() == 0 && n < 0) return null
        return left.pow(n, MATH_CONTEXT)
    }
    return fromDouble(Math.pow(left.toDouble(), right.toDouble()))
}

/** Divide is the only binary operator whose failure isn't "invalid input". */
private fun failureFor(operator: Operator, right: BigDecimal): CalcError = when {
    operator == Operator.Divide && right.signum() == 0 -> CalcError.DivideByZero
    else -> CalcError.InvalidInput
}

/**
 * The messages themselves live in the UI layer (as string resources) so this
 * engine stays free of presentation and localisation concerns.
 */
private fun CalculatorState.errorState(reason: CalcError) =
    CalculatorState(error = reason, memory = memory, angleMode = angleMode)

private fun CalculatorState.invalid() = errorState(CalcError.InvalidInput)

private fun CalculatorState.overflow() = errorState(CalcError.Overflow)

private fun CalculatorState.currentValue(): BigDecimal =
    display.toBigDecimalOrNull() ?: BigDecimal.ZERO

/** Rounds a double result and rejects the values BigDecimal cannot represent. */
private fun fromDouble(value: Double): BigDecimal? {
    if (value.isNaN() || value.isInfinite()) return null
    if (value == 0.0) return BigDecimal.ZERO
    return BigDecimal(value).round(TRANSCENDENTAL_CONTEXT).stripTrailingZeros()
}

/** Renders a result without a trailing ".000…" and without scientific notation. */
internal fun format(value: BigDecimal): String {
    if (value.signum() == 0) return "0"
    val stripped = value.stripTrailingZeros()
    val plain = stripped.toPlainString()
    if (plain.count { it.isDigit() } <= MAX_RESULT_DIGITS) return plain
    return stripped.round(MathContext(MAX_DIGITS, RoundingMode.HALF_UP))
        .stripTrailingZeros()
        .toPlainString()
}
