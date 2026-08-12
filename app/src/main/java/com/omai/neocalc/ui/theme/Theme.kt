package com.omai.neocalc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val DarkColors = darkColorScheme(
    primary = Operation,
    onPrimary = OnOperation,
    primaryContainer = Operation,
    onPrimaryContainer = OnOperation,
    secondary = OperationSoft,
    onSecondary = OnOperation,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = OnGround,
    // Tertiary is the cool counterweight: everything that is deliberately not
    // an operation. Keeping it out of `primary` is what stops the orange from
    // losing its meaning.
    tertiary = Info,
    onTertiary = Ground,
    background = Ground,
    onBackground = OnGround,
    surface = Surface,
    onSurface = OnGround,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = OnGroundMuted,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
    onError = OnDanger,
)

private val LightColors = lightColorScheme(
    primary = OperationLight,
    onPrimary = PaperSurface,
    primaryContainer = OperationLight,
    onPrimaryContainer = PaperSurface,
    secondary = OperationSoftLight,
    onSecondary = PaperSurface,
    secondaryContainer = PaperMuted,
    onSecondaryContainer = OnPaper,
    tertiary = InfoLight,
    onTertiary = PaperSurface,
    background = Paper,
    onBackground = OnPaper,
    surface = PaperSurface,
    onSurface = OnPaper,
    surfaceVariant = PaperMuted,
    onSurfaceVariant = OnPaperMuted,
    outline = PaperOutline,
    outlineVariant = PaperOutline,
    error = DangerLight,
    onError = PaperSurface,
)

/** Soft, even radii - rounded enough to feel modern, never pill-shaped. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dynamic colour is deliberately not used: the palette *is* the product's
 * identity here, and letting the wallpaper recolour it undoes the point.
 */
@Composable
fun FirstTestAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
