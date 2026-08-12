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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The real-time arcade games.
 *
 * They share a convention: the world is measured in units where x runs 0..1 and
 * y runs 0..[BreakoutWorld.HEIGHT]-style constants derived from the board's
 * aspect ratio. Circles stay circular that way, and every speed can be written
 * as "fractions of the board per second" instead of pixels.
 */

// ------------------------------------------------------------------ Brick Breaker

private const val BREAKOUT_ASPECT = 0.78f
private const val BREAKOUT_H = 1f / BREAKOUT_ASPECT

data class Brick(val column: Int, val row: Int, val strength: Int)

data class BreakoutWorld(
    val paddle: Float = 0.5f,
    val ballX: Float = 0.5f,
    val ballY: Float = BREAKOUT_H - 0.12f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val bricks: List<Brick> = emptyList(),
    val score: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val launched: Boolean = false,
    val over: Boolean = false,
) {
    companion object {
        const val COLUMNS = 7
        const val ROWS = 5
        const val PADDLE_W = 0.24f
        const val BALL_R = 0.022f

        fun wall(level: Int): List<Brick> = buildList {
            for (row in 0 until ROWS) {
                for (column in 0 until COLUMNS) {
                    // Lower rows are single-hit; the top two harden as levels go up.
                    add(Brick(column, row, if (row < (level - 1).coerceAtMost(2)) 2 else 1))
                }
            }
        }

        fun new(level: Int = 1, score: Int = 0, lives: Int = 3) = BreakoutWorld(
            bricks = wall(level),
            level = level,
            score = score,
            lives = lives,
        )
    }

    val speed: Float get() = 0.78f + (level - 1) * 0.07f

    fun brickRect(brick: Brick): Pair<Offset, Size> {
        val width = 1f / COLUMNS
        val height = 0.062f
        return Offset(brick.column * width, 0.14f + brick.row * height) to
            Size(width, height)
    }

    /** Releases the ball from the paddle at a slight angle. */
    fun launch(): BreakoutWorld = if (launched) this else copy(
        launched = true,
        vx = speed * 0.45f,
        vy = -speed,
    )

    fun step(dt: Float): BreakoutWorld {
        if (over) return this
        if (!launched) return copy(ballX = paddle, ballY = BREAKOUT_H - 0.12f)

        var x = ballX + vx * dt
        var y = ballY + vy * dt
        var dx = vx
        var dy = vy
        var score = this.score
        var bricks = this.bricks

        if (x < BALL_R) { x = BALL_R; dx = abs(dx) }
        if (x > 1f - BALL_R) { x = 1f - BALL_R; dx = -abs(dx) }
        if (y < BALL_R) { y = BALL_R; dy = abs(dy) }

        // Paddle: the further from the centre it is struck, the wider the angle.
        val paddleY = BREAKOUT_H - 0.075f
        if (dy > 0 && y + BALL_R >= paddleY && y < paddleY + 0.05f) {
            val offset = (x - paddle) / (PADDLE_W / 2f)
            if (abs(offset) <= 1.25f) {
                y = paddleY - BALL_R
                val angle = offset.coerceIn(-1f, 1f) * 1.0f
                dx = speed * sin(angle)
                dy = -speed * cos(angle)
            }
        }

        val hit = bricks.firstOrNull { brick ->
            val (topLeft, size) = brickRect(brick)
            x + BALL_R > topLeft.x && x - BALL_R < topLeft.x + size.width &&
                y + BALL_R > topLeft.y && y - BALL_R < topLeft.y + size.height
        }
        if (hit != null) {
            val (topLeft, size) = brickRect(hit)
            // Bounce off whichever face was actually crossed.
            val fromSide = x < topLeft.x || x > topLeft.x + size.width
            if (fromSide) dx = -dx else dy = -dy
            bricks = if (hit.strength > 1) {
                bricks.map { if (it == hit) it.copy(strength = it.strength - 1) else it }
            } else {
                bricks - hit
            }
            score += if (hit.strength > 1) 5 else 10
        }

        // Falling past the paddle costs a life, and the ball goes back to it.
        if (y > BREAKOUT_H + BALL_R) {
            val lives = this.lives - 1
            return if (lives <= 0) {
                copy(score = score, lives = 0, over = true)
            } else {
                copy(score = score, lives = lives, launched = false, bricks = bricks)
            }
        }

        if (bricks.isEmpty()) return new(level + 1, score + 50, lives)

        return copy(ballX = x, ballY = y, vx = dx, vy = dy, bricks = bricks, score = score)
    }
}

@Composable
fun BreakoutScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(BreakoutWorld.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "breakout")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.launch().step(dt)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "breakout", world.score)
            best = ArcadeScores.best(context, "breakout")
        }
    }

    GameShell(
        title = "Brick Breaker",
        score = world.score,
        best = best,
        extra = "LIVES ${world.lives}",
        status = when {
            world.over -> GameStatus.Over("Game over", "You scored ${world.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Horizontal,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = {
            val step = if (it == Direction.Left) -0.09f else 0.09f
            world = world.copy(paddle = (world.paddle + step).coerceIn(0.12f, 0.88f))
        },
        aspect = BREAKOUT_ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.dragFraction { world = world.copy(paddle = it.coerceIn(0.12f, 0.88f)) }) {
            val unit = size.width

            world.bricks.forEach { brick ->
                val (topLeft, boxSize) = world.brickRect(brick)
                val color = Arcade.series[brick.row % Arcade.series.size]
                drawBevelBlock(
                    topLeft = Offset(topLeft.x * unit + 2f, topLeft.y * unit + 2f),
                    size = Size(boxSize.width * unit - 4f, boxSize.height * unit - 4f),
                    color = if (brick.strength > 1) color.darken(0.25f) else color,
                    radius = unit * 0.012f,
                )
                if (brick.strength > 1) {
                    // A crack line marks the bricks that need a second hit.
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset((topLeft.x + boxSize.width * 0.3f) * unit, (topLeft.y + boxSize.height * 0.3f) * unit),
                        end = Offset((topLeft.x + boxSize.width * 0.7f) * unit, (topLeft.y + boxSize.height * 0.7f) * unit),
                        strokeWidth = 2f,
                    )
                }
            }

            drawBevelBlock(
                topLeft = Offset(
                    (world.paddle - BreakoutWorld.PADDLE_W / 2) * unit,
                    (BREAKOUT_H - 0.075f) * unit,
                ),
                size = Size(BreakoutWorld.PADDLE_W * unit, 0.028f * unit),
                color = Arcade.Sky,
            )

            drawGlossyBall(
                center = Offset(world.ballX * unit, world.ballY * unit),
                radius = BreakoutWorld.BALL_R * unit,
                color = Arcade.Cloud,
            )
        }
    }
}

// ------------------------------------------------------------------ Pong

private const val PONG_ASPECT = 0.72f
private const val PONG_H = 1f / PONG_ASPECT

data class PongWorld(
    val player: Float = 0.5f,
    val ai: Float = 0.5f,
    val ballX: Float = 0.5f,
    val ballY: Float = PONG_H / 2,
    val vx: Float = 0.5f,
    val vy: Float = 0.7f,
    val playerScore: Int = 0,
    val aiScore: Int = 0,
    val rally: Int = 0,
) {
    companion object {
        const val PADDLE_W = 0.26f
        const val TARGET = 11
    }

    val finished: Boolean get() = playerScore >= TARGET || aiScore >= TARGET

    private fun serve(towardsPlayer: Boolean, random: Random) = copy(
        ballX = 0.5f,
        ballY = PONG_H / 2,
        // Served gently and always at an angle, never straight down a column.
        vx = (if (random.nextBoolean()) 1 else -1) * 0.42f,
        vy = if (towardsPlayer) 0.62f else -0.62f,
        rally = 0,
    )

    fun step(dt: Float, random: Random): PongWorld {
        if (finished) return this
        var x = ballX + vx * dt
        var y = ballY + vy * dt
        var dx = vx
        var dy = vy

        if (x < 0.02f) { x = 0.02f; dx = abs(dx) }
        if (x > 0.98f) { x = 0.98f; dx = -abs(dx) }

        var rally = this.rally
        // Both paddles play the same way: contact angle comes from where on the
        // paddle the ball lands.
        if (dy > 0 && y > PONG_H - 0.08f && abs(x - player) < PADDLE_W / 2) {
            y = PONG_H - 0.08f
            dy = -abs(dy)
            dx += (x - player) * 1.1f
            rally++
        }
        if (dy < 0 && y < 0.08f && abs(x - ai) < PADDLE_W / 2) {
            y = 0.08f
            dy = abs(dy)
            dx += (x - ai) * 1.1f
            rally++
        }

        // Rallies get quicker, which is what makes a long one feel earned.
        val gain = 1f + rally * 0.02f
        val speed = hypot(dx, dy)
        if (speed > 0f) {
            val target = (0.85f * gain).coerceAtMost(1.7f)
            dx = dx / speed * target
            dy = dy / speed * target
        }

        // The opponent chases with a capped speed, so it can be beaten wide.
        val aiTarget = if (dy < 0) x else 0.5f
        val aiMove = (aiTarget - ai).coerceIn(-0.62f * dt, 0.62f * dt)

        return when {
            y > PONG_H -> copy(aiScore = aiScore + 1, ai = ai + aiMove)
                .serve(towardsPlayer = false, random = random)

            y < 0f -> copy(playerScore = playerScore + 1, ai = ai + aiMove)
                .serve(towardsPlayer = true, random = random)

            else -> copy(ballX = x, ballY = y, vx = dx, vy = dy, ai = ai + aiMove, rally = rally)
        }
    }
}

@Composable
fun PongScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(PongWorld()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "pong")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.finished && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
    }
    LaunchedEffect(world.finished) {
        if (world.finished && world.playerScore > world.aiScore) {
            ArcadeScores.submit(context, "pong", world.playerScore * 10 + (11 - world.aiScore))
            best = ArcadeScores.best(context, "pong")
        }
    }

    GameShell(
        title = "Pong",
        score = world.playerScore,
        best = best,
        extra = "THEM ${world.aiScore}",
        status = when {
            world.playerScore >= PongWorld.TARGET ->
                GameStatus.Won("You win", "${world.playerScore} - ${world.aiScore}")

            world.aiScore >= PongWorld.TARGET ->
                GameStatus.Over("You lose", "${world.playerScore} - ${world.aiScore}")

            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Horizontal,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = {
            val step = if (it == Direction.Left) -0.1f else 0.1f
            world = world.copy(player = (world.player + step).coerceIn(0.13f, 0.87f))
        },
        aspect = PONG_ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.dragFraction { world = world.copy(player = it.coerceIn(0.13f, 0.87f)) }) {
            val unit = size.width

            // Centre line, drawn as dashes the old-fashioned way.
            var y = 0f
            while (y < size.height) {
                drawRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(size.width / 2 - 2f, y),
                    size = Size(4f, 14f),
                )
                y += 26f
            }

            drawBevelBlock(
                topLeft = Offset((world.ai - PongWorld.PADDLE_W / 2) * unit, 0.05f * unit),
                size = Size(PongWorld.PADDLE_W * unit, 0.026f * unit),
                color = Arcade.Coral,
            )
            drawBevelBlock(
                topLeft = Offset((world.player - PongWorld.PADDLE_W / 2) * unit, (PONG_H - 0.076f) * unit),
                size = Size(PongWorld.PADDLE_W * unit, 0.026f * unit),
                color = Arcade.Sky,
            )
            drawGlossyBall(
                center = Offset(world.ballX * unit, world.ballY * unit),
                radius = 0.019f * unit,
                color = Arcade.Cloud,
            )
        }
    }
}

// ------------------------------------------------------------------ Invaders

private const val INVADERS_ASPECT = 0.8f
private const val INVADERS_H = 1f / INVADERS_ASPECT

data class Invader(val column: Int, val row: Int, val alive: Boolean = true)

data class Shot(val x: Float, val y: Float, val vy: Float, val mine: Boolean)

data class InvadersWorld(
    val ship: Float = 0.5f,
    val invaders: List<Invader> = emptyList(),
    val fleetX: Float = 0.06f,
    val fleetY: Float = 0.10f,
    val fleetDir: Int = 1,
    val shots: List<Shot> = emptyList(),
    val bunkers: List<Pair<Cell, Int>> = emptyList(),
    val score: Int = 0,
    val lives: Int = 3,
    val wave: Int = 1,
    val frame: Int = 0,
    val over: Boolean = false,
    val fireCooldown: Float = 0f,
) {
    companion object {
        const val COLUMNS = 7
        const val ROWS = 4
        const val SPACING = 0.115f
        const val SHIP_Y = INVADERS_H - 0.08f

        fun new(wave: Int = 1, score: Int = 0, lives: Int = 3) = InvadersWorld(
            invaders = buildList {
                for (row in 0 until ROWS) for (column in 0 until COLUMNS) add(Invader(column, row))
            },
            // Three bunkers, each with a small pool of hit points.
            bunkers = listOf(0.2f, 0.5f, 0.8f).mapIndexed { index, _ ->
                Cell(index, 0) to 6
            },
            wave = wave,
            score = score,
            lives = lives,
        )
    }

    val remaining: Int get() = invaders.count { it.alive }

    /** Speed scales with how few are left - the classic accelerating fleet. */
    val fleetSpeed: Float
        get() = (0.055f + (ROWS * COLUMNS - remaining) * 0.004f) * (1f + (wave - 1) * 0.15f)

    fun invaderPos(invader: Invader) =
        Offset(fleetX + invader.column * SPACING, fleetY + invader.row * SPACING)

    fun bunkerX(index: Int) = listOf(0.2f, 0.5f, 0.8f)[index]

    fun step(dt: Float, random: Random): InvadersWorld {
        if (over) return this

        var x = fleetX + fleetDir * fleetSpeed * dt
        var y = fleetY
        var direction = fleetDir
        val rightEdge = x + (COLUMNS - 1) * SPACING + 0.07f
        if (rightEdge > 1f || x < 0.02f) {
            direction = -fleetDir
            x = fleetX
            y += 0.045f
        }

        var shots = shots.map { it.copy(y = it.y + it.vy * dt) }
            .filter { it.y > -0.05f && it.y < INVADERS_H + 0.05f }

        // Invaders shoot from the bottom of their column only, and rarely, so
        // the screen never fills with fire the player cannot read.
        if (random.nextFloat() < dt * (0.9f + wave * 0.25f)) {
            invaders.filter { it.alive }
                .groupBy { it.column }
                .values
                .mapNotNull { column -> column.maxByOrNull { it.row } }
                .randomOrNull(random)
                ?.let { shooter ->
                    val position = invaderPos(shooter)
                    shots = shots + Shot(position.x + 0.035f, position.y + 0.07f, 0.55f, mine = false)
                }
        }

        var invaders = this.invaders
        var bunkers = this.bunkers
        var score = this.score
        var lives = this.lives

        // Player shots hit invaders.
        shots.filter { it.mine }.forEach { shot ->
            val hit = invaders.firstOrNull { invader ->
                invader.alive && run {
                    val position = invaderPos(invader)
                    shot.x > position.x && shot.x < position.x + 0.07f &&
                        shot.y > position.y && shot.y < position.y + 0.07f
                }
            }
            if (hit != null) {
                invaders = invaders.map { if (it == hit) it.copy(alive = false) else it }
                shots = shots - shot
                score += (ROWS - hit.row) * 10
            }
        }

        // Bunkers absorb anything crossing their band, from either side.
        val bunkerY = SHIP_Y - 0.12f
        shots.forEach { shot ->
            if (shot.y in bunkerY..(bunkerY + 0.05f)) {
                val index = bunkers.indices.firstOrNull { abs(shot.x - bunkerX(it)) < 0.09f }
                if (index != null && bunkers[index].second > 0) {
                    bunkers = bunkers.toMutableList()
                        .also { it[index] = it[index].first to it[index].second - 1 }
                    shots = shots - shot
                }
            }
        }

        // Enemy fire reaching the ship.
        val hitShip = shots.firstOrNull {
            !it.mine && it.y > SHIP_Y - 0.03f && abs(it.x - ship) < 0.055f
        }
        if (hitShip != null) {
            shots = shots - hitShip
            lives -= 1
        }

        val landed = invaders.any { it.alive && invaderPos(it).y > SHIP_Y - 0.08f }
        if (lives <= 0 || landed) return copy(score = score, lives = 0, over = true)

        if (invaders.none { it.alive }) return new(wave + 1, score + 100, lives)

        return copy(
            invaders = invaders,
            fleetX = x,
            fleetY = y,
            fleetDir = direction,
            shots = shots,
            bunkers = bunkers,
            score = score,
            lives = lives,
            frame = frame + 1,
            fireCooldown = (fireCooldown - dt).coerceAtLeast(0f),
        )
    }

    /** Rate-limited rather than ignored: holding fire still works, just not spam. */
    fun fire(): InvadersWorld = if (fireCooldown > 0f) this else copy(
        shots = shots + Shot(ship, SHIP_Y - 0.03f, -1.15f, mine = true),
        fireCooldown = 0.32f,
    )
}

@Composable
fun InvadersScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(InvadersWorld.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "invaders")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "invaders", world.score)
            best = ArcadeScores.best(context, "invaders")
        }
    }

    GameShell(
        title = "Invaders",
        score = world.score,
        best = best,
        extra = "LIVES ${world.lives}",
        status = when {
            world.over -> GameStatus.Over("The fleet lands", "You scored ${world.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.HorizontalAction,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = {
            val step = if (it == Direction.Left) -0.075f else 0.075f
            world = world.copy(ship = (world.ship + step).coerceIn(0.06f, 0.94f))
        },
        onAction = { world = world.fire() },
        aspect = INVADERS_ASPECT,
    ) { boardModifier ->
        GameCanvas(
            boardModifier
                .dragFraction { world = world.copy(ship = it.coerceIn(0.06f, 0.94f)) }
                .tapAnywhere { world = world.fire() },
        ) {
            val unit = size.width
            drawStarField(42, seed = 3)

            world.invaders.filter { it.alive }.forEach { invader ->
                val position = world.invaderPos(invader)
                drawInvader(
                    topLeft = Offset(position.x * unit, position.y * unit),
                    size = 0.07f * unit,
                    color = Arcade.series[invader.row % Arcade.series.size],
                    frame = world.frame / 20,
                )
            }

            world.bunkers.forEachIndexed { index, (_, strength) ->
                if (strength <= 0) return@forEachIndexed
                drawBevelBlock(
                    topLeft = Offset(
                        (world.bunkerX(index) - 0.08f) * unit,
                        (InvadersWorld.SHIP_Y - 0.12f) * unit,
                    ),
                    size = Size(0.16f * unit, 0.045f * unit),
                    color = Arcade.Green.copy(alpha = 0.35f + strength * 0.1f),
                )
            }

            world.shots.forEach { shot ->
                drawRoundRect(
                    color = if (shot.mine) Arcade.Cloud else Arcade.Red,
                    topLeft = Offset(shot.x * unit - 2f, shot.y * unit - 10f),
                    size = Size(4f, 18f),
                    cornerRadius = CornerRadius(2f),
                )
            }

            drawShip(
                center = Offset(world.ship * unit, InvadersWorld.SHIP_Y * unit),
                radius = 0.045f * unit,
                headingDegrees = -90f,
                color = Arcade.Sky,
            )
        }
    }
}

// ------------------------------------------------------------------ Asteroids

private const val ROCK_ASPECT = 0.85f
private const val ROCK_H = 1f / ROCK_ASPECT

data class Rock(val x: Float, val y: Float, val vx: Float, val vy: Float, val size: Int, val spin: Float)

/** Bullets carry their own velocity and a life, so they expire by distance. */
data class Bullet(val x: Float, val y: Float, val vx: Float, val vy: Float, val life: Float)

data class AsteroidsWorld(
    val x: Float = 0.5f,
    val y: Float = ROCK_H / 2,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val heading: Float = -90f,
    val thrusting: Boolean = false,
    val rocks: List<Rock> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val score: Int = 0,
    val lives: Int = 3,
    val shield: Float = 2.5f,
    val wave: Int = 1,
    val over: Boolean = false,
) {
    companion object {
        fun rocks(count: Int, random: Random) = List(count) {
            // Spawned around the edges so nothing materialises on the player.
            val edge = random.nextInt(4)
            Rock(
                x = if (edge == 0) 0.02f else if (edge == 1) 0.98f else random.nextFloat(),
                y = if (edge == 2) 0.02f else if (edge == 3) ROCK_H - 0.02f else random.nextFloat() * ROCK_H,
                vx = (random.nextFloat() - 0.5f) * 0.22f,
                vy = (random.nextFloat() - 0.5f) * 0.22f,
                size = 3,
                spin = random.nextFloat() * 60f - 30f,
            )
        }

        fun new(random: Random, wave: Int = 1, score: Int = 0, lives: Int = 3) = AsteroidsWorld(
            rocks = rocks(2 + wave, random),
            wave = wave,
            score = score,
            lives = lives,
        )
    }

    private fun wrapX(value: Float) = (value + 1f) % 1f
    private fun wrapY(value: Float) = (value + ROCK_H) % ROCK_H

    fun radiusOf(rock: Rock) = 0.022f * rock.size

    fun step(dt: Float, random: Random): AsteroidsWorld {
        if (over) return this

        val radians = Math.toRadians(heading.toDouble())
        var dx = vx
        var dy = vy
        if (thrusting) {
            dx += cos(radians).toFloat() * 0.9f * dt
            dy += sin(radians).toFloat() * 0.9f * dt
        }
        // Drag, so a ship that has been let go eventually becomes controllable.
        dx *= (1f - 0.5f * dt)
        dy *= (1f - 0.5f * dt)

        var rocks = rocks.map {
            it.copy(x = wrapX(it.x + it.vx * dt), y = wrapY(it.y + it.vy * dt))
        }
        var bullets = bullets
            .map {
                it.copy(
                    x = wrapX(it.x + it.vx * dt),
                    y = wrapY(it.y + it.vy * dt),
                    life = it.life - dt,
                )
            }
            .filter { it.life > 0f }
        var score = this.score

        bullets.forEach { bullet ->
            val hit = rocks.firstOrNull {
                hypot(it.x - bullet.x, it.y - bullet.y) < radiusOf(it)
            }
            if (hit != null) {
                bullets = bullets - bullet
                rocks = rocks - hit
                score += (4 - hit.size) * 20
                if (hit.size > 1) {
                    // Splitting into two faster fragments is the whole game.
                    rocks = rocks + List(2) {
                        Rock(
                            x = hit.x,
                            y = hit.y,
                            vx = (random.nextFloat() - 0.5f) * 0.4f,
                            vy = (random.nextFloat() - 0.5f) * 0.4f,
                            size = hit.size - 1,
                            spin = random.nextFloat() * 90f - 45f,
                        )
                    }
                }
            }
        }

        val nx = wrapX(x + dx * dt)
        val ny = wrapY(y + dy * dt)
        val shield = (this.shield - dt).coerceAtLeast(0f)

        if (shield <= 0f && rocks.any { hypot(it.x - nx, it.y - ny) < radiusOf(it) + 0.02f }) {
            val lives = this.lives - 1
            return if (lives <= 0) {
                copy(score = score, lives = 0, over = true)
            } else {
                // Respawned in the middle, motionless, and briefly invulnerable.
                copy(
                    x = 0.5f, y = ROCK_H / 2, vx = 0f, vy = 0f,
                    lives = lives, score = score, rocks = rocks, bullets = emptyList(),
                    shield = 2.5f,
                )
            }
        }

        if (rocks.isEmpty()) return new(random, wave + 1, score + 150, lives)

        return copy(
            x = nx, y = ny, vx = dx, vy = dy,
            rocks = rocks, bullets = bullets, score = score, shield = shield,
        )
    }

    /** Capped at six in flight, which is plenty and keeps the screen readable. */
    fun fire(): AsteroidsWorld {
        if (bullets.size >= 6) return this
        val radians = Math.toRadians(heading.toDouble())
        val dirX = cos(radians).toFloat()
        val dirY = sin(radians).toFloat()
        return copy(
            bullets = bullets + Bullet(
                x = wrapX(x + dirX * 0.05f),
                y = wrapY(y + dirY * 0.05f),
                // Inherits the ship's momentum, so shooting while drifting works
                // the way the physics everywhere else in the game implies.
                vx = vx + dirX * 0.95f,
                vy = vy + dirY * 0.95f,
                life = 0.9f,
            ),
        )
    }
}

@Composable
fun AsteroidsScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(AsteroidsWorld.new(random)) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "asteroids")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
        // Thrust is a tap, so it decays rather than sticking on.
        if (world.thrusting) world = world.copy(thrusting = false)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "asteroids", world.score)
            best = ArcadeScores.best(context, "asteroids")
        }
    }

    GameShell(
        title = "Asteroids",
        score = world.score,
        best = best,
        extra = "LIVES ${world.lives}",
        status = when {
            world.over -> GameStatus.Over("Ship lost", "You scored ${world.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Ship,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = {
            world = when (it) {
                Direction.Left -> world.copy(heading = world.heading - 22f)
                Direction.Right -> world.copy(heading = world.heading + 22f)
                else -> world.copy(thrusting = true)
            }
        },
        onAction = { world = world.fire() },
        aspect = ROCK_ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.tapAnywhere { world = world.fire() }) {
            val unit = size.width
            drawStarField(60, seed = 11)

            world.rocks.forEach { rock ->
                val centre = Offset(rock.x * unit, rock.y * unit)
                val radius = world.radiusOf(rock) * unit
                // Rocks are irregular polygons, not circles - a circle reads as
                // a ball, and this is meant to be stone.
                val path = androidx.compose.ui.graphics.Path()
                val points = 9
                repeat(points) { index ->
                    val angle = index * 360f / points + rock.spin
                    val wobble = 0.78f + ((index * 37) % 10) / 24f
                    val point = pointOnCircle(centre, radius * wobble, angle)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                drawPath(path, Arcade.Slate)
                drawPath(path, Color.White.copy(alpha = 0.25f), style = Stroke(width = 2f))
            }

            world.bullets.forEach {
                drawCircle(Arcade.Amber, 3.5f, Offset(it.x * unit, it.y * unit))
            }

            if (world.shield > 0f) {
                drawCircle(
                    color = Arcade.Sky.copy(alpha = 0.35f),
                    radius = 0.055f * unit,
                    center = Offset(world.x * unit, world.y * unit),
                    style = Stroke(width = 3f),
                )
            }
            drawShip(
                center = Offset(world.x * unit, world.y * unit),
                radius = 0.038f * unit,
                headingDegrees = world.heading,
                color = Arcade.Cloud,
                thrusting = world.thrusting,
            )
        }
    }
}

// ------------------------------------------------------------------ Light Cycles

data class TronState(
    val player: Cell,
    val playerDir: Direction,
    val rival: Cell,
    val rivalDir: Direction,
    val trails: Map<Cell, Boolean>,
    val score: Int = 0,
    val outcome: ChompOutcome = ChompOutcome.Playing,
) {
    companion object {
        const val GRID = 21

        fun new() = TronState(
            player = Cell(4, GRID / 2),
            playerDir = Direction.Right,
            rival = Cell(GRID - 5, GRID / 2),
            rivalDir = Direction.Left,
            trails = mapOf(Cell(4, GRID / 2) to true, Cell(GRID - 5, GRID / 2) to false),
        )
    }

    private fun blocked(cell: Cell) =
        cell.x !in 0 until GRID || cell.y !in 0 until GRID || cell in trails

    fun turn(direction: Direction): TronState =
        if (direction == playerDir.opposite()) this else copy(playerDir = direction)

    fun step(random: Random): TronState {
        if (outcome != ChompOutcome.Playing) return this

        // The rival keeps going while it can, prefers open space, and only turns
        // when it has to - which is what makes it feel like a driver.
        val options = Direction.entries
            .filter { it != rivalDir.opposite() && !blocked(rival.move(it)) }
        val rivalDir = when {
            options.isEmpty() -> rivalDir
            !blocked(rival.move(this.rivalDir)) && random.nextInt(100) > 22 -> this.rivalDir
            else -> options.maxByOrNull { direction ->
                // Count how far it could keep going that way.
                var steps = 0
                var probe = rival
                while (steps < 6 && !blocked(probe.move(direction))) {
                    probe = probe.move(direction)
                    steps++
                }
                steps
            } ?: this.rivalDir
        }

        val nextPlayer = player.move(playerDir)
        val nextRival = rival.move(rivalDir)
        val playerCrash = blocked(nextPlayer)
        val rivalCrash = blocked(nextRival)
        val headOn = nextPlayer == nextRival

        return when {
            (playerCrash && rivalCrash) || headOn ->
                copy(outcome = ChompOutcome.Lost, score = score)

            playerCrash -> copy(outcome = ChompOutcome.Lost)
            rivalCrash -> copy(outcome = ChompOutcome.Won, score = score + 100)
            else -> copy(
                player = nextPlayer,
                rival = nextRival,
                rivalDir = rivalDir,
                trails = trails + (nextPlayer to true) + (nextRival to false),
                score = score + 1,
            )
        }
    }
}

@Composable
fun TronScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(TronState.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "tron")) }
    val countdown = rememberCountdown(round)

    TickLoop(
        running = state.outcome == ChompOutcome.Playing && !paused && countdown == 0,
        intervalMs = { 115L },
    ) { state = state.step(random) }

    LaunchedEffect(state.outcome) {
        if (state.outcome != ChompOutcome.Playing) {
            ArcadeScores.submit(context, "tron", state.score)
            best = ArcadeScores.best(context, "tron")
        }
    }

    GameShell(
        title = "Light Cycles",
        score = state.score,
        best = best,
        status = when (state.outcome) {
            ChompOutcome.Won -> GameStatus.Won("They crashed first", "Score ${state.score}.")
            ChompOutcome.Lost -> GameStatus.Over("You crashed", "Score ${state.score}.")
            else -> if (paused) GameStatus.Paused else null
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
            val cell = size.width / TronState.GRID
            state.trails.forEach { (position, mine) ->
                drawRect(
                    color = (if (mine) Arcade.Teal else Arcade.Pink).copy(alpha = 0.55f),
                    topLeft = Offset(position.x * cell, position.y * cell),
                    size = Size(cell, cell),
                )
            }
            listOf(state.player to Arcade.Teal, state.rival to Arcade.Pink).forEach { (cellPos, color) ->
                drawRect(
                    color = color,
                    topLeft = Offset(cellPos.x * cell, cellPos.y * cell),
                    size = Size(cell, cell),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(cellPos.x * cell + cell * 0.3f, cellPos.y * cell + cell * 0.3f),
                    size = Size(cell * 0.4f, cell * 0.4f),
                )
            }
        }
    }
}
