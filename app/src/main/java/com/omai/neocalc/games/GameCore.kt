package com.omai.neocalc.games

import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** A cell on a game grid. Integer coordinates: every tile game here uses these. */
data class Cell(val x: Int, val y: Int) {
    fun move(direction: Direction) = Cell(x + direction.dx, y + direction.dy)

    /** Manhattan distance - the right metric on a grid with no diagonal moves. */
    fun distanceTo(other: Cell): Int = abs(x - other.x) + abs(y - other.y)
}

enum class Direction(val dx: Int, val dy: Int) {
    Up(0, -1),
    Down(0, 1),
    Left(-1, 0),
    Right(1, 0),
    ;

    fun opposite(): Direction = when (this) {
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
    }
}

/**
 * Which way a drag went. Games read a single direction per gesture rather than
 * tracking the finger, which is what makes a flick feel like a d-pad press.
 */
fun directionOf(dx: Float, dy: Float, threshold: Float = 24f): Direction? = when {
    abs(dx) < threshold && abs(dy) < threshold -> null
    abs(dx) > abs(dy) -> if (dx > 0) Direction.Right else Direction.Left
    else -> if (dy > 0) Direction.Down else Direction.Up
}

/** How a round ended. Drives the overlay the shell paints over the board. */
sealed interface GameStatus {
    data class Over(val headline: String, val detail: String? = null) : GameStatus
    data class Won(val headline: String, val detail: String? = null) : GameStatus
    data object Paused : GameStatus
}

/**
 * Fixed-step loop for tile games: one call to [onTick] every [intervalMs].
 *
 * The interval is read fresh each time round, so a game that speeds up as the
 * player progresses just returns a smaller number - no restarting the loop.
 */
@Composable
fun TickLoop(running: Boolean, intervalMs: () -> Long, onTick: () -> Unit) {
    val tick by rememberUpdatedState(onTick)
    val interval by rememberUpdatedState(intervalMs)
    LaunchedEffect(running) {
        while (isActive && running) {
            delay(interval().coerceAtLeast(16L))
            tick()
        }
    }
}

/**
 * Frame loop for the games with real movement rather than tile steps. Hands the
 * elapsed seconds to the caller so physics is frame-rate independent - the same
 * game plays identically at 60Hz and 120Hz.
 */
@Composable
fun FrameLoop(running: Boolean, onFrame: (dt: Float) -> Unit) {
    val frame by rememberUpdatedState(onFrame)
    LaunchedEffect(running) {
        var last = 0L
        while (isActive && running) {
            withFrameNanos { now ->
                if (last != 0L) {
                    // Clamped: a backgrounded app can return a gap of seconds,
                    // which would teleport everything through walls on resume.
                    frame(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f))
                }
                last = now
            }
        }
    }
}

/**
 * Best scores, kept between launches. Trivial storage on purpose: a hidden
 * arcade does not deserve a database.
 */
object ArcadeScores {

    private const val PREFS = "neocalc.arcade"

    fun best(context: Context, game: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(game, 0)

    /** Returns true when [score] was a new record, so the UI can say so. */
    fun submit(context: Context, game: String, score: Int): Boolean {
        if (score <= best(context, game)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(game, score)
            .apply()
        return true
    }

    /** How many games have ever been finished - the hub's "progress" line. */
    fun played(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.size
}

/**
 * A small fixed palette for the games.
 *
 * The rest of the app draws entirely from the Material scheme, but arcade
 * characters carry meaning in their colour - a red ghost, a yellow player, a
 * green pipe - and remapping those to a theme would make them unreadable.
 */
object Arcade {
    val Yellow = Color(0xFFFFD54F)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF5252)
    val Coral = Color(0xFFFF7043)
    val Pink = Color(0xFFFF4081)
    val Purple = Color(0xFFB388FF)
    val Indigo = Color(0xFF7C4DFF)
    val Blue = Color(0xFF448AFF)
    val Sky = Color(0xFF4FC3F7)
    val Teal = Color(0xFF26C6DA)
    val Green = Color(0xFF66BB6A)
    val Lime = Color(0xFFD4E157)
    val Brown = Color(0xFF8D6E63)
    val Slate = Color(0xFF546E7A)
    val Cloud = Color(0xFFECEFF1)

    /** Distinct, in order, for anything that needs "the next colour". */
    val series = listOf(Red, Amber, Green, Sky, Purple, Pink, Teal, Lime, Blue, Coral)
}

/**
 * How far the current tick has progressed, from 0 at the tick that just fired
 * to 1 at the next one.
 *
 * Tile games move a whole cell at a time, which is correct for the rules and
 * looks like a slideshow on screen. Drawing at the position between the last
 * cell and the next one, using this as the fraction, turns the same logic into
 * continuous motion without touching the logic at all.
 *
 * Reset by passing a [key] that changes on every tick, usually the tick counter.
 */
@Composable
fun rememberTickProgress(key: Any, intervalMs: Long, running: Boolean): State<Float> {
    // Returned as a State, not a Float, on purpose. A Float would be read during
    // composition, so every frame of every animation would recompose the whole
    // screen - which cost 30 to 70 dropped frames a second when measured. Read
    // inside a draw lambda instead, only the drawing is invalidated.
    val progress = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(key, running, intervalMs) {
        if (!running) {
            progress.floatValue = 1f
            return@LaunchedEffect
        }
        progress.floatValue = 0f
        val started = withFrameNanos { it }
        while (progress.floatValue < 1f) {
            withFrameNanos { now ->
                progress.floatValue =
                    ((now - started) / 1_000_000f / intervalMs).coerceIn(0f, 1f)
            }
        }
    }
    return progress
}

/** Linear interpolation between two grid cells, in cell units. */
fun lerpCell(from: Cell, to: Cell, t: Float): Pair<Float, Float> {
    // A wrap across the board is a teleport, not a slide: interpolating it
    // would drag the sprite backwards across the whole screen.
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dy) > 1) return to.x.toFloat() to to.y.toFloat()
    return (from.x + dx * t) to (from.y + dy * t)
}
