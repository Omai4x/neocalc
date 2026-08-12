package com.omai.neocalc.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Which control cluster a game wants under the board. */
enum class Pad {
    /** All four directions. Mazes, snakes, sliding puzzles. */
    Directional,

    /** Left and right only, sized to fill the width. Paddles, lanes, shooters. */
    Horizontal,

    /** One big button. Flappy-style games, jumpers, timing games. */
    Action,

    /** Left, right, and a fire button between them. */
    HorizontalAction,

    /** Rotate left/right plus thrust and fire. */
    Ship,

    /** Nothing: the board itself is the control. Board games, taps, puzzles. */
    Board,
}

/** Labels for [Pad.Action] and the fire key, so a game can name its own verb. */
data class PadLabels(val action: String = "TAP", val fire: String = "FIRE")

/**
 * Chrome shared by every game: the top bar with the close button always in the
 * same place, the score line, the board, the overlays, and the controls.
 *
 * Games supply only their board and their reactions - nothing here knows what
 * any particular game is.
 */
@Composable
fun GameShell(
    title: String,
    score: Int,
    best: Int,
    status: GameStatus?,
    pad: Pad,
    onExit: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    extra: String? = null,
    countdown: Int = 0,
    paused: Boolean = false,
    onPause: (() -> Unit)? = null,
    onDirection: (Direction) -> Unit = {},
    onAction: () -> Unit = {},
    onSecondary: () -> Unit = {},
    /** An extra button beside Restart, for a game-specific verb like Undo. */
    extraAction: Pair<String, () -> Unit>? = null,
    labels: PadLabels = PadLabels(),
    aspect: Float = 1f,
    board: @Composable (Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        ArcadeTopBar(title = title, onExit = onExit)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("SCORE", score.toString())
            StatChip("BEST", best.toString())
            extra?.let { StatChip(it.substringBefore(' '), it.substringAfter(' ')) }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            board(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(18.dp)),
            )

            // A round only begins after the count, so a player who just tapped
            // "play" is never killed before they have looked at the board.
            if (countdown > 0 && status == null) {
                CountdownOverlay(countdown)
            } else {
                status?.let { StatusOverlay(status = it, onRestart = onRestart) }
            }
        }

        Spacer(Modifier.height(12.dp))

        ControlDeck(
            pad = pad,
            labels = labels,
            onDirection = onDirection,
            onAction = onAction,
            onSecondary = onSecondary,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallAction("Restart", Modifier.weight(1f), onRestart)
            extraAction?.let { (label, action) ->
                SmallAction(label, Modifier.weight(1f), action)
            }
            onPause?.let {
                SmallAction(if (paused) "Resume" else "Pause", Modifier.weight(1f), it)
            }
        }
    }
}

/**
 * One top bar for the whole arcade - hub, intro and games all use it, so the
 * close button never moves and never sits on top of anything.
 */
@Composable
fun ArcadeTopBar(
    title: String,
    onExit: () -> Unit,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        onBack?.let {
            IconKey(glyph = "‹", description = "Back", onClick = it)
            Spacer(Modifier.width(2.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (onBack == null) 8.dp else 0.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconKey(glyph = "✕", description = "Close the arcade", onClick = onExit)
    }
}

@Composable
private fun IconKey(glyph: String, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceVariant,
        contentColor = scheme.onSurface,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = glyph, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun CountdownOverlay(count: Int) {
    val scale by animateFloatAsState(targetValue = 1f, label = "countdown")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.size(104.dp).scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (count > 0) count.toString() else "GO",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusOverlay(status: GameStatus, onRestart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (headline, detail) = when (status) {
        is GameStatus.Over -> status.headline to status.detail
        is GameStatus.Won -> status.headline to status.detail
        GameStatus.Paused -> "Paused" to "Take your time."
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (status is GameStatus.Won) scheme.primary else scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (status != GameStatus.Paused) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    onClick = onRestart,
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                ) {
                    Text(
                        text = "Play again",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Controls

/** Big by design: these are thumb targets on a phone held one-handed. */
private val PAD_KEY = 74.dp
private val PAD_TALL = 66.dp

@Composable
private fun ControlDeck(
    pad: Pad,
    labels: PadLabels,
    onDirection: (Direction) -> Unit,
    onAction: () -> Unit,
    onSecondary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (pad) {
            Pad.Directional -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PadKey("▲", "Up", Modifier.size(PAD_KEY)) { onDirection(Direction.Up) }
                Row(horizontalArrangement = Arrangement.spacedBy(PAD_KEY)) {
                    PadKey("◀", "Left", Modifier.size(PAD_KEY)) { onDirection(Direction.Left) }
                    PadKey("▶", "Right", Modifier.size(PAD_KEY)) { onDirection(Direction.Right) }
                }
                PadKey("▼", "Down", Modifier.size(PAD_KEY)) { onDirection(Direction.Down) }
            }

            Pad.Horizontal -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PadKey("◀", "Left", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Left)
                }
                PadKey("▶", "Right", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Right)
                }
            }

            Pad.HorizontalAction -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PadKey("◀", "Left", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Left)
                }
                PadKey(labels.fire, labels.fire, Modifier.weight(1.2f).height(PAD_TALL), accent = true) {
                    onAction()
                }
                PadKey("▶", "Right", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Right)
                }
            }

            Pad.Action -> PadKey(
                labels.action,
                labels.action,
                Modifier.fillMaxWidth().height(PAD_TALL),
                accent = true,
            ) { onAction() }

            Pad.Ship -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PadKey("↺", "Turn left", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Left)
                }
                PadKey("↻", "Turn right", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Right)
                }
                PadKey("▲", "Thrust", Modifier.weight(1f).height(PAD_TALL)) {
                    onDirection(Direction.Up)
                }
                PadKey(labels.fire, labels.fire, Modifier.weight(1.1f).height(PAD_TALL), accent = true) {
                    onAction()
                }
            }

            Pad.Board -> Text(
                text = "Play on the board",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun PadKey(
    glyph: String,
    description: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            // A control you can feel is one you can use without looking at it,
            // which is exactly the situation on a game board.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        color = if (accent) scheme.primary else scheme.surfaceVariant,
        contentColor = if (accent) scheme.onPrimary else scheme.primary,
        border = if (accent) null else BorderStroke(1.dp, scheme.outline),
        modifier = modifier
            .padding(3.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = modifier.heightIn(min = 46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ------------------------------------------------------------------ Input helpers

/**
 * Fires once per gesture, at the moment the drag passes the threshold, rather
 * than continuously: a single flick should be a single turn.
 */
fun Modifier.swipeDirections(onDirection: (Direction) -> Unit): Modifier =
    this.pointerInput(Unit) {
        var dx = 0f
        var dy = 0f
        var fired = false
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
                fired = false
            },
            onDrag = { change, amount ->
                change.consume()
                dx += amount.x
                dy += amount.y
                if (!fired) {
                    directionOf(dx, dy, threshold = 20.dp.toPx())?.let {
                        onDirection(it)
                        fired = true
                    }
                }
            },
        )
    }

/** Reports which cell of a [columns] x [rows] grid was tapped. */
fun Modifier.tapCells(columns: Int, rows: Int, onTap: (Cell) -> Unit): Modifier =
    this.pointerInput(columns, rows) {
        detectTapGestures { offset ->
            val x = (offset.x / (size.width.toFloat() / columns)).toInt().coerceIn(0, columns - 1)
            val y = (offset.y / (size.height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            onTap(Cell(x, y))
        }
    }

/** Long-press variant, for the games where a second action needs a second gesture. */
fun Modifier.tapCells(
    columns: Int,
    rows: Int,
    onTap: (Cell) -> Unit,
    onLongPress: (Cell) -> Unit,
): Modifier = this.pointerInput(columns, rows) {
    fun cellAt(offset: Offset): Cell {
        val x = (offset.x / (size.width.toFloat() / columns)).toInt().coerceIn(0, columns - 1)
        val y = (offset.y / (size.height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
        return Cell(x, y)
    }
    detectTapGestures(
        onTap = { onTap(cellAt(it)) },
        onLongPress = { onLongPress(cellAt(it)) },
    )
}

/** Horizontal drag as a normalised 0..1 position - paddles and lanes. */
fun Modifier.dragFraction(onFraction: (Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectDragGestures { change, _ ->
            change.consume()
            onFraction((change.position.x / size.width).coerceIn(0f, 1f))
        }
    }

/** A tap anywhere on the board - flappy-style games. */
fun Modifier.tapAnywhere(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onTap() } }

// ------------------------------------------------------------------ Grace period

/**
 * The "ready?" count every game runs through after starting or restarting.
 *
 * Being dropped straight into a moving board is the single most common way an
 * arcade game feels unfair; three seconds costs nothing and removes it.
 */
@Composable
fun rememberCountdown(restartKey: Int, seconds: Int = 3): Int {
    var remaining by remember(restartKey) { mutableIntStateOf(seconds) }
    LaunchedEffect(restartKey) {
        remaining = seconds
        while (remaining > 0) {
            delay(700)
            remaining -= 1
        }
    }
    return remaining
}

/** A flat board fill, so every game starts from the same visual ground. */
@Composable
fun boardBackground(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

/** Convenience for the many games that draw on a plain canvas. */
@Composable
fun GameCanvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val background = boardBackground()
    Canvas(modifier = modifier) {
        drawRect(color = background, size = size)
        onDraw()
    }
}
