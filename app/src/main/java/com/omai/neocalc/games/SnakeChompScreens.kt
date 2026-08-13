package com.omai.neocalc.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

/**
 * Snake and Chomp.
 *
 * Both are drawn to look like the machines they come from: Snake as the solid
 * blocks of a Nokia screen, Chomp as the blue double-line maze, yellow chomper
 * and four coloured ghosts of the 1980 cabinet.
 */

@Composable
fun SnakeGameScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(SnakeState.new()) }
    var previous by remember(round) { mutableStateOf(state) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "snake")) }
    val countdown = rememberCountdown(round)
    val running = state.alive && !paused && countdown == 0
    var ticks by remember(round) { mutableIntStateOf(0) }

    TickLoop(running = running, intervalMs = { state.intervalMs }) {
        previous = state
        ticks++
        // The stick is read on the tick rather than on the gesture: holding a
        // direction keeps steering, and a turn entered between ticks is applied
        // on the next one instead of being dropped.
        controls.direction?.let { state = state.turn(it) }
        state = state.step()
    }

    val progress = rememberTickProgress(ticks, state.intervalMs, running)

    LaunchedEffect(state.alive) {
        if (!state.alive) {
            ArcadeScores.submit(context, "snake", state.score)
            best = ArcadeScores.best(context, "snake")
        }
    }

    GameShell(
        title = "Snake",
        score = state.score,
        best = best,
        status = when {
            !state.alive -> GameStatus.Over("Game over", "You reached ${state.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        controls = Controls.Joystick,
        state = controls,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
    ) { boardModifier ->
        GameCanvas(boardModifier.boardStick(controls) { state = state.turn(it) }) {
            val cell = size.width / state.gridWidth

            // The Nokia screen: a solid ground with the pixels drawn dark on it.
            drawRect(color = Color(0xFF9BB53F), size = size)
            for (i in 0..state.gridWidth) {
                drawLine(
                    Color(0xFF8AA436),
                    Offset(i * cell, 0f), Offset(i * cell, size.height), 1f,
                )
                drawLine(
                    Color(0xFF8AA436),
                    Offset(0f, i * cell), Offset(size.width, i * cell), 1f,
                )
            }

            val ink = Color(0xFF1F2A12)

            // Food is the classic hollow square with a dot in it.
            val food = Offset(state.food.x * cell, state.food.y * cell)
            drawRect(ink, Offset(food.x + cell * 0.14f, food.y + cell * 0.14f),
                Size(cell * 0.72f, cell * 0.72f), style = Stroke(width = cell * 0.14f))
            drawRect(ink, Offset(food.x + cell * 0.38f, food.y + cell * 0.38f),
                Size(cell * 0.24f, cell * 0.24f))

            // Body segments are squares with a light gap, like an LCD grid.
            state.body.forEachIndexed { index, part ->
                val inset = if (index == 0) cell * 0.06f else cell * 0.12f
                // Each segment slides from where the one in front of it was,
                // which is what makes a snake flow rather than shuffle.
                val was = previous.body.getOrNull(index) ?: part
                val (bx, by) = lerpCell(was, part, progress.value)
                drawRect(
                    color = ink,
                    topLeft = Offset(bx * cell + inset, by * cell + inset),
                    size = Size(cell - inset * 2, cell - inset * 2),
                )
                if (index == 0) {
                    // Eyes as two cut-out pixels, facing the way it travels.
                    val d = state.direction
                    val c = Offset(bx * cell + cell / 2, by * cell + cell / 2)
                    listOf(-1, 1).forEach { side ->
                        drawRect(
                            color = Color(0xFF9BB53F),
                            topLeft = Offset(
                                c.x + d.dx * cell * 0.18f + side * d.dy * cell * 0.2f - cell * 0.07f,
                                c.y + d.dy * cell * 0.18f + side * d.dx * cell * 0.2f - cell * 0.07f,
                            ),
                            size = Size(cell * 0.14f, cell * 0.14f),
                        )
                    }
                } else {
                    drawRect(
                        color = Color(0xFF9BB53F).copy(alpha = 0.22f),
                        topLeft = Offset(bx * cell + cell * 0.32f, by * cell + cell * 0.32f),
                        size = Size(cell * 0.36f, cell * 0.36f),
                    )
                }
            }
        }
    }
}

@Composable
fun ChompGameScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(ChompState.new()) }
    // The frame before this one, so everything can be drawn on its way from
    // there to here rather than jumping a whole cell at a time.
    var previous by remember(round) { mutableStateOf(state) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "chomp")) }
    val countdown = rememberCountdown(round)
    val running = state.playing && !paused && countdown == 0

    TickLoop(running = running, intervalMs = { ChompState.STEP_INTERVAL_MS }) {
        controls.direction?.let { state = state.turn(it) }
        previous = state
        state = state.step()
    }

    val progress = rememberTickProgress(state.tick, ChompState.STEP_INTERVAL_MS, running)

    LaunchedEffect(state.outcome) {
        if (!state.playing) {
            ArcadeScores.submit(context, "chomp", state.score)
            best = ArcadeScores.best(context, "chomp")
        }
    }

    // Blinky, Pinky, Inky, Clyde. The colours are the point of the characters.
    val ghostColors = listOf(
        Color(0xFFFF0000), Color(0xFFFFB8FF), Color(0xFF00FFFF), Color(0xFFFFB852),
    )

    GameShell(
        title = "Chomp",
        score = state.score,
        best = best,
        extra = "LIVES ${state.lives.coerceAtLeast(0)}",
        status = when {
            state.outcome == ChompOutcome.Won ->
                GameStatus.Won("Maze cleared!", "Final score ${state.score}.")

            state.outcome == ChompOutcome.Lost ->
                GameStatus.Over("Game over", "You scored ${state.score}.")

            paused -> GameStatus.Paused
            else -> null
        },
        controls = Controls.Joystick,
        state = controls,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        aspect = ChompState.WIDTH.toFloat() / ChompState.HEIGHT,
    ) { boardModifier ->
        GameCanvas(boardModifier.boardStick(controls) { state = state.turn(it) }) {
            val cell = size.width / ChompState.WIDTH

            // The cabinet's maze is black, and the walls are a blue outline
            // rather than a filled block. Drawing the outline is what makes it
            // read as the original rather than as a tile map.
            drawRect(Color(0xFF000000), size = size)
            val wall = Color(0xFF2121DE)
            val thickness = cell * 0.14f

            ChompState.MAZE.forEachIndexed { y, row ->
                row.forEachIndexed { x, symbol ->
                    if (symbol != '#') return@forEachIndexed
                    val left = x * cell
                    val top = y * cell
                    // An edge is drawn only where the neighbour is open, so
                    // adjacent wall tiles join into one continuous corridor line.
                    Direction.entries.forEach { d ->
                        if (ChompState.isWall(Cell(x, y).move(d))) return@forEach
                        val inset = cell * 0.2f
                        when (d) {
                            Direction.Up -> drawLine(wall,
                                Offset(left + inset, top + inset),
                                Offset(left + cell - inset, top + inset), thickness)
                            Direction.Down -> drawLine(wall,
                                Offset(left + inset, top + cell - inset),
                                Offset(left + cell - inset, top + cell - inset), thickness)
                            Direction.Left -> drawLine(wall,
                                Offset(left + inset, top + inset),
                                Offset(left + inset, top + cell - inset), thickness)
                            Direction.Right -> drawLine(wall,
                                Offset(left + cell - inset, top + inset),
                                Offset(left + cell - inset, top + cell - inset), thickness)
                        }
                    }
                }
            }

            val pelletColor = Color(0xFFFFB8AE)
            state.pellets.forEach { p ->
                drawRect(
                    color = pelletColor,
                    topLeft = Offset(p.x * cell + cell * 0.42f, p.y * cell + cell * 0.42f),
                    size = Size(cell * 0.16f, cell * 0.16f),
                )
            }
            state.powers.forEach { p ->
                // Power pellets blink, as they do in the cabinet.
                if (state.tick % 8 < 5) {
                    drawCircle(
                        pelletColor, cell * 0.28f,
                        Offset(p.x * cell + cell / 2, p.y * cell + cell / 2),
                    )
                }
            }

            val facing = when (state.direction) {
                Direction.Right -> 0f
                Direction.Down -> 90f
                Direction.Left -> 180f
                Direction.Up -> 270f
            }
            // Three-frame mouth cycle: closed, half, wide, back again.
            val open = when (state.tick % 4) {
                0 -> 6f
                1, 3 -> 45f
                else -> 80f
            }
            val (px, py) = lerpCell(previous.player, state.player, progress.value)
            drawChomper(
                topLeft = Offset(px * cell, py * cell),
                size = cell,
                facingDegrees = facing,
                open = open,
                color = Color(0xFFFFFF00),
            )

            state.ghosts.forEachIndexed { index, ghost ->
                val was = previous.ghosts.getOrNull(index)?.cell ?: ghost.cell
                val (gx, gy) = lerpCell(was, ghost.cell, progress.value)
                val scared = ghost.frightened > 0
                val color = when {
                    scared && ghost.frightened < 8 && state.tick % 2 == 0 -> Color(0xFFFFFFFF)
                    scared -> Color(0xFF2121DE)
                    else -> ghostColors[ghost.colorIndex % ghostColors.size]
                }
                drawGhost(
                    topLeft = Offset(gx * cell, gy * cell),
                    size = cell,
                    color = color,
                    look = ghost.direction,
                    scared = scared,
                )
            }
        }
    }
}
