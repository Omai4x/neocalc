package com.omai.neocalc.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ------------------------------------------------------------------ Stack

data class Slab(val x: Float, val width: Float)

data class StackWorld(
    val landed: List<Slab> = listOf(Slab(0.3f, 0.4f)),
    val moving: Slab = Slab(0f, 0.4f),
    val direction: Int = 1,
    val score: Int = 0,
    val over: Boolean = false,
) {
    companion object {
        const val ASPECT = 0.75f
        val HEIGHT = 1f / ASPECT
        const val ROW = 0.055f
        const val VISIBLE_ROWS = 12
    }

    val speed: Float get() = (0.42f + score * 0.022f).coerceAtMost(1.15f)

    fun step(dt: Float): StackWorld {
        if (over) return this
        var x = moving.x + direction * speed * dt
        var heading = direction
        if (x < 0f) { x = 0f; heading = 1 }
        if (x + moving.width > 1f) { x = 1f - moving.width; heading = -1 }
        return copy(moving = moving.copy(x = x), direction = heading)
    }

    /**
     * Drops the slab. Overhang is trimmed; a landing within a small tolerance
     * counts as perfect and hands the lost width back - the forgiving version
     * of this game, which is the version people keep playing.
     */
    fun drop(): StackWorld {
        if (over) return this
        val top = landed.last()
        val left = maxOf(moving.x, top.x)
        val right = minOf(moving.x + moving.width, top.x + top.width)
        val overlap = right - left
        if (overlap <= 0f) return copy(over = true)

        val perfect = abs(moving.x - top.x) < 0.015f
        val width = if (perfect) top.width else overlap
        val x = if (perfect) top.x else left
        val slab = Slab(x, width)
        return copy(
            landed = landed + slab,
            moving = Slab(if (direction > 0) 0f else 1f - width, width),
            direction = -direction,
            score = score + if (perfect) 3 else 1,
        )
    }
}

@Composable
fun StackScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(StackWorld()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "stack")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt -> world = world.step(dt) }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "stack", world.score)
            best = ArcadeScores.best(context, "stack")
        }
    }

    GameShell(
        title = "Stack",
        score = world.score,
        best = best,
        status = when {
            world.over -> GameStatus.Over("Missed the tower", "You stacked ${world.landed.size - 1}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Action,
        labels = PadLabels(action = "DROP"),
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onAction = { world = world.drop() },
        aspect = StackWorld.ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.tapAnywhere { world = world.drop() }) {
            val unit = size.width
            val base = size.height - unit * 0.04f

            // Only the top dozen rows are drawn; the tower is conceptually
            // infinite but the screen is not.
            world.landed.takeLast(StackWorld.VISIBLE_ROWS).forEachIndexed { index, slab ->
                val fromTop = world.landed.takeLast(StackWorld.VISIBLE_ROWS).size - index
                drawBevelBlock(
                    Offset(slab.x * unit, base - fromTop * StackWorld.ROW * unit),
                    Size(slab.width * unit, StackWorld.ROW * unit - 2f),
                    Arcade.series[(world.landed.size - fromTop) % Arcade.series.size],
                )
            }
            drawBevelBlock(
                Offset(world.moving.x * unit, base - (StackWorld.VISIBLE_ROWS + 1) * StackWorld.ROW * unit),
                Size(world.moving.width * unit, StackWorld.ROW * unit - 2f),
                Arcade.Cloud,
            )
        }
    }
}

// ------------------------------------------------------------------ Sharpshooter

/** [friendly] targets cost points; [bonus] ones are worth triple. */
data class Target(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val friendly: Boolean,
    val bonus: Boolean,
    val life: Float,
)

@Composable
fun SharpshooterScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var targets by remember(round) { mutableStateOf(listOf<Target>()) }
    var score by remember(round) { mutableIntStateOf(0) }
    var misses by remember(round) { mutableIntStateOf(0) }
    var nextId by remember(round) { mutableIntStateOf(0) }
    var spawnIn by remember(round) { mutableStateOf(0.6f) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "sharpshooter")) }
    val countdown = rememberCountdown(round)
    val over = misses >= 5

    FrameLoop(running = !over && !paused && countdown == 0) { dt ->
        targets = targets.map { it.copy(life = it.life - dt) }
            .filter { target ->
                // A hostile target timing out is a miss; a friendly one is not.
                if (target.life > 0f) true else { if (!target.friendly) misses++; false }
            }
        spawnIn -= dt
        if (spawnIn <= 0f) {
            val difficulty = (score / 60f).coerceAtMost(1f)
            targets = targets + Target(
                id = nextId++,
                x = 0.12f + random.nextFloat() * 0.76f,
                y = 0.12f + random.nextFloat() * 0.76f,
                radius = 0.10f - difficulty * 0.035f,
                friendly = random.nextInt(100) < 18,
                bonus = random.nextInt(100) < 14,
                life = 2.4f - difficulty * 1.1f,
            )
            spawnIn = (0.85f - difficulty * 0.45f)
        }
    }
    LaunchedEffect(over) {
        if (over) {
            ArcadeScores.submit(context, "sharpshooter", score)
            best = ArcadeScores.best(context, "sharpshooter")
        }
    }

    GameShell(
        title = "Sharpshooter",
        score = score,
        best = best,
        extra = "MISSES $misses",
        status = when {
            over -> GameStatus.Over("Too many missed", "You scored $score.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(100, 100) { cell ->
                if (over || countdown > 0) return@tapCells
                val hit = targets.firstOrNull {
                    val dx = it.x - cell.x / 100f
                    val dy = it.y - cell.y / 100f
                    dx * dx + dy * dy < it.radius * it.radius
                }
                when {
                    hit == null -> Unit
                    hit.friendly -> {
                        score = (score - 15).coerceAtLeast(0)
                        targets = targets - hit
                    }

                    else -> {
                        score += if (hit.bonus) 30 else 10
                        targets = targets - hit
                    }
                }
            },
        ) {
            val unit = size.width
            targets.forEach { target ->
                val centre = Offset(target.x * unit, target.y * size.height)
                val radius = target.radius * unit
                val color = when {
                    target.friendly -> Arcade.Sky
                    target.bonus -> Arcade.Amber
                    else -> Arcade.Red
                }
                // Concentric rings, so it reads as a target rather than a dot,
                // and the shrinking outer ring shows the time left.
                drawCircle(color.copy(alpha = 0.25f), radius, centre)
                drawCircle(color, radius * 0.66f, centre)
                drawCircle(Arcade.Cloud, radius * 0.34f, centre)
                drawCircle(color, radius * 0.14f, centre)
                drawCircle(
                    color = Arcade.Cloud.copy(alpha = 0.7f),
                    radius = radius * (0.7f + 0.5f * (target.life / 2.4f).coerceIn(0f, 1f)),
                    center = centre,
                    style = Stroke(width = 2.5f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Whack-a-Mole

@Composable
fun MoleScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var up by remember(round) { mutableStateOf(mapOf<Int, Boolean>()) }
    var score by remember(round) { mutableIntStateOf(0) }
    var timeLeft by remember(round) { mutableStateOf(60f) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "mole")) }
    val countdown = rememberCountdown(round)
    val over = timeLeft <= 0f
    val holes = 9

    FrameLoop(running = !over && !paused && countdown == 0) { dt -> timeLeft -= dt }

    // Moles rise and fall on their own schedule; the pace picks up as the clock
    // runs down, so the last ten seconds are the busy ones.
    LaunchedEffect(round, paused, over, countdown) {
        while (!over && !paused && countdown == 0) {
            val pace = (900L - (60f - timeLeft).toLong() * 8L).coerceAtLeast(320L)
            delay(pace)
            val hole = random.nextInt(holes)
            val golden = random.nextInt(100) < 15
            up = up + (hole to golden)
            delay(pace + 250L)
            up = up - hole
        }
    }

    LaunchedEffect(over) {
        if (over) {
            ArcadeScores.submit(context, "mole", score)
            best = ArcadeScores.best(context, "mole")
        }
    }

    GameShell(
        title = "Whack-a-Mole",
        score = score,
        best = best,
        extra = "TIME ${timeLeft.toInt().coerceAtLeast(0)}",
        status = when {
            over -> GameStatus.Over("Time!", "You scored $score.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(3, 3) { cell ->
                val hole = cell.y * 3 + cell.x
                up[hole]?.let { golden ->
                    score += if (golden) 5 else 1
                    up = up - hole
                }
            },
        ) {
            val cell = size.width / 3
            for (row in 0 until 3) {
                for (column in 0 until 3) {
                    val hole = row * 3 + column
                    val centre = Offset(column * cell + cell / 2, row * cell + cell / 2)
                    // The hole is an ellipse, so the mole can sit "in" it.
                    drawOval(
                        color = Color(0xFF3E2723),
                        topLeft = Offset(centre.x - cell * 0.32f, centre.y - cell * 0.16f),
                        size = Size(cell * 0.64f, cell * 0.34f),
                    )
                    val golden = up[hole] ?: continue
                    val color = if (golden) Arcade.Amber else Arcade.Brown
                    drawCircle(color, cell * 0.24f, Offset(centre.x, centre.y - cell * 0.06f))
                    listOf(-1, 1).forEach { side ->
                        drawCircle(
                            Color.Black,
                            cell * 0.035f,
                            Offset(centre.x + side * cell * 0.09f, centre.y - cell * 0.1f),
                        )
                    }
                    drawOval(
                        color = Arcade.Cloud.copy(alpha = 0.85f),
                        topLeft = Offset(centre.x - cell * 0.07f, centre.y - cell * 0.02f),
                        size = Size(cell * 0.14f, cell * 0.1f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Reflex

private enum class ReflexPhase { Waiting, Ready, Scored, Early }

@Composable
fun ReactionScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var phase by remember(round) { mutableStateOf(ReflexPhase.Waiting) }
    var readyAt by remember(round) { mutableStateOf(0L) }
    var times by remember(round) { mutableStateOf(listOf<Long>()) }
    var lastMs by remember(round) { mutableStateOf(0L) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "reflex")) }
    val attempts = 5
    val done = times.size >= attempts

    // Each round waits a random beat, so the timing can never be anticipated.
    LaunchedEffect(round, times.size, phase) {
        if (phase == ReflexPhase.Waiting && !done) {
            delay(900L + random.nextLong(2200))
            readyAt = System.currentTimeMillis()
            phase = ReflexPhase.Ready
        }
    }

    val average = if (times.isEmpty()) 0 else times.average().toInt()
    // Faster is better, but scores go up, so invert into a friendly number.
    val score = if (times.isEmpty()) 0 else (1200 - average).coerceAtLeast(0)

    LaunchedEffect(done) {
        if (done) {
            ArcadeScores.submit(context, "reflex", score)
            best = ArcadeScores.best(context, "reflex")
        }
    }

    GameShell(
        title = "Reflex",
        score = score,
        best = best,
        extra = "TRY ${(times.size + 1).coerceAtMost(attempts)}/$attempts",
        status = if (done) {
            GameStatus.Won("${average}ms average", "Score $score from $attempts tries.")
        } else {
            null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        val background = when (phase) {
            ReflexPhase.Ready -> Arcade.Green
            ReflexPhase.Early -> Arcade.Red
            ReflexPhase.Scored -> Arcade.Sky
            ReflexPhase.Waiting -> Arcade.Slate
        }
        Box(
            modifier = boardModifier
                .tapAnywhere {
                    if (done) return@tapAnywhere
                    when (phase) {
                        ReflexPhase.Ready -> {
                            lastMs = System.currentTimeMillis() - readyAt
                            times = times + lastMs
                            phase = if (times.size >= attempts) ReflexPhase.Scored else ReflexPhase.Waiting
                        }
                        // Jumping the gun costs the attempt, not the game.
                        ReflexPhase.Waiting -> phase = ReflexPhase.Early
                        else -> phase = ReflexPhase.Waiting
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            GameCanvas(Modifier.fillMaxSize()) {
                drawRect(background.copy(alpha = 0.9f), size = size)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = when {
                        done -> "Done"
                        phase == ReflexPhase.Ready -> "TAP!"
                        phase == ReflexPhase.Early -> "Too early"
                        phase == ReflexPhase.Scored -> "${lastMs}ms"
                        else -> "Wait for green…"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.8f),
                )
                if (times.isNotEmpty() && !done) {
                    Text(
                        text = "last ${times.last()}ms",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
