package com.omai.neocalc.calculator

import com.omai.neocalc.ui.motionDuration
import androidx.compose.animation.core.tween
import com.omai.neocalc.convert.formatMoney
import com.omai.neocalc.convert.RateCache
import com.omai.neocalc.convert.ConvertPrefs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omai.neocalc.R
import com.omai.neocalc.ui.theme.FirstTestAppTheme

/** Stateful entry point, used by previews and UI tests. */
@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {
    var state by rememberSaveable(stateSaver = CalculatorStateSaver) {
        mutableStateOf(CalculatorState())
    }
    CalculatorScreen(
        state = state,
        onKey = { state = state.press(it) },
        modifier = modifier,
    )
}

/** Long-press the display while it reads this to open the hidden arcade. */
private const val ARCADE_CODE = "4199"

private val KEY_GAP = 10.dp
private const val KEYPAD_COLUMNS = 4

/** Share of the height the keypad may take, with and without the science panel. */
private const val KEYPAD_HEIGHT_FRACTION = 0.70f
private const val KEYPAD_HEIGHT_FRACTION_EXPANDED = 0.50f

/** Height budget for the science grid; the rest is left to the display. */
private const val SCIENCE_HEIGHT_FRACTION = 0.20f
private val MIN_SCIENCE_KEY_HEIGHT = 32.dp
private val MAX_SCIENCE_KEY_HEIGHT = 52.dp

/** Floor for the computed key size, so a very short window can't produce a
 *  zero or negative dimension. Matches the minimum touch target. */
private val MIN_KEY_SIZE = 48.dp

/** How much wider than tall a key may get before the keypad centres instead. */
private const val MAX_KEY_ASPECT = 1.6f

@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
    onConvert: (String) -> Unit = {},
    onSecretGesture: () -> Unit = {},
) {
    var scientific by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Height is the scarce dimension - a square key sized off width alone
        // overflows on wide, short windows, and shrinking it to stay square
        // leaves gutters down both sides (very visible with the science panel
        // open, which halves the keypad's height budget). So: height is capped
        // by what's available, width fills the screen, and keys simply become
        // rectangular when the two disagree. MAX_KEY_ASPECT stops that turning
        // into absurdly stretched keys on a landscape tablet.
        val rows = KEYPAD.size
        val fraction =
            if (scientific) KEYPAD_HEIGHT_FRACTION_EXPANDED else KEYPAD_HEIGHT_FRACTION
        val widthPerKey = (maxWidth - KEY_GAP * (KEYPAD_COLUMNS - 1)) / KEYPAD_COLUMNS
        val heightPerKey = (maxHeight * fraction - KEY_GAP * (rows - 1)) / rows
        val keyHeight = minOf(widthPerKey, heightPerKey).coerceAtLeast(MIN_KEY_SIZE)
        val keyWidth = minOf(widthPerKey, keyHeight * MAX_KEY_ASPECT)
        val keypadWidth = keyWidth * KEYPAD_COLUMNS + KEY_GAP * (KEYPAD_COLUMNS - 1)

        // The science grid shares the same content column as the keypad, so the
        // two line up, and takes its height from the budget the keypad left over.
        val sciRows = SCIENCE_GRID.size
        val sciKeyWidth =
            (keypadWidth - SCIENCE_GAP * (SCIENCE_COLUMNS - 1)) / SCIENCE_COLUMNS
        val sciKeyHeight =
            ((maxHeight * SCIENCE_HEIGHT_FRACTION - SCIENCE_GAP * (sciRows - 1)) / sciRows)
                .coerceIn(MIN_SCIENCE_KEY_HEIGHT, MAX_SCIENCE_KEY_HEIGHT)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusStrip(
                state = state,
                scientific = scientific,
                onToggleScientific = { scientific = !scientific },
                onKey = onKey,
                onConvert = { onConvert(state.display) },
                modifier = Modifier.width(keypadWidth),
            )
            Display(
                state = state,
                onSecretGesture = onSecretGesture,
                modifier = Modifier
                    .width(keypadWidth)
                    .weight(1f),
            )
            // Enter is 220ms, exit 150ms: an exit that matches its entrance
            // feels sluggish, because the user has already moved on. Both
            // collapse to zero when the system asks for no motion.
            val enterMs = motionDuration(220)
            val exitMs = motionDuration(150)
            AnimatedVisibility(
                visible = scientific,
                enter = fadeIn(tween(enterMs)) + expandVertically(tween(enterMs)),
                exit = fadeOut(tween(exitMs)) + shrinkVertically(tween(exitMs)),
            ) {
                SciencePanel(
                    onKey = onKey,
                    keyWidth = sciKeyWidth,
                    keyHeight = sciKeyHeight,
                    modifier = Modifier.width(keypadWidth),
                )
            }
            Spacer(Modifier.height(KEY_GAP))
            Keypad(onKey = onKey, keyWidth = keyWidth, keyHeight = keyHeight)
        }
    }
}

/** Mode chips: angle unit, memory indicator, and the science-panel toggle. */
@Composable
private fun StatusStrip(
    state: CalculatorState,
    scientific: Boolean,
    onToggleScientific: () -> Unit,
    onKey: (Key) -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(
            label = if (state.angleMode == AngleMode.Degrees) "DEG" else "RAD",
            active = true,
            description = stringResource(
                if (state.angleMode == AngleMode.Degrees) {
                    R.string.angle_mode_degrees
                } else {
                    R.string.angle_mode_radians
                },
            ),
            onClick = { onKey(Key.ToggleAngleMode) },
        )
        if (state.openBrackets > 0) {
            Chip(
                label = "( ${state.openBrackets}",
                active = true,
                description = "${state.openBrackets} open brackets",
                onClick = { onKey(Key.CloseParen) },
            )
        }
        if (state.hasMemory) {
            Chip(
                label = "M",
                active = false,
                description = stringResource(R.string.memory_indicator),
                onClick = { onKey(Key.Mem(MemoryOp.Recall)) },
            )
        }
        Spacer(Modifier.weight(1f))
        // Only offered when there is a real number to hand over; converting an
        // error or a bare zero would just bounce the user to an empty screen.
        val value = state.display.toDoubleOrNull()?.takeIf { it != 0.0 }
        if (state.error == null && value != null) {
            // The chip shows what the number is worth in the user's last target
            // currency, worked out from the cached rates. Tapping it opens the
            // converter; the point is that most of the time it needn't be.
            Chip(
                label = quickConversion(value) ?: "CONV",
                active = false,
                description = stringResource(R.string.convert_this),
                onClick = onConvert,
            )
        }
        Chip(
            label = "f(x)",
            active = scientific,
            description = stringResource(R.string.scientific_toggle),
            onClick = onToggleScientific,
        )
    }
}

@Composable
private fun Chip(
    label: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) scheme.primary.copy(alpha = 0.5f) else scheme.outline,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun Display(
    state: CalculatorState,
    onSecretGesture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Grouped and, when it has to be, in exponent form. The raw entry is what
    // the engine keeps; this is only what the eye gets.
    val text = state.error?.let { stringResource(it.messageRes()) }
        ?: DisplayFormat.forDisplay(state.display)
    // stringResource is @Composable, so it cannot be called inside semantics {}.
    val description = if (state.error != null) {
        text
    } else {
        stringResource(R.string.result_description, text)
    }
    // The way into the arcade: type the magic number, then press and hold the
    // result. Gated on the number so an ordinary long-press - which people do by
    // accident all the time - never derails someone doing arithmetic.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { if (state.display == ARCADE_CODE) onSecretGesture() },
                onClick = {},
            ),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = state.expression,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        val base = if (state.error != null) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.displayLarge
        }
        Text(
            text = text,
            // A long number shrinks to fit rather than being clipped or wrapped,
            // which is what every hardware calculator does with a wide result.
            style = if (state.error != null) {
                base
            } else {
                base.copy(fontSize = base.fontSize * DisplayFormat.fontScaleFor(text))
            },
            maxLines = 1,
            textAlign = TextAlign.End,
            color = if (state.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                .padding(bottom = 12.dp)
                .semantics { contentDescription = description },
        )
    }
}

private const val SCIENCE_COLUMNS = 7
private val SCIENCE_GAP = 6.dp

/**
 * All 21 advanced keys in exactly three rows of seven - no scrolling, nothing
 * hidden. The count is deliberate: 3 × 7 fills the grid with no dead slots, and
 * the keys are sized from the available height (see [CalculatorScreen]) so the
 * panel and the main keypad always fit the screen together.
 */
private val SCIENCE_GRID: List<List<Pair<String, Key>>> = listOf(
    listOf(
        UnaryFunction.Sin.label to Key.Func(UnaryFunction.Sin),
        UnaryFunction.Cos.label to Key.Func(UnaryFunction.Cos),
        UnaryFunction.Tan.label to Key.Func(UnaryFunction.Tan),
        UnaryFunction.Asin.label to Key.Func(UnaryFunction.Asin),
        UnaryFunction.Acos.label to Key.Func(UnaryFunction.Acos),
        UnaryFunction.Atan.label to Key.Func(UnaryFunction.Atan),
        Constant.Pi.label to Key.Const(Constant.Pi),
    ),
    listOf(
        UnaryFunction.Ln.label to Key.Func(UnaryFunction.Ln),
        UnaryFunction.Log10.label to Key.Func(UnaryFunction.Log10),
        UnaryFunction.Exp.label to Key.Func(UnaryFunction.Exp),
        UnaryFunction.TenPow.label to Key.Func(UnaryFunction.TenPow),
        UnaryFunction.Sqrt.label to Key.Func(UnaryFunction.Sqrt),
        UnaryFunction.Square.label to Key.Func(UnaryFunction.Square),
        Constant.E.label to Key.Const(Constant.E),
    ),
    listOf(
        "xʸ" to Key.Op(Operator.Power),
        UnaryFunction.Reciprocal.label to Key.Func(UnaryFunction.Reciprocal),
        UnaryFunction.Factorial.label to Key.Func(UnaryFunction.Factorial),
        "MC" to Key.Mem(MemoryOp.Clear),
        "MR" to Key.Mem(MemoryOp.Recall),
        "M+" to Key.Mem(MemoryOp.Add),
        "M−" to Key.Mem(MemoryOp.Subtract),
    ),
)

@Composable
private fun SciencePanel(
    onKey: (Key) -> Unit,
    keyWidth: Dp,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(SCIENCE_GAP),
    ) {
        SCIENCE_GRID.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SCIENCE_GAP)) {
                row.forEach { (label, key) ->
                    FunctionKey(
                        label = label,
                        key = key,
                        onKey = onKey,
                        modifier = Modifier.size(width = keyWidth, height = keyHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun FunctionKey(
    label: String,
    key: Key,
    onKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        // A key you can feel is a key you can hit without watching your thumb,
        // which is most of what makes a physical calculator pleasant.
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onKey(key)
        },
        modifier = modifier.semantics { contentDescription = label.toSpokenLabel() },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        contentColor = if (key is Key.Mem) scheme.secondary else scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                // Small and unwrapped: seven columns leave roughly 40dp per key,
                // and "tan⁻¹" has to stay on one line at that width.
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                color = Color.Unspecified,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * The current entry converted into the last-used target currency, using rates
 * already on disk. Returns null when there is no cache yet, in which case the
 * chip falls back to a plain label rather than showing a wrong number.
 */
@Composable
private fun quickConversion(value: Double): String? {
    val context = LocalContext.current
    val (from, to) = remember { ConvertPrefs.pair(context) }
    val cached = remember(from) { RateCache.load(context, from) }
    val rate = cached?.table?.rateFor(to) ?: return null
    return "${formatMoney(value * rate)} $to"
}

/** Keypad layout, row by row. */
private val KEYPAD: List<List<Pair<String, Key>>> = listOf(
    // Brackets sit on the main pad, not behind f(x): precedence without a way to
    // override it is only half the feature. The two promoted science keys fill
    // the row and are the two most reached for.
    listOf(
        "(" to Key.OpenParen,
        ")" to Key.CloseParen,
        "xʸ" to Key.Op(Operator.Power),
        UnaryFunction.Sqrt.label to Key.Func(UnaryFunction.Sqrt),
    ),
    listOf("AC" to Key.Clear, "±" to Key.ToggleSign, "%" to Key.Percent, "÷" to Key.Op(Operator.Divide)),
    listOf("7" to Key.Digit(7), "8" to Key.Digit(8), "9" to Key.Digit(9), "×" to Key.Op(Operator.Multiply)),
    listOf("4" to Key.Digit(4), "5" to Key.Digit(5), "6" to Key.Digit(6), "−" to Key.Op(Operator.Subtract)),
    listOf("1" to Key.Digit(1), "2" to Key.Digit(2), "3" to Key.Digit(3), "+" to Key.Op(Operator.Add)),
    listOf("0" to Key.Digit(0), "." to Key.Decimal, "⌫" to Key.Backspace, "=" to Key.Equals),
)

@Composable
private fun Keypad(onKey: (Key) -> Unit, keyWidth: Dp, keyHeight: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        KEYPAD.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { (label, key) ->
                    KeyButton(
                        label = label,
                        key = key,
                        onKey = onKey,
                        modifier = Modifier.size(width = keyWidth, height = keyHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    key: Key,
    onKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Only '=' is filled. Operators carry the accent as ink, modifiers stay muted,
    // digits are plain - so the eye lands on one element per row at most.
    val filled = key is Key.Equals
    val background = when {
        filled -> scheme.primary
        key is Key.Op -> scheme.primary.copy(alpha = 0.08f)
        else -> scheme.surfaceVariant
    }
    val foreground = when {
        filled -> scheme.onPrimary
        key is Key.Op -> scheme.primary
        key is Key.Digit || key is Key.Decimal -> scheme.onSurface
        else -> scheme.onSurfaceVariant
    }
    val haptics = LocalHapticFeedback.current
    Surface(
        // A key you can feel is a key you can hit without watching your thumb,
        // which is most of what makes a physical calculator pleasant.
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onKey(key)
        },
        modifier = modifier.semantics { contentDescription = label.toSpokenLabel() },
        shape = MaterialTheme.shapes.large,
        color = background,
        contentColor = foreground,
        border = if (filled) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 26.sp,
                color = Color.Unspecified,
                // The button already carries a spoken label; without this the glyph
                // is merged in too and TalkBack announces the key twice.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

private fun String.toSpokenLabel(): String = when (this) {
    "±" -> "Toggle sign"
    "%" -> "Percent"
    "÷" -> "Divide"
    "×" -> "Multiply"
    "−" -> "Minus"
    "+" -> "Plus"
    "=" -> "Equals"
    "(" -> "Open bracket"
    ")" -> "Close bracket"
    "⌫" -> "Backspace"
    "." -> "Decimal point"
    "AC" -> "Clear"
    "√" -> "Square root"
    "x²" -> "Squared"
    "xʸ" -> "To the power of"
    "1/x" -> "Reciprocal"
    "n!" -> "Factorial"
    "eˣ" -> "e to the x"
    "10ˣ" -> "Ten to the x"
    "sin⁻¹" -> "Inverse sine"
    "cos⁻¹" -> "Inverse cosine"
    "tan⁻¹" -> "Inverse tangent"
    "π" -> "Pi"
    "e" -> "Euler's number"
    "MC" -> "Memory clear"
    "MR" -> "Memory recall"
    "M+" -> "Memory add"
    "M−" -> "Memory subtract"
    else -> this
}

internal fun CalcError.messageRes(): Int = when (this) {
    CalcError.DivideByZero -> R.string.error_divide_by_zero
    CalcError.InvalidInput -> R.string.error_invalid_input
    CalcError.Overflow -> R.string.error_overflow
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun CalculatorPreview() {
    FirstTestAppTheme {
        CalculatorScreen(
            state = CalculatorState().press(Key.Digit(1)).press(Key.Digit(2))
                .press(Key.Op(Operator.Multiply)).press(Key.Digit(7)),
            onKey = {},
        )
    }
}

/** Guards the layout against the keypad overflowing on wide, short windows. */
@Preview(showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun CalculatorLandscapePreview() {
    FirstTestAppTheme {
        CalculatorScreen(state = CalculatorState(), onKey = {})
    }
}
