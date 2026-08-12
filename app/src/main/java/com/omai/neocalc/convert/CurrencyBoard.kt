package com.omai.neocalc.convert

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omai.neocalc.R

/** One line of the board: what the amount is worth in a pinned currency. */
data class BoardRow(val info: CurrencyInfo, val converted: Double?)

/**
 * One amount, every pinned currency at once - the view a traveller actually
 * wants, and the reason the rate table is fetched as a whole map rather than a
 * single quote. Costs no extra network call.
 */
@Composable
fun CurrencyBoard(
    rows: List<BoardRow>,
    base: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.board_title, base).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = scheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                BoardLine(row = row, onClick = { onSelect(row.info.code) })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.board_hint),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BoardLine(row: BoardRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val value = row.converted?.let { formatMoney(it) } ?: "-"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Tapping a line makes it the target currency, so the board is also
            // the fastest way to switch what the big result shows.
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "$value ${row.info.name}. Tap to convert to this"
            },
    ) {
        Text(text = row.info.flag, style = MaterialTheme.typography.titleMedium)
        Text(
            text = row.info.code,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Money rounding, not calculator rounding: two decimals with grouping, but small
 * values keep enough digits to be meaningful (0.0043, not 0.00).
 */
internal fun formatMoney(value: Double): String {
    val magnitude = kotlin.math.abs(value)
    val decimals = when {
        magnitude == 0.0 -> 2
        magnitude < 0.01 -> 6
        magnitude < 1 -> 4
        else -> 2
    }
    return String.format(java.util.Locale.getDefault(), "%,.${decimals}f", value)
}
