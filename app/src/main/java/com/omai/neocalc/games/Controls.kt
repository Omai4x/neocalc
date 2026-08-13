package com.omai.neocalc.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Live control state, read by a game's loop rather than pushed at it.
 *
 * The old pad was a row of Surfaces with onClick, which meant one discrete event
 * per tap and nothing at all while a finger was held down. That is why holding
 * left did not steer: there was no "held" to read. Everything here is a value
 * that stays true for as long as the finger is on the control, so a game can ask
 * "where is the stick now" on the frame it needs to know.
 */
class ControlState {
    /** The stick as a unit vector. Zero when centred. */
    var vector by mutableStateOf(Offset.Zero)

    /** The stick snapped to four directions, or null inside the dead zone. */
    var direction by mutableStateOf<Direction?>(null)

    /** Rail position, 0 at the left edge and 1 at the right. */
    var rail by mutableStateOf(0.5f)

    /** True for as long as the fire button is held. */
    var firing by mutableStateOf(false)

    /** True for as long as thrust is held. */
    var thrusting by mutableStateOf(false)

    /** Rotation being applied this frame: -1, 0 or 1. */
    var turning by mutableStateOf(0)

    val magnitude: Float get() = hypot(vector.x, vector.y)
}

/** Which control cluster a game shows. */
enum class Controls {
    /** Analogue stick. Snake, Chomp, Tron, anything that steers. */
    Joystick,

    /** Stick plus a fire button. */
    JoystickFire,

    /** A horizontal rail. Paddles and lanes: absolute position, not nudges. */
    Rail,

    /** Rail plus fire. Invaders. */
    RailFire,

    /** Rotate, thrust, fire. Asteroids. */
    Ship,

    /** One big button, and the board is a button too. Flap, Copter, Stack. */
    Action,

    /** Four discrete keys, for games where a step is a move: 2048, sliding. */
    Steps,

    /** No controls: the board itself is the input. */
    Board,
}

/** Labels so a game can name its own verbs. */
data class ControlLabels(val action: String = "TAP", val fire: String = "FIRE")

private val DEAD_ZONE = 0.22f

/**
 * An analogue stick.
 *
 * The knob follows the finger anywhere inside the base and springs back on
 * release. Touching down anywhere in the pad re-centres the stick under the
 * finger, so there is no hunting for the middle before you can steer - the
 * thing every on-screen stick gets wrong.
 */
@Composable
fun Joystick(
    state: ControlState,
    modifier: Modifier = Modifier,
    onDirection: (Direction) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var knob by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var lastDirection by remember { mutableStateOf<Direction?>(null) }

    fun publish(raw: Offset) {
        val limited = if (hypot(raw.x, raw.y) > radius) {
            val scale = radius / hypot(raw.x, raw.y)
            Offset(raw.x * scale, raw.y * scale)
        } else {
            raw
        }
        knob = limited
        val unit = Offset(limited.x / radius, limited.y / radius)
        state.vector = unit
        val next = directionOf(unit.x, unit.y, DEAD_ZONE)
        state.direction = next
        if (next != null && next != lastDirection) {
            // A tick when the stick crosses into a new direction is the only
            // feedback a glass stick can give that a physical one gives for free.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastDirection = next
            onDirection(next)
        }
        if (next == null) lastDirection = null
    }

    fun release() {
        knob = Offset.Zero
        state.vector = Offset.Zero
        state.direction = null
        lastDirection = null
    }

    Box(
        modifier = modifier
            .size(148.dp)
            .semantics { contentDescription = "Joystick" }
            .pointerInput(Unit) {
                radius = minOf(size.width, size.height) / 2f * 0.72f
                origin = Offset(size.width / 2f, size.height / 2f)
                detectDragGestures(
                    onDragStart = { start -> publish(start - origin) },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                    onDrag = { change, _ ->
                        change.consume()
                        publish(change.position - origin)
                    },
                )
            }
            .pointerInput(Unit) {
                // A tap without a drag still counts: a quick flick in one
                // direction should register rather than being swallowed.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    publish(down.position - Offset(size.width / 2f, size.height / 2f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension / 2f * 0.92f
            drawCircle(scheme.surfaceVariant, base, centre)
            drawCircle(scheme.outline, base, centre, style = Stroke(width = 2f))
            // Four faint notches, so the axes are discoverable at a glance.
            Direction.entries.forEach { d ->
                drawCircle(
                    color = scheme.onSurfaceVariant.copy(alpha = 0.25f),
                    radius = base * 0.055f,
                    center = Offset(centre.x + d.dx * base * 0.72f, centre.y + d.dy * base * 0.72f),
                )
            }
            val knobCentre = centre + knob
            drawCircle(scheme.primary.copy(alpha = 0.22f), base * 0.44f, knobCentre)
            drawCircle(scheme.primary, base * 0.34f, knobCentre)
            drawCircle(Color.White.copy(alpha = 0.35f), base * 0.12f,
                Offset(knobCentre.x - base * 0.1f, knobCentre.y - base * 0.1f))
        }
    }
}

/**
 * A horizontal rail for paddles and lanes.
 *
 * Absolute rather than incremental: the paddle goes where your thumb is, which
 * is the only way a paddle game feels right. Tapping anywhere on the rail jumps
 * to that spot instead of ignoring the touch.
 */
@Composable
fun Rail(
    state: ControlState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceVariant)
            .semantics { contentDescription = "Slide to move" }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    state.rail = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    state.rail = (down.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2
            drawLine(
                color = scheme.onSurfaceVariant.copy(alpha = 0.3f),
                start = Offset(size.height * 0.4f, y),
                end = Offset(size.width - size.height * 0.4f, y),
                strokeWidth = 3f,
            )
            val x = (size.height * 0.4f + state.rail * (size.width - size.height * 0.8f))
            drawCircle(scheme.primary.copy(alpha = 0.2f), size.height * 0.42f, Offset(x, y))
            drawRoundRect(
                color = scheme.primary,
                topLeft = Offset(x - size.height * 0.3f, y - size.height * 0.14f),
                size = androidx.compose.ui.geometry.Size(size.height * 0.6f, size.height * 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.14f),
            )
        }
    }
}

/**
 * A button that reports press and release, and optionally repeats while held.
 *
 * The old pad fired once per tap and nothing while held, which made every game
 * that needs sustained input feel unresponsive.
 */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    repeatMs: Long? = null,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var held by remember { mutableStateOf(false) }

    if (repeatMs != null) {
        LaunchedEffect(held) {
            if (!held) return@LaunchedEffect
            onPress()
            // A short grace before repeating, so a single tap is one move and
            // holding is a stream, exactly like a keyboard.
            delay(260)
            while (held) {
                onPress()
                delay(repeatMs)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    held && accent -> scheme.primary.copy(alpha = 0.82f)
                    accent -> scheme.primary
                    held -> scheme.primary.copy(alpha = 0.22f)
                    else -> scheme.surfaceVariant
                },
            )
            .semantics { contentDescription = label }
            .pointerInput(repeatMs) {
                awaitEachGesture {
                    awaitFirstDown()
                    held = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (repeatMs == null) onPress()
                    // Waits for the finger to lift or leave, so sliding off the
                    // button releases it rather than sticking on.
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    held = false
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) scheme.onPrimary else scheme.primary,
        )
    }
}

/** The control deck for a game, chosen by its [Controls] scheme. */
@Composable
fun ControlDeck(
    controls: Controls,
    state: ControlState,
    labels: ControlLabels,
    modifier: Modifier = Modifier,
    onStep: (Direction) -> Unit = {},
    onAction: () -> Unit = {},
) {
    val tall = 68.dp
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (controls) {
            Controls.Joystick -> Joystick(state)

            Controls.JoystickFire -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Joystick(state)
                Spacer(Modifier.weight(1f))
                HoldButton(
                    label = labels.fire,
                    accent = true,
                    repeatMs = 220,
                    onPress = onAction,
                    modifier = Modifier.size(104.dp).clip(CircleShape),
                )
            }

            Controls.Rail -> Rail(state)

            Controls.RailFire -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Rail(state, modifier = Modifier.weight(1f))
                HoldButton(
                    label = labels.fire,
                    accent = true,
                    repeatMs = 260,
                    onPress = onAction,
                    modifier = Modifier.size(84.dp).clip(CircleShape),
                )
            }

            Controls.Ship -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HoldButton("↺", Modifier.weight(1f).height(tall),
                    onPress = { state.turning = -1 }, onRelease = { state.turning = 0 })
                HoldButton("↻", Modifier.weight(1f).height(tall),
                    onPress = { state.turning = 1 }, onRelease = { state.turning = 0 })
                HoldButton("▲", Modifier.weight(1f).height(tall),
                    onPress = { state.thrusting = true }, onRelease = { state.thrusting = false })
                HoldButton(labels.fire, Modifier.weight(1.2f).height(tall), accent = true,
                    repeatMs = 240, onPress = onAction)
            }

            Controls.Action -> HoldButton(
                label = labels.action,
                accent = true,
                modifier = Modifier.fillMaxWidth().height(tall),
                onPress = { state.firing = true; onAction() },
                onRelease = { state.firing = false },
            )

            Controls.Steps -> Box {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HoldButton("▲", Modifier.size(74.dp), repeatMs = 140) { onStep(Direction.Up) }
                    Row(horizontalArrangement = Arrangement.spacedBy(74.dp)) {
                        HoldButton("◀", Modifier.size(74.dp), repeatMs = 140) { onStep(Direction.Left) }
                        HoldButton("▶", Modifier.size(74.dp), repeatMs = 140) { onStep(Direction.Right) }
                    }
                    HoldButton("▼", Modifier.size(74.dp), repeatMs = 140) { onStep(Direction.Down) }
                }
            }

            Controls.Board -> Text(
                text = "Play on the board",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

/** Drag anywhere on the board and the rail follows, for paddle games. */
fun Modifier.boardRail(state: ControlState): Modifier = this
    .pointerInput(Unit) {
        detectDragGestures { change, _ ->
            change.consume()
            state.rail = (change.position.x / size.width).coerceIn(0f, 1f)
        }
    }

/** Steer by dragging on the board itself, as well as with the stick. */
fun Modifier.boardStick(state: ControlState, onDirection: (Direction) -> Unit): Modifier = this
    .pointerInput(Unit) {
        var start = Offset.Zero
        detectDragGestures(
            onDragStart = { start = it },
            onDragEnd = { state.direction = null; state.vector = Offset.Zero },
            onDrag = { change, _ ->
                change.consume()
                val delta = change.position - start
                val length = hypot(delta.x, delta.y).coerceAtLeast(1f)
                state.vector = Offset(delta.x / length, delta.y / length)
                directionOf(delta.x, delta.y, 28f)?.let {
                    if (it != state.direction) {
                        state.direction = it
                        onDirection(it)
                    }
                }
            },
        )
    }

/** Rounds a stick vector to the nearest of eight compass points. */
fun Offset.toEightWay(): Pair<Int, Int> =
    if (abs(x) < 0.001f && abs(y) < 0.001f) 0 to 0
    else (x.roundToInt().coerceIn(-1, 1)) to (y.roundToInt().coerceIn(-1, 1))

/**
 * Press and hold anywhere on the board to rise, release to fall. The copter
 * needs a held state, not a tap: tapping made it a different, worse game.
 */
fun Modifier.holdToRise(onHold: (Boolean) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown()
        onHold(true)
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.all { !it.pressed }) break
        }
        onHold(false)
    }
}
