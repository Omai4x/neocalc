package com.omai.neocalc.split

import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omai.neocalc.R
import com.omai.neocalc.calculator.ExpressionParser
import com.omai.neocalc.convert.ConvertPrefs
import com.omai.neocalc.convert.Currencies
import com.omai.neocalc.ui.LocalWindowSize
import com.omai.neocalc.ui.ResponsiveColumn
import java.math.BigDecimal
import java.util.Locale

/**
 * Splitting a bill: the most common thing anybody actually does with a
 * calculator at a table, and the one thing a calculator has never made easy.
 *
 * [initialAmount] arrives from the calculator or the history tape, so a total
 * worked out on the keypad can be split without retyping it.
 */
@Composable
fun SplitScreen(
    modifier: Modifier = Modifier,
    initialAmount: String? = null,
    onAmountConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val window = LocalWindowSize.current
    val scheme = MaterialTheme.colorScheme

    var amount by rememberSaveable { mutableStateOf("") }
    var people by rememberSaveable { mutableIntStateOf(2) }
    // Itemised mode: what each person actually ordered. Tax and tip are then
    // shared in proportion, which is the fair reading of "split by what we had".
    var itemised by rememberSaveable { mutableStateOf(false) }
    val items = rememberSaveable(saver = ItemsSaver) { mutableStateListOf("") }
    var tipPercent by rememberSaveable { mutableStateOf(10.0) }
    var taxPercent by rememberSaveable { mutableStateOf(0.0) }
    var roundUp by rememberSaveable { mutableStateOf(false) }
    var tipOnTax by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialAmount) {
        if (initialAmount != null) {
            amount = initialAmount
            onAmountConsumed()
        }
    }

    val currency = ConvertPrefs.pair(context).first
    val symbol = Currencies.info(currency).flag
    val value = ExpressionParser.evaluate(amount) ?: 0.0
    val itemValues = items.map { ExpressionParser.evaluate(it) ?: 0.0 }
    val result = if (itemised) {
        Bill.splitByItems(
            items = itemValues,
            taxPercent = taxPercent,
            tipPercent = tipPercent,
            tipOnTax = tipOnTax,
        )
    } else {
        Bill.split(
            amount = value,
            taxPercent = taxPercent,
            tipPercent = tipPercent,
            people = people,
            tipOnTax = tipOnTax,
            roundUp = roundUp,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(window.padding()),
    ) {
        ResponsiveColumn {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    false to stringResource(R.string.split_mode_even),
                    true to stringResource(R.string.split_mode_items),
                ).forEach { (mode, label) ->
                    val active = mode == itemised
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) scheme.primary else Color.Transparent)
                            .clickable { itemised = mode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (itemised) {
                ItemList(
                    items = items,
                    symbol = symbol,
                    onChange = { index, text -> items[index] = text },
                    onAdd = { items.add("") },
                    onRemove = { index -> if (items.size > 1) items.removeAt(index) },
                )
            } else {
                OutlinedTextField(
                    value = amount,
                onValueChange = { text ->
                    amount = text.filter { it in "0123456789.,+-*/()%" }
                },
                label = { Text(stringResource(R.string.split_bill_total)) },
                placeholder = { Text("0.00") },
                prefix = { Text("$symbol ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!itemised) {
                Card(title = stringResource(R.string.split_people)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StepperButton(Icons.Rounded.Remove, "One fewer person") {
                        people = (people - 1).coerceAtLeast(1)
                    }
                    Text(
                        text = people.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepperButton(Icons.Rounded.Add, "One more person") {
                        people = (people + 1).coerceAtMost(50)
                    }
                    }
                }
            }

            Card(title = stringResource(R.string.split_tip)) {
                PercentRow(
                    options = listOf(0.0, 5.0, 10.0, 12.5, 15.0, 20.0),
                    selected = tipPercent,
                    onSelect = { tipPercent = it },
                )
            }

            Card(title = stringResource(R.string.split_tax)) {
                PercentRow(
                    options = listOf(0.0, 5.0, 7.5, 10.0, 15.0, 20.0),
                    selected = taxPercent,
                    onSelect = { taxPercent = it },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!itemised) {
                    ToggleChip(
                        label = stringResource(R.string.split_round_up),
                        active = roundUp,
                        modifier = Modifier.weight(1f),
                    ) { roundUp = !roundUp }
                }
                ToggleChip(
                    label = stringResource(R.string.split_tip_on_tax),
                    active = tipOnTax,
                    modifier = Modifier.weight(1f),
                ) { tipOnTax = !tipOnTax }
            }

            ResultPanel(
                result = result,
                symbol = symbol,
                people = if (itemised) items.size else people,
                itemised = itemised,
            )
        }
    }
}

@Composable
private fun ResultPanel(
    result: BillSplit,
    symbol: String,
    people: Int,
    itemised: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(
                    if (itemised) R.string.split_bill_total else R.string.split_each,
                ).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = money(if (itemised) result.total else result.perPerson),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " $symbol",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                )
            }

            if (itemised) {
                Spacer(Modifier.height(10.dp))
                result.shares.forEachIndexed { index, share ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.split_person, index + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = money(share),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                        )
                    }
                }
            }

            if (result.uneven && !itemised) {
                Spacer(Modifier.height(4.dp))
                // Being explicit about the odd penny is the point: silently
                // rounding is how a split stops adding up to the bill.
                Text(
                    text = stringResource(
                        R.string.split_uneven,
                        result.shares.count { it == result.shares.first() },
                        money(result.shares.first()),
                        money(result.shares.last()),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))
            listOf(
                stringResource(R.string.split_subtotal) to result.subtotal,
                stringResource(R.string.split_tax) to result.tax,
                stringResource(R.string.split_tip) to result.tip,
                stringResource(R.string.split_total) to result.total,
            ).forEachIndexed { index, (label, figure) ->
                val emphasised = index == 3
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (emphasised) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                    Text(
                        text = money(figure),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (emphasised) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.split_ways, people),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One row per person, each holding what they ordered. Rows accept an expression,
 * so "12.50+3" for two things is fine without a separate line.
 */
@Composable
private fun ItemList(
    items: List<String>,
    symbol: String,
    onChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Card(title = stringResource(R.string.split_items)) {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { text ->
                        onChange(index, text.filter { it in "0123456789.,+-*/()%" })
                    },
                    label = { Text(stringResource(R.string.split_person, index + 1)) },
                    prefix = { Text("$symbol ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                StepperButton(Icons.Rounded.Remove, "Remove person ${index + 1}") {
                    onRemove(index)
                }
            }
        }
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.split_add_person),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Keeps the item rows across rotation; a plain list of strings is enough. */
private val ItemsSaver = androidx.compose.runtime.saveable.listSaver<
    androidx.compose.runtime.snapshots.SnapshotStateList<String>, String,
    >(
    save = { it.toList() },
    restore = { saved -> androidx.compose.runtime.mutableStateListOf(*saved.toTypedArray()) },
)

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PercentRow(options: List<Double>, selected: Double, onSelect: (Double) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { percent ->
            val active = percent == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) scheme.primary else scheme.surfaceVariant)
                    .clickable { onSelect(percent) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (percent == percent.toInt().toDouble()) {
                        "${percent.toInt()}%"
                    } else {
                        String.format(Locale.getDefault(), "%.1f%%", percent)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (active) scheme.primary else scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (active) scheme.primary.copy(alpha = 0.5f) else scheme.outline),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceVariant,
        contentColor = scheme.primary,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description)
        }
    }
}

private fun money(value: BigDecimal): String =
    String.format(Locale.getDefault(), "%,.2f", value)
