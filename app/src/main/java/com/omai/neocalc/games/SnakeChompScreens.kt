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
import androidx.compose.ui.platform.LocalContext

/**
 * Screens for the two original arcade games. The rules live in SnakeGame.kt and
 * ChompGame.kt; everything here is presentation, input and the game loop.
 */

@Composable
fun SnakeGameScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(SnakeState.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "snake")) }
    val countdown = rememberCountdown(round)

    TickLoop(
        running = state.alive && !paused && countdown == 0,
        intervalMs = { state.intervalMs },
    ) { state = state.step() }

    // Recording on death rather than on every point keeps the write off the
    // game loop, where a disk hit would show up as a stutter.
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
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = { state = state.turn(it) },
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections { state = state.turn(it) }) {
            val cell = size.width / state.gridWidth

            // A faint grid gives the board a sense of scale without competing
            // with the pieces on it.
            for (i in 1 until state.gridWidth) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(i * cell, 0f),
                    end = Offset(i * cell, size.height),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, i * cell),
                    end = Offset(size.width, i * cell),
                    strokeWidth = 1f,
                )
            }

            // The apple, with a stalk - a red square reads as nothing in particular.
            val apple = Offset(state.food.x * cell + cell / 2, state.food.y * cell + cell / 2)
            drawGlossyBall(apple, cell * 0.32f, Arcade.Red)
            drawRect(
                color = Arcade.Green.darken(0.2f),
                topLeft = Offset(apple.x - cell * 0.04f, apple.y - cell * 0.46f),
                size = Size(cell * 0.08f, cell * 0.18f),
            )

            state.body.forEachIndexed { index, part ->
                val head = index == 0
                val fade = (0.9f - index.toFloat() / (state.body.size + 2) * 0.5f).coerceIn(0.4f, 1f)
                drawBevelBlock(
                    topLeft = Offset(part.x * cell + 1.5f, part.y * cell + 1.5f),
                    size = Size(cell - 3f, cell - 3f),
                    color = if (head) Arcade.Lime else Arcade.Green.copy(alpha = fade),
                    radius = cell * (if (head) 0.4f else 0.28f),
                )
                if (head) {
                    // Eyes on the leading face, so you can see which way it goes.
                    val d = state.direction
                    val centre = Offset(part.x * cell + cell / 2, part.y * cell + cell / 2)
                    listOf(-1, 1).forEach { side ->
                        val eye = Offset(
                            centre.x + d.dx * cell * 0.22f + side * d.dy * cell * 0.2f,
                            centre.y + d.dy * cell * 0.22f + side * d.dx * cell * 0.2f,
                        )
                        drawCircle(Color.White, cell * 0.11f, eye)
                        drawCircle(Color.Black, cell * 0.055f, eye)
                    }
                }
            }
        }
    }
}

@Composable
fun ChompGameScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(ChompState.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "chomp")) }
    val countdown = rememberCountdown(round)

    TickLoop(
        running = state.playing && !paused && countdown == 0,
        intervalMs = { ChompState.STEP_INTERVAL_MS },
    ) { state = state.step() }

    LaunchedEffect(state.outcome) {
        if (!state.playing) {
            ArcadeScores.submit(context, "chomp", state.score)
            best = ArcadeScores.best(context, "chomp")
        }
    }

    val ghostColors = listOf(Arcade.Red, Arcade.Sky, Arcade.Coral)

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
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = { state = state.turn(it) },
        aspect = ChompState.WIDTH.toFloat() / ChompState.HEIGHT,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections { state = state.turn(it) }) {
            val cell = size.width / ChompState.WIDTH

            // Walls are drawn as connected pipes: a rounded block plus a bar
            // towards each neighbouring wall, which is what makes a maze look
            // built rather than tiled.
            ChompState.MAZE.forEachIndexed { y, row ->
                row.forEachIndexed { x, symbol ->
                    if (symbol != '#') return@forEachIndexed
                    val left = x * cell
                    val top = y * cell
                    val inset = cell * 0.22f
                    drawRoundRect(
                        color = Arcade.Blue.copy(alpha = 0.75f),
                        topLeft = Offset(left + inset, top + inset),
                        size = Size(cell - inset * 2, cell - inset * 2),
                        cornerRadius = CornerRadius(cell * 0.16f),
                    )
                    Direction.entries.forEach { direction ->
                        if (!ChompState.isWall(Cell(x, y).move(direction))) return@forEach
                        drawRect(
                            color = Arcade.Blue.copy(alpha = 0.75f),
                            topLeft = Offset(
                                left + inset + direction.dx * cell * 0.5f,
                                top + inset + direction.dy * cell * 0.5f,
                            ),
                            size = Size(cell - inset * 2, cell - inset * 2),
                        )
                    }
                }
            }

            state.pellets.forEach { pellet ->
                drawCircle(
                    color = Arcade.Cloud,
                    radius = cell * 0.09f,
                    center = Offset(pellet.x * cell + cell / 2, pellet.y * cell + cell / 2),
                )
            }
            state.powers.forEach { power ->
                val centre = Offset(power.x * cell + cell / 2, power.y * cell + cell / 2)
                // Pulsing, so the thing that changes the game is the thing that
                // draws the eye.
                val pulse = if (state.tick % 6 < 3) 0.26f else 0.20f
                drawCircle(Arcade.Amber.copy(alpha = 0.35f), cell * (pulse + 0.1f), centre)
                drawCircle(Arcade.Amber, cell * pulse, centre)
            }

            val facing = when (state.direction) {
                Direction.Right -> 0f
                Direction.Down -> 90f
                Direction.Left -> 180f
                Direction.Up -> 270f
            }
            drawChomper(
                topLeft = Offset(state.player.x * cell, state.player.y * cell),
                size = cell,
                facingDegrees = facing,
                open = if (state.tick % 2 == 0) 62f else 14f,
            )

            state.ghosts.forEach { ghost ->
                val scared = ghost.frightened > 0
                val color = when {
                    scared && ghost.frightened < 8 && state.tick % 2 == 0 -> Arcade.Cloud
                    scared -> Arcade.Indigo
                    else -> ghostColors[ghost.colorIndex % ghostColors.size]
                }
                drawGhost(
                    topLeft = Offset(ghost.cell.x * cell, ghost.cell.y * cell),
                    size = cell,
                    color = color,
                    look = ghost.direction,
                    scared = scared,
                )
            }
        }
    }
}
