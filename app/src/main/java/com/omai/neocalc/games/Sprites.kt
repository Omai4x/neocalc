package com.omai.neocalc.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared sprite drawing.
 *
 * The games are Canvas-drawn rather than image-backed - no assets to ship - so
 * "looking like something" is a matter of building each character out of a few
 * primitives. Keeping those here means a brick, a ship or a wheel looks the
 * same wherever it appears, and no game re-invents a rounded rectangle.
 */

/** A block with a lit top edge and a shaded bottom: reads as solid, not flat. */
fun DrawScope.drawBevelBlock(
    topLeft: Offset,
    size: Size,
    color: Color,
    radius: Float = size.minDimension * 0.22f,
) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(color.lighten(0.22f), color, color.darken(0.18f)),
            startY = topLeft.y,
            endY = topLeft.y + size.height,
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(radius),
    )
    // A single highlight line along the top does most of the work.
    drawRoundRect(
        color = Color.White.copy(alpha = 0.28f),
        topLeft = Offset(topLeft.x + size.width * 0.12f, topLeft.y + size.height * 0.12f),
        size = Size(size.width * 0.76f, size.height * 0.16f),
        cornerRadius = CornerRadius(size.height * 0.08f),
    )
}

/** A ball with a specular dot, which is what stops it reading as a flat circle. */
fun DrawScope.drawGlossyBall(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.lighten(0.35f), color, color.darken(0.25f)),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius * 1.8f,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = radius * 0.22f,
        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
    )
}

/** The classic wedge-mouth disc, facing [facingDegrees], mouth open by [open]. */
fun DrawScope.drawChomper(
    topLeft: Offset,
    size: Float,
    facingDegrees: Float,
    open: Float,
    color: Color = Arcade.Yellow,
) {
    drawArc(
        color = color,
        startAngle = facingDegrees + open / 2f,
        sweepAngle = 360f - open,
        useCenter = true,
        topLeft = topLeft,
        size = Size(size, size),
    )
    // The eye sits above the mouth axis, rotated with the body.
    rotate(degrees = facingDegrees, pivot = Offset(topLeft.x + size / 2, topLeft.y + size / 2)) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = size * 0.07f,
            center = Offset(topLeft.x + size * 0.55f, topLeft.y + size * 0.25f),
        )
    }
}

/** A ghost: domed head, skirted hem, two eyes that look where it is going. */
fun DrawScope.drawGhost(
    topLeft: Offset,
    size: Float,
    color: Color,
    look: Direction,
    scared: Boolean = false,
) {
    val body = Path().apply {
        val left = topLeft.x + size * 0.08f
        val right = topLeft.x + size * 0.92f
        val top = topLeft.y + size * 0.06f
        val bottom = topLeft.y + size * 0.9f
        moveTo(left, bottom)
        lineTo(left, top + size * 0.36f)
        arcTo(
            rect = Rect(left, top, right, top + size * 0.72f),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(right, bottom)
        // Three scallops along the hem, the detail that makes it a ghost.
        val step = (right - left) / 3f
        var x = right
        repeat(3) {
            lineTo(x - step / 2, bottom - size * 0.14f)
            lineTo(x - step, bottom)
            x -= step
        }
        close()
    }
    drawPath(body, color)

    if (scared) {
        // Frightened ghosts get a flat mouth and blank eyes - the visual cue
        // that they are prey now.
        val y = topLeft.y + size * 0.62f
        drawLine(
            color = Color.White,
            start = Offset(topLeft.x + size * 0.24f, y),
            end = Offset(topLeft.x + size * 0.76f, y),
            strokeWidth = size * 0.07f,
        )
    }
    val eyeY = topLeft.y + size * 0.42f
    val pupil = Offset(look.dx * size * 0.06f, look.dy * size * 0.06f)
    listOf(0.34f, 0.66f).forEach { fraction ->
        val eye = Offset(topLeft.x + size * fraction, eyeY)
        drawCircle(Color.White, size * 0.13f, eye)
        drawCircle(Color(0xFF1A237E), size * 0.07f, eye + pupil)
    }
}

/** A player ship: hull, nose, fins, and a thruster flame when [thrusting]. */
fun DrawScope.drawShip(
    center: Offset,
    radius: Float,
    headingDegrees: Float,
    color: Color,
    thrusting: Boolean = false,
) {
    rotate(degrees = headingDegrees, pivot = center) {
        val hull = Path().apply {
            moveTo(center.x + radius, center.y)
            lineTo(center.x - radius * 0.7f, center.y - radius * 0.72f)
            lineTo(center.x - radius * 0.35f, center.y)
            lineTo(center.x - radius * 0.7f, center.y + radius * 0.72f)
            close()
        }
        drawPath(hull, color)
        drawPath(hull, Color.White.copy(alpha = 0.35f), style = Stroke(width = radius * 0.12f))
        drawCircle(Arcade.Sky, radius * 0.18f, Offset(center.x + radius * 0.1f, center.y))
        if (thrusting) {
            val flame = Path().apply {
                moveTo(center.x - radius * 0.35f, center.y - radius * 0.3f)
                lineTo(center.x - radius * 1.15f, center.y)
                lineTo(center.x - radius * 0.35f, center.y + radius * 0.3f)
                close()
            }
            drawPath(flame, Arcade.Amber)
        }
    }
}

/** A descending invader, two-frame animated by [frame]. */
fun DrawScope.drawInvader(topLeft: Offset, size: Float, color: Color, frame: Int) {
    val unit = size / 8f
    fun block(cx: Int, cy: Int, w: Int = 1, h: Int = 1) = drawRect(
        color = color,
        topLeft = Offset(topLeft.x + cx * unit, topLeft.y + cy * unit),
        size = Size(w * unit, h * unit),
    )
    // Body
    block(1, 2, 6, 3)
    block(2, 1, 4, 1)
    // Eyes punched out of the body
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(topLeft.x + 2 * unit, topLeft.y + 2 * unit),
        size = Size(unit, unit),
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(topLeft.x + 5 * unit, topLeft.y + 2 * unit),
        size = Size(unit, unit),
    )
    // Legs swap on alternate frames - the whole animation of the original.
    if (frame % 2 == 0) {
        block(0, 3, 1, 2)
        block(7, 3, 1, 2)
        block(1, 5, 1, 1)
        block(6, 5, 1, 1)
    } else {
        block(0, 2, 1, 2)
        block(7, 2, 1, 2)
        block(2, 5, 1, 1)
        block(5, 5, 1, 1)
    }
}

/** A car seen from above: body, cabin glass, and four wheels. */
fun DrawScope.drawCar(topLeft: Offset, size: Size, color: Color, facingUp: Boolean = true) {
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(topLeft.x - size.width * 0.06f, topLeft.y + size.height * 0.08f),
        size = size,
        cornerRadius = CornerRadius(size.width * 0.25f),
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(color.lighten(0.15f), color.darken(0.15f))),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(size.width * 0.25f),
    )
    val glassTop = if (facingUp) 0.18f else 0.52f
    drawRoundRect(
        color = Arcade.Sky.copy(alpha = 0.75f),
        topLeft = Offset(topLeft.x + size.width * 0.18f, topLeft.y + size.height * glassTop),
        size = Size(size.width * 0.64f, size.height * 0.3f),
        cornerRadius = CornerRadius(size.width * 0.12f),
    )
    listOf(0.16f, 0.78f).forEach { y ->
        listOf(-0.04f, 0.92f).forEach { x ->
            drawRoundRect(
                color = Color(0xFF212121),
                topLeft = Offset(topLeft.x + size.width * x, topLeft.y + size.height * y),
                size = Size(size.width * 0.12f, size.height * 0.14f),
                cornerRadius = CornerRadius(size.width * 0.04f),
            )
        }
    }
}

/** A bird with a flapping wing - the flappy games' player. */
fun DrawScope.drawBird(center: Offset, radius: Float, flap: Float, tilt: Float) {
    rotate(degrees = tilt, pivot = center) {
        drawCircle(Arcade.Amber, radius, center)
        drawCircle(Arcade.Yellow.copy(alpha = 0.6f), radius * 0.75f, center)
        // Wing rides up and down with the flap phase.
        drawOval(
            color = Arcade.Coral,
            topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.1f + flap * radius * 0.5f),
            size = Size(radius * 1.1f, radius * 0.6f),
        )
        drawCircle(Color.White, radius * 0.3f, Offset(center.x + radius * 0.35f, center.y - radius * 0.25f))
        drawCircle(Color.Black, radius * 0.14f, Offset(center.x + radius * 0.45f, center.y - radius * 0.25f))
        val beak = Path().apply {
            moveTo(center.x + radius * 0.7f, center.y)
            lineTo(center.x + radius * 1.35f, center.y + radius * 0.12f)
            lineTo(center.x + radius * 0.7f, center.y + radius * 0.3f)
            close()
        }
        drawPath(beak, Arcade.Coral)
    }
}

/** A frog, seen from above, for the road-crossing game. */
fun DrawScope.drawFrog(topLeft: Offset, size: Float) {
    val center = Offset(topLeft.x + size / 2, topLeft.y + size / 2)
    drawCircle(Arcade.Green.darken(0.1f), size * 0.36f, center)
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = Arcade.Green.darken(0.25f),
            topLeft = Offset(center.x + side * size * 0.14f - size * 0.14f, center.y + size * 0.1f),
            size = Size(size * 0.28f, size * 0.34f),
        )
    }
    listOf(-1f, 1f).forEach { side ->
        val eye = Offset(center.x + side * size * 0.16f, center.y - size * 0.24f)
        drawCircle(Arcade.Lime, size * 0.13f, eye)
        drawCircle(Color.Black, size * 0.06f, eye)
    }
}

/** A star field, seeded by index so it doesn't shimmer between frames. */
fun DrawScope.drawStarField(count: Int, seed: Int = 0, scroll: Float = 0f) {
    repeat(count) { index ->
        val n = (index * 2654435761L + seed).toInt()
        val x = ((n ushr 8) % 1000) / 1000f * size.width
        val baseY = ((n ushr 18) % 1000) / 1000f * size.height
        val y = (baseY + scroll * size.height) % size.height
        val radius = if (index % 5 == 0) 2.4f else 1.3f
        drawCircle(Color.White.copy(alpha = if (index % 3 == 0) 0.55f else 0.28f), radius, Offset(x, y))
    }
}

/** Convenience for a rotated point on a circle - used by anything that aims. */
fun pointOnCircle(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        center.x + radius * cos(radians).toFloat(),
        center.y + radius * sin(radians).toFloat(),
    )
}

fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
