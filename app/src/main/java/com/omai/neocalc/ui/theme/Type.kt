package com.omai.neocalc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One family, one scale, and tabular figures wherever a number changes.
 *
 * The previous scale mixed the platform monospace into the display and body
 * styles to stop digits jittering. That solved the jitter and cost the app a
 * consistent voice: monospace body text reads as a terminal, not as an app.
 * `fontFeatureSettings = "tnum"` fixes the jitter properly - every digit gets
 * the same advance width without changing the typeface - so the whole product
 * can sit on one sans and still have a display that does not twitch.
 *
 * No font files are bundled and nothing is downloaded: the platform sans is
 * Roboto on Android, which supports tabular numerals, is already resident, and
 * renders the first frame with no fallback flash. A downloadable Inter would
 * match the reference design more literally at the cost of a Play Services
 * dependency and a visible reflow on cold start, which is a bad trade for a
 * screen whose entire job is a number.
 */

/** Digits with a fixed advance width. The single most important line here. */
private const val TABULAR = "tnum"

private val Sans = FontFamily.SansSerif

val Typography = Typography(
    // The answer. Light weight at this size reads as precision rather than
    // shouting, and the negative tracking closes the gaps a large sans leaves.
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = TABULAR,
    ),
    // Converter and split results.
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    // The pending expression above the display, and card headings.
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = TABULAR,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // 16sp floor for anything the user reads as prose.
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR,
    ),
    // Uppercase section labels. The wide tracking is what makes small caps
    // legible; without it they read as a smudge.
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
        fontFeatureSettings = TABULAR,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
)
