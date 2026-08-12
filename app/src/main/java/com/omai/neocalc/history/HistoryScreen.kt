package com.omai.neocalc.history

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omai.neocalc.R
import com.omai.neocalc.ui.theme.FirstTestAppTheme

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onClear: () -> Unit,
    onReuse: (HistoryEntry) -> Unit,
    onConvert: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_reuse).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entries.isNotEmpty()) {
                    Surface(
                        onClick = onClear,
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        contentColor = scheme.error,
                        border = BorderStroke(1.dp, scheme.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.history_clear),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(entries) { entry ->
                    HistoryRow(entry = entry, onReuse = onReuse, onConvert = onConvert)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onReuse: (HistoryEntry) -> Unit,
    onConvert: (HistoryEntry) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onReuse(entry) },
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${entry.expression} equals ${entry.result}" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = entry.expression,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = entry.result,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
            // Its own target inside the row: tapping the row reuses the result in
            // the calculator, which is a different intent from converting it.
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = "Convert ${entry.result} to another currency",
                tint = scheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onConvert(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun HistoryPreview() {
    FirstTestAppTheme {
        HistoryScreen(
            entries = listOf(
                HistoryEntry("12 × 7", "84"),
                HistoryEntry("100 + 10", "110"),
                HistoryEntry("√ 2", "1.41421356237"),
            ),
            onClear = {},
            onReuse = {},
            onConvert = {},
        )
    }
}
