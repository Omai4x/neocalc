package com.omai.neocalc.convert

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omai.neocalc.R

/** A labelled group of currencies inside the modal - "Pinned", "Recent", "All". */
internal data class Section(val titleRes: Int, val entries: List<CurrencyInfo>)

/**
 * The currency counterpart to the generic unit picker: with ~160 options a
 * dropdown is a scroll marathon, so tapping the field opens a searchable modal
 * instead. The field itself still reads as the same control.
 */
@Composable
fun CurrencyPicker(
    title: String,
    codes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    favourites: List<String> = emptyList(),
    recents: List<String> = emptyList(),
    onToggleFavourite: (String) -> Unit = {},
    /** Optional quote shown beside each row, e.g. "0.9231". */
    rateFor: (String) -> Double? = { null },
) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val info = Currencies.info(selected)

    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            color = scheme.surfaceVariant,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$title: ${info.code}, ${info.name}. Tap to search"
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(text = info.flag, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.code,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }

    if (open) {
        CurrencySearchDialog(
            title = title,
            codes = codes,
            selected = selected,
            favourites = favourites,
            recents = recents,
            onToggleFavourite = onToggleFavourite,
            rateFor = rateFor,
            onDismiss = { open = false },
            onSelect = {
                onSelect(it)
                open = false
            },
        )
    }
}

@Composable
private fun CurrencySearchDialog(
    title: String,
    codes: List<String>,
    selected: String,
    favourites: List<String>,
    recents: List<String>,
    onToggleFavourite: (String) -> Unit,
    rateFor: (String) -> Double?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Searching flattens the list: once you have typed, sections are noise -
    // you want every match, ranked by nothing but the query.
    val sections = remember(codes, query, favourites, recents) {
        buildSections(codes, query, favourites, recents)
    }

    // Typing is the point of this dialog, so open straight into the field.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // A new query always starts from the top; otherwise the list keeps the
    // scroll offset of the previous, longer result set.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.currency_select, title.lowercase()),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.currency_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { query = "" }
                                    .padding(8.dp),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(12.dp))

                if (sections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.currency_search_empty, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        sections.forEach { section ->
                            // Only worth a header when there is more than one
                            // group; a lone "All currencies" label says nothing.
                            if (sections.size > 1) {
                                item(key = "header-${section.titleRes}") {
                                    SectionHeader(stringResource(section.titleRes))
                                }
                            }
                            items(
                                items = section.entries,
                                key = { "${section.titleRes}-${it.code}" },
                            ) { info ->
                                CurrencyRow(
                                    info = info,
                                    active = info.code == selected,
                                    pinned = info.code in favourites,
                                    rate = rateFor(info.code),
                                    onClick = { onSelect(info.code) },
                                    onTogglePin = { onToggleFavourite(info.code) },
                                )
                                HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pinned and recent first when browsing, one flat list when searching. Returns
 * empty when nothing matches, which is what drives the no-results message.
 */
internal fun buildSections(
    codes: List<String>,
    query: String,
    favourites: List<String>,
    recents: List<String>,
): List<Section> {
    val all = codes.map(Currencies::info)
    if (query.isNotBlank()) {
        val matches = all.filter { it.matches(query) }
        return if (matches.isEmpty()) emptyList() else listOf(Section(R.string.currency_all, matches))
    }

    val available = codes.toSet()
    val pinned = favourites.filter { it in available }.map(Currencies::info)
    // A pinned currency is already at the top; repeating it under "Recent" wastes
    // a row and makes the two sections look unreliable.
    val recent = recents.filter { it in available && it !in favourites }.map(Currencies::info)

    return buildList {
        if (pinned.isNotEmpty()) add(Section(R.string.currency_pinned, pinned))
        if (recent.isNotEmpty()) add(Section(R.string.currency_recent, recent))
        if (all.isNotEmpty()) add(Section(R.string.currency_all, all))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun CurrencyRow(
    info: CurrencyInfo,
    active: Boolean,
    pinned: Boolean,
    rate: Double?,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(text = info.flag, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.code,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) scheme.primary else scheme.onSurface,
            )
            Text(
                text = info.name,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (rate != null) {
            Text(
                // Four decimals, not formatDecimal's ten: this is a glanceable
                // hint beside the name, not the conversion result.
                text = formatRate(rate),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) {
            if (active) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // The pin is its own target rather than a long-press, so it is
        // discoverable and reachable with a screen reader.
        Icon(
            imageVector = if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (pinned) {
                "${info.code} is pinned. Tap to unpin"
            } else {
                "Pin ${info.code} to the board"
            },
            tint = if (pinned) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTogglePin)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .size(34.dp),
        )
    }
}
