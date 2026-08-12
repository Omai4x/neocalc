package com.omai.neocalc.convert

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omai.neocalc.R
import java.util.Locale
import kotlin.math.abs

/**
 * The pair's last 30 closes as a single line - enough to answer "is this a good
 * time?" without turning the converter into a trading app. Drawn with Canvas, so
 * it costs no charting dependency.
 */
@Composable
fun Sparkline(
    trend: RateTrend,
    from: String,
    to: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val rising = trend.changePercent >= 0
    // Not red/green: the same two colours already mean error and primary in this
    // app, and a rate going up is not an error. Primary for a rise, muted for a
    // fall, with the sign doing the actual work.
    val lineColor = if (rising) scheme.primary else scheme.tertiary
    val change = String.format(
        Locale.getDefault(),
        "%s%.2f%%",
        if (rising) "+" else "−",
        abs(trend.changePercent),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$from to $to over 30 days, $change, low ${trend.low}, high ${trend.high}"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.trend_window, from, to),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = change,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = lineColor,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 6.dp),
        ) {
            val points = trend.points
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            // Inset so the stroke's own width can't clip against the top or bottom.
            val inset = 3f
            val usableHeight = (size.height - inset * 2).coerceAtLeast(1f)

            val offsets = points.mapIndexed { index, point ->
                Offset(
                    x = index * stepX,
                    // Canvas y grows downward; the high must sit at the top.
                    y = inset + (1f - trend.normalise(point.rate)) * usableHeight,
                )
            }

            val line = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }

            // A soft fill under the line reads as "area of movement" and keeps the
            // shape legible when the line itself is nearly flat.
            val fill = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, size.height)
                lineTo(offsets.first().x, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                ),
            )
            drawPath(path = line, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = offsets.last())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.trend_low, formatRate(trend.low)),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.trend_high, formatRate(trend.high)),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Four decimals: the precision a rate is quoted at, not the ten a result needs. */
internal fun formatRate(value: Double): String =
    String.format(Locale.getDefault(), "%,.4f", value)
