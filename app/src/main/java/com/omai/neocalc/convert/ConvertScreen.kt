package com.omai.neocalc.convert

import com.omai.neocalc.ui.LocalWindowSize
import com.omai.neocalc.alerts.AlertsPanel
import com.omai.neocalc.smart.Understood
import com.omai.neocalc.smart.SmartBar
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.Icons
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omai.neocalc.R
import com.omai.neocalc.calculator.ExpressionParser
import com.omai.neocalc.ui.theme.FirstTestAppTheme
import com.omai.neocalc.widget.RateWidget
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private enum class ConvertMode { Currency, Units }

/**
 * [pendingAmount] is a value handed over from the calculator or the history tape;
 * it is consumed once and then cleared, so returning to this tab later doesn't
 * silently overwrite whatever the user has since typed.
 */
@Composable
fun ConvertScreen(
    modifier: Modifier = Modifier,
    pendingAmount: String? = null,
    onAmountConsumed: () -> Unit = {},
) {
    var mode by rememberSaveable { mutableStateOf(ConvertMode.Currency) }

    // A handed-over value is always a currency amount, so make sure the tab that
    // can show it is the one on screen.
    LaunchedEffect(pendingAmount) {
        if (pendingAmount != null) mode = ConvertMode.Currency
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LocalWindowSize.current.padding()),
        verticalArrangement = Arrangement.spacedBy(LocalWindowSize.current.spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SegmentedTabs(
            options = ConvertMode.entries,
            selected = mode,
            label = {
                stringResource(
                    if (it == ConvertMode.Currency) {
                        R.string.convert_currency
                    } else {
                        R.string.convert_units
                    },
                )
            },
            onSelect = { mode = it },
        )
        when (mode) {
            ConvertMode.Currency -> CurrencyPanel(
                pendingAmount = pendingAmount,
                onAmountConsumed = onAmountConsumed,
            )

            ConvertMode.Units -> UnitsPanel()
        }
    }
}

@Composable
private fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.primary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CurrencyPanel(
    pendingAmount: String?,
    onAmountConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var amount by rememberSaveable { mutableStateOf("100") }
    var from by rememberSaveable { mutableStateOf(ConvertPrefs.pair(context).first) }
    var to by rememberSaveable { mutableStateOf(ConvertPrefs.pair(context).second) }

    var table by remember { mutableStateOf<RateTable?>(null) }
    /** Non-null while the numbers on screen came from disk rather than the network. */
    var offlineSince by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var trend by remember { mutableStateOf<RateTrend?>(null) }

    var favourites by remember { mutableStateOf(ConvertPrefs.favourites(context)) }
    var recents by remember { mutableStateOf(ConvertPrefs.recents(context)) }
    var clipboardHit by remember { mutableStateOf<DetectedAmount?>(null) }

    suspend fun load() {
        loading = true
        failed = false
        CurrencyApi.fetchRates(from)
            .onSuccess { fresh ->
                table = fresh
                offlineSince = null
                RateCache.save(context, fresh)
                // The widget renders from the same cache, so a refresh here is
                // also the moment its numbers stop being stale.
                RateWidget.refresh(context)
            }
            .onFailure {
                // Falling back to disk is the whole point of the cache: a failed
                // fetch should cost freshness, not the entire screen.
                val cached = RateCache.load(context, from)
                if (cached != null) {
                    table = cached.table
                    offlineSince = cached.fetchedAt
                } else {
                    failed = true
                }
            }
        loading = false
    }

    // Show the cache immediately, then go to the network: a cold start on a slow
    // connection has numbers on screen in the first frame instead of a dash.
    LaunchedEffect(from) {
        if (table?.base != from) {
            RateCache.load(context, from)?.let { cached ->
                table = cached.table
                offlineSince = cached.fetchedAt
            }
        }
        load()
    }

    // History is a separate, narrower endpoint and is allowed to fail silently -
    // exotic pairs simply have no chart.
    LaunchedEffect(from, to) {
        trend = null
        trend = CurrencyApi.fetchSeries(from, to).getOrNull()?.let(RateTrend::of)
    }

    LaunchedEffect(from, to) {
        ConvertPrefs.savePair(context, from, to)
        RateWidget.refresh(context)
    }

    // Entering the tab is the only moment a clipboard read is expected; polling
    // it would trip the system's "pasted from clipboard" warning repeatedly.
    LaunchedEffect(Unit) {
        clipboardHit = ClipboardAmount.parse(readClipboard(context))
    }

    LaunchedEffect(pendingAmount) {
        if (pendingAmount != null) {
            amount = pendingAmount
            onAmountConsumed()
        }
    }

    fun choose(code: String, isBase: Boolean) {
        if (isBase) from = code else to = code
        recents = ConvertPrefs.recordRecent(context, code)
    }

    val codes = table?.currencies ?: CurrencyApi.FALLBACK_CURRENCIES
    val quotes = table?.takeIf { it.base == from }
    val amountValue = ExpressionParser.evaluate(amount)
    val converted = quotes?.rateFor(to)?.let { rate -> amountValue?.times(rate) }
    val boardRows = favourites
        .filter { it != from }
        .map { code ->
            BoardRow(
                info = Currencies.info(code),
                converted = quotes?.rateFor(code)?.let { rate -> amountValue?.times(rate) },
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // One field that takes a sentence, a pasted price, or a photo, and turns
        // any of them into the same conversion.
        SmartBar(
            onUnderstood = { understood ->
                when (understood) {
                    is Understood.Currency -> {
                        amount = formatDecimal(understood.amount)
                        if (understood.from in codes) choose(understood.from, isBase = true)
                        understood.to?.takeIf { it in codes }?.let { choose(it, isBase = false) }
                    }

                    is Understood.Discount -> amount = formatDecimal(understood.result)
                    is Understood.Arithmetic -> amount = formatDecimal(understood.value)
                    is Understood.Split -> amount = formatDecimal(understood.amount)
                    // Units are a different tab; the phrase is still evaluated,
                    // and the amount carried over is the useful part here.
                    is Understood.Units -> amount = formatDecimal(understood.amount)
                }
            },
        )

        clipboardHit?.let { hit ->
            ClipboardChip(
                detected = hit,
                onAccept = {
                    amount = formatDecimal(hit.amount)
                    hit.code?.takeIf { it in codes }?.let { choose(it, isBase = true) }
                    clipboardHit = null
                },
                onDismiss = { clipboardHit = null },
            )
        }

        AmountField(
            value = amount,
            onValueChange = { amount = it },
            evaluated = amountValue,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CurrencyPicker(
                title = stringResource(R.string.convert_from),
                codes = codes,
                selected = from,
                onSelect = { choose(it, isBase = true) },
                favourites = favourites,
                recents = recents,
                onToggleFavourite = { favourites = ConvertPrefs.toggleFavourite(context, it) },
                modifier = Modifier.weight(1f),
            )
            SwapButton {
                val previous = from
                from = to
                to = previous
            }
            CurrencyPicker(
                title = stringResource(R.string.convert_to),
                codes = codes,
                selected = to,
                onSelect = { choose(it, isBase = false) },
                favourites = favourites,
                recents = recents,
                onToggleFavourite = { favourites = ConvertPrefs.toggleFavourite(context, it) },
                // Quoted against the current base, so the list doubles as a
                // rate board once the fetch lands.
                rateFor = { code -> quotes?.rateFor(code) },
                modifier = Modifier.weight(1f),
            )
        }

        ResultCard(
            value = converted?.let { formatMoney(it) } ?: "-",
            caption = rateCaption(
                loading = loading,
                failed = failed,
                offlineSince = offlineSince,
                table = table,
            ),
            unit = "${Currencies.info(to).flag} $to",
            emphasiseCaption = failed || offlineSince != null,
        )

        trend?.let { history ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Sparkline(
                    trend = history,
                    from = from,
                    to = to,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        CurrencyBoard(
            rows = boardRows,
            base = from,
            onSelect = { choose(it, isBase = false) },
        )

        AlertsPanel(from = from, to = to, currentRate = quotes?.rateFor(to))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A fixed slot for the spinner: showing and hiding it must not shift
            // the refresh button, and CircularProgressIndicator needs both
            // dimensions constrained or it falls back to its 40dp default width.
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            OutlineAction(
                label = stringResource(R.string.convert_refresh),
                enabled = !loading,
                onClick = { scope.launch { load() } },
            )
        }
    }
}

/** Describes where the numbers came from: live, saved, or nowhere. */
@Composable
private fun rateCaption(
    loading: Boolean,
    failed: Boolean,
    offlineSince: Long?,
    table: RateTable?,
): String = when {
    loading && table == null -> stringResource(R.string.rates_loading)
    failed -> stringResource(R.string.rates_failed)
    offlineSince != null -> {
        val days = daysSince(offlineSince)
        if (days < 1) {
            stringResource(R.string.rates_offline_today)
        } else {
            pluralStringResource(R.plurals.rates_offline_days, days, days)
        }
    }

    table != null -> stringResource(R.string.rates_updated, table.date)
    else -> ""
}

/**
 * Reading the clipboard can fail (no permission, no focus, an item with no text),
 * and none of those are worth reporting - the feature is a suggestion.
 */
private fun readClipboard(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}.getOrNull()

@Composable
private fun ClipboardChip(
    detected: DetectedAmount,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val label = detected.code
        ?.let { "${Currencies.info(it).flag} ${formatMoney(detected.amount)} $it" }
        ?: formatMoney(detected.amount)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.clipboard_detected, label),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAccept)
                    .padding(vertical = 10.dp),
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss the clipboard suggestion",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun UnitsPanel() {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf(UnitCategory.Length) }
    var amount by rememberSaveable { mutableStateOf("1") }
    var custom by remember { mutableStateOf(CustomUnits.all(context)) }
    var fromIndex by rememberSaveable(category) { mutableStateOf(0) }
    var toIndex by rememberSaveable(category) { mutableStateOf(1) }

    // A unit the user defined behaves exactly like a built-in one from here on,
    // which is the whole point of storing it as a factor.
    val units = category.units + custom.filter { it.category == category }.map { it.toMeasure() }
    val from = units[fromIndex.coerceIn(units.indices)]
    val to = units[toIndex.coerceIn(units.indices)]
    val amountValue = ExpressionParser.evaluate(amount)
    val converted = amountValue?.let { convert(it, from, to) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Picker(
            title = stringResource(R.string.convert_units),
            options = UnitCategory.entries,
            selected = category,
            optionLabel = { it.label },
            onSelect = { category = it },
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            evaluated = amountValue,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Picker(
                title = stringResource(R.string.convert_from),
                options = units.indices.toList(),
                selected = fromIndex.coerceIn(units.indices),
                optionLabel = { "${units[it].label} (${units[it].symbol})" },
                onSelect = { fromIndex = it },
                modifier = Modifier.weight(1f),
            )
            SwapButton {
                val previous = fromIndex
                fromIndex = toIndex
                toIndex = previous
            }
            Picker(
                title = stringResource(R.string.convert_to),
                options = units.indices.toList(),
                selected = toIndex.coerceIn(units.indices),
                optionLabel = { "${units[it].label} (${units[it].symbol})" },
                onSelect = { toIndex = it },
                modifier = Modifier.weight(1f),
            )
        }
        ResultCard(
            value = converted?.let { formatDecimal(it) } ?: "-",
            caption = "${from.label} → ${to.label}",
            unit = to.symbol,
            emphasiseCaption = false,
        )

        CustomUnitEditor(
            category = category,
            units = custom.filter { it.category == category },
            onAdd = { label, symbol, amountOf, reference ->
                custom = CustomUnits.add(context, label, symbol, category, amountOf, reference)
            },
            onRemove = { custom = CustomUnits.remove(context, it) },
        )
    }
}

/**
 * Defining a personal unit, phrased the way people describe one: "a crate is 24
 * bottles". Working out the factor against the base unit is the app's job, not
 * the user's.
 */
@Composable
private fun CustomUnitEditor(
    category: UnitCategory,
    units: List<CustomUnit>,
    onAdd: (String, String, Double, Measure) -> Unit,
    onRemove: (CustomUnit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("") }
    var symbol by rememberSaveable { mutableStateOf("") }
    var howMany by rememberSaveable { mutableStateOf("") }
    var referenceIndex by rememberSaveable(category) { mutableStateOf(0) }

    // Temperature units carry an offset, which makes "n of them" meaningless.
    val references = category.units.filter { it.offset == 0.0 }
    if (references.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.units_custom).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlineAction(
                    label = stringResource(R.string.units_custom_add),
                    onClick = { open = !open },
                )
            }

            if (units.isEmpty() && !open) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.units_custom_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            units.forEach { unit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "${unit.label} (${unit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove ${unit.label}",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRemove(unit) }
                            .padding(6.dp),
                    )
                }
            }

            if (open) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.units_custom_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.take(4) },
                        label = { Text(stringResource(R.string.units_custom_symbol)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = howMany,
                        onValueChange = { text ->
                            howMany = text.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text(stringResource(R.string.units_custom_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Picker(
                        title = stringResource(R.string.units_custom_of),
                        options = references.indices.toList(),
                        selected = referenceIndex.coerceIn(references.indices),
                        optionLabel = { "${references[it].label} (${references[it].symbol})" },
                        onSelect = { referenceIndex = it },
                        modifier = Modifier.weight(1.4f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlineAction(
                    label = stringResource(R.string.units_custom_save),
                    enabled = label.isNotBlank() && howMany.toDoubleOrNull() != null,
                    onClick = {
                        onAdd(
                            label,
                            symbol,
                            howMany.toDoubleOrNull() ?: 0.0,
                            references[referenceIndex.coerceIn(references.indices)],
                        )
                        label = ""
                        symbol = ""
                        howMany = ""
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Accepts a whole expression, not just a number: pasting "12*3.5" or typing
 * "(80+20)/4" is a normal thing to want in a converter, and the app already owns
 * a parser. [evaluated] drives the running total shown underneath.
 */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    evaluated: Double?,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter { it in ALLOWED_AMOUNT_CHARS }) },
            label = { Text(stringResource(R.string.convert_amount)) },
            singleLine = true,
            isError = value.isNotEmpty() && evaluated == null,
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        // Only worth showing when there is arithmetic to resolve; echoing a plain
        // number back at the user is noise.
        if (ExpressionParser.isExpression(value)) {
            Text(
                text = evaluated?.let { "= ${formatDecimal(it)}" }
                    ?: stringResource(R.string.amount_invalid),
                style = MaterialTheme.typography.labelMedium,
                color = if (evaluated != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(start = 14.dp, top = 4.dp),
            )
        }
    }
}

/** Digits, separators and every operator glyph [ExpressionParser] understands. */
private const val ALLOWED_AMOUNT_CHARS = "0123456789.,+-*/^()%×÷−xX "

@Composable
private fun <T> Picker(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // The menu is anchored to this Box - which wraps the field alone - so it
        // drops directly below the field instead of over the label above it.
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceVariant,
                contentColor = scheme.onSurface,
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { fieldWidth = it.size.width }
                    .semantics { contentDescription = "$title: ${optionLabel(selected)}" },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = optionLabel(selected),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // Match the field's width so the menu reads as an extension of it.
                modifier = Modifier
                    .width(with(density) { fieldWidth.toDp() })
                    .heightIn(max = 320.dp),
            ) {
                options.forEach { option ->
                    val active = option == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) scheme.primary else scheme.onSurface,
                                maxLines = 1,
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Bottom-aligned by its parent Row rather than by a hand-tuned top spacer, so it
 * lines up with the picker fields even when a label wraps to two lines.
 */
@Composable
private fun SwapButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = scheme.primary,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.semantics {
            contentDescription = "Swap the two units"
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ResultCard(
    value: String,
    caption: String,
    unit: String,
    emphasiseCaption: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                )
            }
            if (caption.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (emphasiseCaption) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutlineAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        contentColor = if (enabled) scheme.primary else scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Ten significant digits, no scientific notation, no trailing zeroes. */
internal fun formatDecimal(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "-"
    if (value == 0.0) return "0"
    return BigDecimal(value)
        .round(MathContext(10, RoundingMode.HALF_UP))
        .stripTrailingZeros()
        .toPlainString()
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ConvertPreview() {
    FirstTestAppTheme {
        ConvertScreen()
    }
}
