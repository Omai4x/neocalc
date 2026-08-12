package com.omai.neocalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the app has, in three buckets.
 *
 * Measured from the actual composable's constraints rather than from the display
 * metrics, so it is right inside a split-screen window, a freeform window, or a
 * foldable's inner screen - all of which report a full-size display while giving
 * the app a fraction of it.
 */
enum class WindowSize {
    /** Phones in portrait, and any narrow split-screen window. */
    Compact,

    /** Large phones in landscape, small tablets, unfolded inner screens. */
    Medium,

    /** Tablets, desktops, and anything else genuinely wide. */
    Expanded,
    ;

    val isCompact: Boolean get() = this == Compact
    val atLeastMedium: Boolean get() = this != Compact

    /**
     * Screen padding that grows with the window. A 16dp gutter that looks right
     * on a phone looks mean on a tablet.
     */
    val gutter: Dp
        get() = when (this) {
            Compact -> 16.dp
            Medium -> 24.dp
            Expanded -> 32.dp
        }

    /** Vertical rhythm, scaled the same way. */
    val spacing: Dp
        get() = when (this) {
            Compact -> 12.dp
            Medium -> 16.dp
            Expanded -> 20.dp
        }

    /**
     * Text and controls stop growing past this; a 900dp-wide input field is not
     * more usable than a 600dp one, just harder to read across.
     */
    val contentMaxWidth: Dp
        get() = when (this) {
            Compact -> Dp.Unspecified
            Medium -> 620.dp
            Expanded -> 760.dp
        }

    /** How many columns a card grid should use. */
    val gridColumns: Int
        get() = when (this) {
            Compact -> 2
            Medium -> 3
            Expanded -> 4
        }

    fun padding(vertical: Dp = 12.dp) = PaddingValues(horizontal = gutter, vertical = vertical)

    companion object {
        /** Material's own breakpoints, so this agrees with the rest of Android. */
        fun of(widthDp: Dp): WindowSize = when {
            widthDp < 600.dp -> Compact
            widthDp < 840.dp -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowSize: ProvidableCompositionLocal<WindowSize> =
    compositionLocalOf { WindowSize.Compact }

/**
 * Measures the space available and publishes it to everything inside.
 *
 * Wrapping the whole app once means no screen has to measure anything itself,
 * and a screen that does not care about size simply ignores the local.
 */
@Composable
fun WithWindowSize(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowSize provides WindowSize.of(maxWidth)) {
            content()
        }
    }
}

/**
 * Centres a column of content and stops it from stretching on a wide screen.
 * On a phone this is a no-op, which is why screens can apply it unconditionally.
 */
@Composable
fun ResponsiveColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = LocalWindowSize.current.spacing,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val window = LocalWindowSize.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (window.contentMaxWidth == Dp.Unspecified) {
                        Modifier
                    } else {
                        Modifier.widthIn(max = window.contentMaxWidth)
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}
