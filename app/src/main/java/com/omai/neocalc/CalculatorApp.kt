package com.omai.neocalc

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import com.omai.neocalc.ui.WindowSize
import com.omai.neocalc.ui.LocalWindowSize
import com.omai.neocalc.smart.Understood
import com.omai.neocalc.smart.NaturalInput
import androidx.compose.runtime.LaunchedEffect
import com.omai.neocalc.about.AboutScreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import com.omai.neocalc.split.SplitScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omai.neocalc.calculator.CalculatorScreen
import com.omai.neocalc.calculator.CalculatorState
import com.omai.neocalc.calculator.CalculatorStateSaver
import com.omai.neocalc.calculator.Key
import com.omai.neocalc.calculator.enterValue
import com.omai.neocalc.calculator.pendingEvaluation
import com.omai.neocalc.calculator.press
import com.omai.neocalc.convert.ConvertScreen
import com.omai.neocalc.games.ArcadeScreen
import com.omai.neocalc.history.HistoryEntry
import com.omai.neocalc.history.HistoryListSaver
import com.omai.neocalc.history.HistoryScreen
import com.omai.neocalc.history.MAX_HISTORY

/**
 * Destinations are a plain enum rather than a NavHost: with three flat, stateless
 * sections there is no back stack worth modelling, and it keeps the project free
 * of a navigation dependency.
 */
private enum class Destination(val icon: ImageVector, val labelRes: Int) {
    Calculator(Icons.Rounded.Calculate, R.string.nav_calculator),
    Convert(Icons.Rounded.SwapHoriz, R.string.nav_convert),
    Split(Icons.Rounded.ReceiptLong, R.string.nav_split),
    History(Icons.Rounded.History, R.string.nav_history),
}

@Composable
fun CalculatorApp(
    onReplayTour: () -> Unit = {},
    sharedText: String? = null,
    onSharedConsumed: () -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Calculator) }
    // A value on its way from the calculator or the tape to the converter. Held
    // here because it has to survive the tab switch that carries it.
    var pendingAmount by rememberSaveable { mutableStateOf<String?>(null) }
    var arcade by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }

    // Text arriving from outside always means "convert this", so it lands on the
    // converter with the amount already filled in.
    LaunchedEffect(sharedText) {
        val understood = NaturalInput.parse(sharedText) ?: return@LaunchedEffect
        val amount = when (understood) {
            is Understood.Currency -> understood.amount
            is Understood.Discount -> understood.result
            is Understood.Arithmetic -> understood.value
            is Understood.Split -> understood.amount
            is Understood.Units -> understood.amount
        }
        pendingAmount = amount.toString().removeSuffix(".0")
        destination = Destination.Convert
        onSharedConsumed()
    }

    // The arcade takes over the whole window, bottom bar included: it is a mode,
    // not a fourth tab, and showing the calculator's navigation under a game
    // would give away that it is there.
    if (arcade) {
        ArcadeScreen(onExit = { arcade = false })
        return
    }

    // Calculator state is hoisted here so switching tabs doesn't discard it.
    var calculator by rememberSaveable(stateSaver = CalculatorStateSaver) {
        mutableStateOf(CalculatorState())
    }
    val history = rememberSaveable(saver = HistoryListSaver) {
        mutableStateListOf<HistoryEntry>()
    }

    if (about) {
        AboutScreen(
            versionName = BuildConfig.VERSION_NAME,
            history = history,
            onHistoryImported = { imported ->
                history.clear()
                history.addAll(imported.take(MAX_HISTORY))
            },
            onClose = { about = false },
            onReplayTour = {
                about = false
                onReplayTour()
            },
        )
        return
    }

    // One implementation of "a key was pressed", used by both layouts.
    fun onCalculatorKey(key: Key) {
        val before = calculator
        val after = before.press(key)
        // Record the tape entry from the *pre-press* state, which is the only
        // place the operands are still both known.
        if (key == Key.Equals && after.error == null) {
            before.pendingEvaluation()?.let { expression ->
                history.add(0, HistoryEntry(expression, after.display))
                if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
            }
        }
        calculator = after
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // TopAppBar would inset itself; a plain Row in the topBar
                    // slot does not, so without this the title sits under the
                    // clock. Only the top edge: the bottom belongs to the
                    // navigation bar, which handles its own.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { about = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.about_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Icon(imageVector = item.icon, contentDescription = null)
                        },
                        label = {
                            Text(
                                stringResource(item.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val window = LocalWindowSize.current
            // On a genuinely wide window the calculator and the converter are
            // both visible at once: they are the two halves of the same job, and
            // a tablet has room for both. Anything narrower keeps the tabs.
            if (window == WindowSize.Expanded && destination != Destination.History) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        CalculatorScreen(
                            state = calculator,
                            onKey = { key -> onCalculatorKey(key) },
                            onConvert = { value ->
                                pendingAmount = value
                                destination = Destination.Convert
                            },
                            onSecretGesture = { arcade = true },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (destination) {
                            Destination.Split -> SplitScreen(
                                initialAmount = pendingAmount,
                                onAmountConsumed = { pendingAmount = null },
                            )

                            else -> ConvertScreen(
                                pendingAmount = pendingAmount,
                                onAmountConsumed = { pendingAmount = null },
                            )
                        }
                    }
                }
                return@Box
            }

            when (destination) {
                Destination.Calculator -> CalculatorScreen(
                    state = calculator,
                    onKey = { key -> onCalculatorKey(key) },
                    onConvert = { value ->
                        pendingAmount = value
                        destination = Destination.Convert
                    },
                    onSecretGesture = { arcade = true },
                )

                Destination.Convert -> ConvertScreen(
                    pendingAmount = pendingAmount,
                    onAmountConsumed = { pendingAmount = null },
                )

                Destination.Split -> SplitScreen(
                    initialAmount = pendingAmount,
                    onAmountConsumed = { pendingAmount = null },
                )

                Destination.History -> HistoryScreen(
                    entries = history,
                    onClear = { history.clear() },
                    onReuse = { entry ->
                        calculator = calculator.enterValue(entry.result)
                        destination = Destination.Calculator
                    },
                    onConvert = { entry ->
                        pendingAmount = entry.result
                        destination = Destination.Convert
                    },
                )
            }
        }
    }
}
