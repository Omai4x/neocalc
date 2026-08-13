package com.omai.neocalc.onboarding

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The device previews shown on the onboarding pages.
 *
 * These are drawn live in Compose rather than shipped as PNG screenshots. Real
 * captures would go stale the moment a colour or a label changed, would need a
 * light and a dark copy of every image, and would add megabytes to the APK.
 * Built from the same MaterialTheme as the app, these follow the user's theme
 * automatically and can never show a version of the UI that no longer exists.
 */

/** A phone bezel with a screen inside it. Everything below draws into one. */
@Composable
fun PhoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface,
        border = BorderStroke(2.dp, scheme.outline),
        shadowElevation = 12.dp,
        modifier = modifier.aspectRatio(0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(scheme.background),
        ) {
            // The pill at the top is what makes the frame read as a phone.
            Box(
                modifier = Modifier.fillMaxWidth().height(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(scheme.outline),
                )
            }
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp)) { content() }
        }
    }
}

/** A miniature of the calculator screen: status chips, display, keypad. */
@Composable
fun CalculatorPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            MiniChip("DEG", accent = true)
            MiniChip("M")
            Spacer(Modifier.weight(1f))
            MiniChip("f(x)", accent = true)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "128 ×",
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = "1,024",
                fontSize = 50.sp,
                fontWeight = FontWeight.Light,
                color = scheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            listOf("AC", "( )", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "="),
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            ) {
                row.forEach { key ->
                    val operator = key in listOf("÷", "×", "−", "+", "=")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    key == "=" -> scheme.primary
                                    operator -> scheme.primary.copy(alpha = 0.14f)
                                    else -> scheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            fontSize = 15.sp,
                            fontWeight = if (operator) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                key == "=" -> scheme.onPrimary
                                operator -> scheme.primary
                                else -> scheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A miniature of the converter: pickers with flags, result, trend, board. */
@Composable
fun ConverterPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(scheme.surfaceVariant)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf("Currency" to true, "Units" to false).forEach { (label, active) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (active) scheme.primary else Color.Transparent)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniPicker("🇺🇸", "USD", Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
            MiniPicker("🇪🇺", "EUR", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))

        MiniCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "92.31",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                )
                Text(" 🇪🇺 EUR", fontSize = 10.5.sp, color = scheme.primary)
            }
            Text("Updated today", fontSize = 8.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(5.dp))

        MiniCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("USD to EUR, 30 days", fontSize = 8.sp, color = scheme.onSurfaceVariant)
                Text("+1.24%", fontSize = 8.sp, color = scheme.primary)
            }
            Spacer(Modifier.height(3.dp))
            // A stylised sparkline: bars of varying height read as a chart at
            // this size, where an actual polyline would be a smear.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxWidth().height(18.dp),
            ) {
                listOf(6, 8, 7, 10, 9, 12, 11, 14, 13, 16, 15, 18).forEach { height ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(height.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.primary.copy(alpha = 0.55f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))

        MiniCard {
            Text("ALSO WORTH, FROM USD", fontSize = 7.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            listOf("🇬🇧" to "78.45", "🇯🇵" to "14,722", "🇳🇬" to "158,900").forEach { (flag, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(flag, fontSize = 9.sp)
                    Text(value, fontSize = 9.sp, color = scheme.onSurface)
                }
            }
        }
    }
}

/** A miniature of the currency search modal - the flag list in action. */
@Composable
fun SearchPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select to currency", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("pound", fontSize = 10.5.sp, color = scheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text("PINNED", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        listOf(
            Triple("🇬🇧", "GBP", "British Pound Sterling"),
            Triple("🇪🇬", "EGP", "Egyptian Pound"),
        ).forEach { (flag, code, name) -> MiniCurrencyRow(flag, code, name, pinned = true) }
        Spacer(Modifier.height(4.dp))
        Text("ALL CURRENCIES", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        listOf(
            Triple("🇱🇧", "LBP", "Lebanese Pound"),
            Triple("🇸🇩", "SDG", "Sudanese Pound"),
            Triple("🇸🇭", "SHP", "Saint Helena Pound"),
            Triple("🇸🇾", "SYP", "Syrian Pound"),
            Triple("🇬🇮", "GIP", "Gibraltar Pound"),
        ).forEach { (flag, code, name) -> MiniCurrencyRow(flag, code, name, pinned = false) }
    }
}

@Composable
private fun MiniCurrencyRow(flag: String, code: String, name: String, pinned: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
    ) {
        Text(flag, fontSize = 14.sp)
        Spacer(Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(code, fontSize = 10.5.sp, color = scheme.onSurface)
            Text(name, fontSize = 7.5.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(
            imageVector = if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            tint = if (pinned) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun MiniPicker(flag: String, code: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        Text(flag, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(code, fontSize = 10.5.sp, color = scheme.onSurface)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun MiniCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(7.dp)) { content() }
    }
}

@Composable
private fun MiniChip(label: String, accent: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (accent) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceVariant,
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            color = if (accent) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** The home-screen widget, shown on its own rather than inside a phone frame. */
@Composable
fun WidgetPreview(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("\uD83C\uDDFA\uD83C\uDDF8 USD to \uD83C\uDDEA\uD83C\uDDFA EUR", fontSize = 14.sp, color = scheme.onSurfaceVariant)
            Text(
                text = "0.9231",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text("Updated today", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** A soft brand-tinted backdrop the preview sits on, so it never floats. */
@Composable
fun PreviewStage(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.04f)),
                ),
            ),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
