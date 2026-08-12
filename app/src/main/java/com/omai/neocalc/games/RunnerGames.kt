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
import kotlin.math.abs
import kotlin.random.Random

// ------------------------------------------------------------------ Blocks (falling tetrominoes)

/** The seven pieces, each as its rotations' filled cells in a 4x4 box. */
enum class Piece(val color: Color, val cells: List<List<Cell>>) {
    I(
        Arcade.Teal,
        listOf(
            listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)),
            listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2), Cell(2, 3)),
        ),
    ),
    O(
        Arcade.Yellow,
        listOf(listOf(Cell(1, 0), Cell(2, 0), Cell(1, 1), Cell(2, 1))),
    ),
    T(
        Arcade.Purple,
        listOf(
            listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
            listOf(Cell(1, 0), Cell(1, 1), Cell(2, 1), Cell(1, 2)),
            listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(1, 2)),
            listOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2)),
        ),
    ),
    S(
        Arcade.Green,
        listOf(
            listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1)),
            listOf(Cell(1, 0), Cell(1, 1), Cell(2, 1), Cell(2, 2)),
        ),
    ),
    Z(
        Arcade.Red,
        listOf(
            listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)),
            listOf(Cell(2, 0), Cell(1, 1), Cell(2, 1), Cell(1, 2)),
        ),
    ),
    J(
        Arcade.Blue,
        listOf(
            listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
            listOf(Cell(1, 0), Cell(2, 0), Cell(1, 1), Cell(1, 2)),
            listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(2, 2)),
            listOf(Cell(1, 0), Cell(1, 1), Cell(0, 2), Cell(1, 2)),
        ),
    ),
    L(
        Arcade.Coral,
        listOf(
            listOf(Cell(2, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
            listOf(Cell(1, 0), Cell(1, 1), Cell(1, 2), Cell(2, 2)),
            listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(0, 2)),
            listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(1, 2)),
        ),
    ),
}

data class TetrisState(
    val well: Map<Cell, Color> = emptyMap(),
    val piece: Piece = Piece.T,
    val rotation: Int = 0,
    val position: Cell = Cell(3, 0),
    val next: Piece = Piece.I,
    val score: Int = 0,
    val lines: Int = 0,
    val over: Boolean = false,
) {
    companion object {
        const val WIDTH = 10
        const val HEIGHT = 18

        fun new(random: Random) = TetrisState(
            piece = Piece.entries.random(random),
            next = Piece.entries.random(random),
        )
    }

    val level: Int get() = lines / 10 + 1

    /** One step faster per ten lines, with a floor so it stays playable. */
    val intervalMs: Long get() = (620L - (level - 1) * 55L).coerceAtLeast(120L)

    fun blocks(
        piece: Piece = this.piece,
        rotation: Int = this.rotation,
        position: Cell = this.position,
    ): List<Cell> = piece.cells[rotation % piece.cells.size]
        .map { Cell(position.x + it.x, position.y + it.y) }

    private fun legal(cells: List<Cell>) = cells.all {
        it.x in 0 until WIDTH && it.y < HEIGHT && it !in well
    }

    fun move(dx: Int): TetrisState {
        val moved = position.copy(x = position.x + dx)
        return if (legal(blocks(position = moved))) copy(position = moved) else this
    }

    /**
     * Rotation with a wall kick: if the spin would clip a wall, the piece is
     * nudged in instead of the input being dropped. Without this, rotating in a
     * corner silently does nothing, which reads as a broken control.
     */
    fun rotate(): TetrisState {
        val turned = (rotation + 1) % piece.cells.size
        listOf(0, -1, 1, -2, 2).forEach { kick ->
            val candidate = position.copy(x = position.x + kick)
            if (legal(blocks(rotation = turned, position = candidate))) {
                return copy(rotation = turned, position = candidate)
            }
        }
        return this
    }

    fun drop(random: Random): TetrisState {
        val lower = position.copy(y = position.y + 1)
        if (legal(blocks(position = lower))) return copy(position = lower)

        // Landed: bake the piece in, clear any full rows, and deal the next one.
        val filled = well + blocks().associateWith { piece.color }
        val fullRows = (0 until HEIGHT).filter { row ->
            (0 until WIDTH).all { column -> Cell(column, row) in filled }
        }
        val cleared = if (fullRows.isEmpty()) {
            filled
        } else {
            filled
                .filterKeys { it.y !in fullRows }
                .mapKeys { (cell, _) ->
                    // Everything above a cleared row falls by however many rows
                    // below it went away.
                    cell.copy(y = cell.y + fullRows.count { it > cell.y })
                }
        }

        val gained = when (fullRows.size) {
            0 -> 0
            1 -> 100
            2 -> 300
            3 -> 500
            else -> 800
        }

        val spawned = copy(
            well = cleared,
            piece = next,
            next = Piece.entries.random(random),
            rotation = 0,
            position = Cell(3, 0),
            score = score + gained + 4,
            lines = lines + fullRows.size,
        )
        return if (legal(spawned.blocks())) spawned else spawned.copy(over = true)
    }
}

@Composable
fun TetrisScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(TetrisState.new(random)) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "blocks")) }
    val countdown = rememberCountdown(round)

    TickLoop(
        running = !state.over && !paused && countdown == 0,
        intervalMs = { state.intervalMs },
    ) { state = state.drop(random) }

    LaunchedEffect(state.over) {
        if (state.over) {
            ArcadeScores.submit(context, "blocks", state.score)
            best = ArcadeScores.best(context, "blocks")
        }
    }

    GameShell(
        title = "Blocks",
        score = state.score,
        best = best,
        extra = "LINES ${state.lines}",
        status = when {
            state.over -> GameStatus.Over("Stack topped out", "You cleared ${state.lines} lines.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = {
            state = when (it) {
                Direction.Left -> state.move(-1)
                Direction.Right -> state.move(1)
                Direction.Up -> state.rotate()
                Direction.Down -> state.drop(random)
            }
        },
        aspect = TetrisState.WIDTH.toFloat() / TetrisState.HEIGHT,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections {
            state = when (it) {
                Direction.Left -> state.move(-1)
                Direction.Right -> state.move(1)
                Direction.Up -> state.rotate()
                Direction.Down -> state.drop(random)
            }
        }) {
            val cell = size.width / TetrisState.WIDTH

            for (column in 0..TetrisState.WIDTH) {
                drawLine(
                    Color.White.copy(alpha = 0.04f),
                    Offset(column * cell, 0f),
                    Offset(column * cell, size.height),
                )
            }

            // Ghost piece: where it will land if you do nothing. This is the
            // single biggest quality-of-life feature the original lacked.
            var ghost = state.position
            while (state.blocks(position = ghost.copy(y = ghost.y + 1)).all {
                    it.y < TetrisState.HEIGHT && it !in state.well
                }
            ) {
                ghost = ghost.copy(y = ghost.y + 1)
            }
            state.blocks(position = ghost).forEach {
                drawRoundRect(
                    color = state.piece.color.copy(alpha = 0.18f),
                    topLeft = Offset(it.x * cell + 2f, it.y * cell + 2f),
                    size = Size(cell - 4f, cell - 4f),
                    cornerRadius = CornerRadius(cell * 0.2f),
                )
            }

            state.well.forEach { (position, color) ->
                drawBevelBlock(
                    Offset(position.x * cell + 1.5f, position.y * cell + 1.5f),
                    Size(cell - 3f, cell - 3f),
                    color,
                )
            }
            state.blocks().forEach {
                drawBevelBlock(
                    Offset(it.x * cell + 1.5f, it.y * cell + 1.5f),
                    Size(cell - 3f, cell - 3f),
                    state.piece.color,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Road Hop

data class TrafficLane(val row: Int, val offset: Float, val speed: Float, val gaps: Set<Int>)

data class HopState(
    val player: Cell,
    val lanes: List<TrafficLane>,
    val score: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val over: Boolean = false,
) {
    companion object {
        const val WIDTH = 11
        const val HEIGHT = 13
        const val FIRST_ROAD = 1
        const val LAST_ROAD = HEIGHT - 2

        fun lanes(level: Int, random: Random): List<TrafficLane> =
            (FIRST_ROAD..LAST_ROAD).map { row ->
                // Every lane keeps at least two gaps, so no row is ever a wall.
                val gapCount = (3 - level / 4).coerceAtLeast(2)
                TrafficLane(
                    row = row,
                    offset = random.nextFloat() * WIDTH,
                    speed = (0.9f + random.nextFloat() * 0.8f + level * 0.16f) *
                        if (row % 2 == 0) 1f else -1f,
                    gaps = buildSet {
                        while (size < gapCount) add(random.nextInt(WIDTH))
                    },
                )
            }

        fun new(random: Random, level: Int = 1, score: Int = 0, lives: Int = 3) = HopState(
            player = Cell(WIDTH / 2, HEIGHT - 1),
            lanes = lanes(level, random),
            level = level,
            score = score,
            lives = lives,
        )
    }

    fun occupied(lane: TrafficLane, x: Int): Boolean {
        val shifted = ((x - lane.offset).toInt() % WIDTH + WIDTH) % WIDTH
        return shifted !in lane.gaps
    }

    fun advance(dt: Float): HopState {
        if (over) return this
        val moved = lanes.map { it.copy(offset = (it.offset + it.speed * dt + WIDTH) % WIDTH) }
        val lane = moved.firstOrNull { it.row == player.y }
        val hit = lane != null && occupied(lane, player.x)
        return when {
            !hit -> copy(lanes = moved)
            lives > 1 -> copy(lanes = moved, lives = lives - 1, player = Cell(WIDTH / 2, HEIGHT - 1))
            else -> copy(lanes = moved, lives = 0, over = true)
        }
    }

    fun hop(direction: Direction, random: Random): HopState {
        if (over) return this
        val next = Cell(
            (player.x + direction.dx).coerceIn(0, WIDTH - 1),
            (player.y + direction.dy).coerceIn(0, HEIGHT - 1),
        )
        // Reaching the top bank scores and starts a slightly quicker board.
        return if (next.y == 0) {
            new(random, level + 1, score + 100, lives)
        } else {
            copy(player = next, score = if (next.y < player.y) score + 10 else score)
        }
    }
}

@Composable
fun FroggerScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var state by remember(round) { mutableStateOf(HopState.new(random)) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "hop")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !state.over && !paused && countdown == 0) { dt ->
        state = state.advance(dt)
    }
    LaunchedEffect(state.over) {
        if (state.over) {
            ArcadeScores.submit(context, "hop", state.score)
            best = ArcadeScores.best(context, "hop")
        }
    }

    GameShell(
        title = "Road Hop",
        score = state.score,
        best = best,
        extra = "LIVES ${state.lives}",
        status = when {
            state.over -> GameStatus.Over("Squashed", "You scored ${state.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = { state = state.hop(it, random) },
        aspect = HopState.WIDTH.toFloat() / HopState.HEIGHT,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections { state = state.hop(it, random) }) {
            val cell = size.width / HopState.WIDTH

            // Grass banks top and bottom, tarmac between - the road reads as a road.
            drawRect(Arcade.Green.darken(0.35f), Offset.Zero, Size(size.width, cell))
            drawRect(
                Arcade.Green.darken(0.35f),
                Offset(0f, (HopState.HEIGHT - 1) * cell),
                Size(size.width, cell),
            )
            drawRect(
                Color(0xFF37474F),
                Offset(0f, cell),
                Size(size.width, cell * (HopState.HEIGHT - 2)),
            )
            for (row in HopState.FIRST_ROAD..HopState.LAST_ROAD) {
                var x = 0f
                while (x < size.width) {
                    drawRect(
                        Color.White.copy(alpha = 0.18f),
                        Offset(x, row * cell - 1f),
                        Size(cell * 0.35f, 2f),
                    )
                    x += cell * 0.7f
                }
            }

            state.lanes.forEach { lane ->
                for (x in 0 until HopState.WIDTH) {
                    if (!state.occupied(lane, x)) continue
                    drawCar(
                        topLeft = Offset(x * cell + cell * 0.08f, lane.row * cell + cell * 0.12f),
                        size = Size(cell * 0.84f, cell * 0.76f),
                        color = Arcade.series[(lane.row * 3 + x) % Arcade.series.size],
                        facingUp = lane.speed > 0,
                    )
                }
            }

            drawFrog(Offset(state.player.x * cell, state.player.y * cell), cell)
        }
    }
}

// ------------------------------------------------------------------ Lane Racer

data class Obstacle(val lane: Int, val y: Float, val kind: Int)

data class RacerWorld(
    val lane: Int = 1,
    val obstacles: List<Obstacle> = emptyList(),
    val distance: Float = 0f,
    val spawnIn: Float = 1.2f,
    val over: Boolean = false,
) {
    companion object {
        const val LANES = 3
        const val ASPECT = 0.62f
        val HEIGHT = 1f / ASPECT
    }

    val score: Int get() = distance.toInt()

    /** Ramps for a minute and then holds - endless acceleration is just unfair. */
    val speed: Float get() = (0.85f + distance / 180f).coerceAtMost(2.1f)

    fun step(dt: Float, random: Random): RacerWorld {
        if (over) return this
        val moved = obstacles
            .map { it.copy(y = it.y + speed * dt) }
            .filter { it.y < HEIGHT + 0.2f }

        var spawn = spawnIn - dt
        var spawned = moved
        if (spawn <= 0f) {
            // Never spawn across every lane at once: there is always a way through.
            val blocked = (0 until LANES).shuffled(random).take(random.nextInt(1, LANES))
            spawned = moved + blocked.map { Obstacle(it, -0.2f, random.nextInt(3)) }
            spawn = (0.95f - distance / 400f).coerceAtLeast(0.45f)
        }

        val playerY = HEIGHT - 0.16f
        val crashed = spawned.any {
            it.lane == lane && abs(it.y - playerY) < 0.11f
        }
        return copy(
            obstacles = spawned,
            distance = distance + speed * dt * 12f,
            spawnIn = spawn,
            over = crashed,
        )
    }
}

@Composable
fun RacerScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(RacerWorld()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "racer")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "racer", world.score)
            best = ArcadeScores.best(context, "racer")
        }
    }

    GameShell(
        title = "Lane Racer",
        score = world.score,
        best = best,
        status = when {
            world.over -> GameStatus.Over("Crash", "You covered ${world.score}m.")
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
            val next = world.lane + if (it == Direction.Left) -1 else 1
            world = world.copy(lane = next.coerceIn(0, RacerWorld.LANES - 1))
        },
        aspect = RacerWorld.ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections {
            val next = world.lane + if (it == Direction.Left) -1 else 1
            world = world.copy(lane = next.coerceIn(0, RacerWorld.LANES - 1))
        }) {
            val unit = size.width
            val laneWidth = unit / RacerWorld.LANES

            drawRect(Color(0xFF2E3B44), Offset.Zero, size)
            // Dashes scroll with the distance travelled, which is what sells speed.
            for (divider in 1 until RacerWorld.LANES) {
                var y = -(world.distance * 8f) % 60f
                while (y < size.height) {
                    drawRect(
                        Color.White.copy(alpha = 0.35f),
                        Offset(divider * laneWidth - 2.5f, y),
                        Size(5f, 28f),
                    )
                    y += 60f
                }
            }

            world.obstacles.forEach { obstacle ->
                drawCar(
                    topLeft = Offset(
                        obstacle.lane * laneWidth + laneWidth * 0.16f,
                        obstacle.y * unit,
                    ),
                    size = Size(laneWidth * 0.68f, laneWidth * 1.15f),
                    color = Arcade.series[obstacle.kind % Arcade.series.size],
                    facingUp = false,
                )
            }

            drawCar(
                topLeft = Offset(
                    world.lane * laneWidth + laneWidth * 0.16f,
                    (RacerWorld.HEIGHT - 0.22f) * unit,
                ),
                size = Size(laneWidth * 0.68f, laneWidth * 1.15f),
                color = Arcade.Sky,
                facingUp = true,
            )
        }
    }
}

// ------------------------------------------------------------------ Cave Copter

data class CopterWorld(
    val y: Float = 0.5f,
    val vy: Float = 0f,
    val lifting: Boolean = false,
    val scroll: Float = 0f,
    val walls: List<Triple<Float, Float, Float>> = emptyList(),
    val passed: Int = 0,
    val over: Boolean = false,
) {
    companion object {
        const val ASPECT = 0.72f
        val HEIGHT = 1f / ASPECT
        const val COPTER_X = 0.26f

        fun new(random: Random) = CopterWorld(
            y = HEIGHT / 2,
            walls = List(4) { index ->
                Triple(1.1f + index * 0.55f, HEIGHT / 2, 0.46f)
            },
        )
    }

    /** Gaps narrow with progress, but never below something clearly flyable. */
    fun gapFor(index: Int) = (0.46f - passed * 0.008f).coerceAtLeast(0.24f)

    fun step(dt: Float, random: Random): CopterWorld {
        if (over) return this
        val accel = if (lifting) -2.1f else 2.3f
        val speed = (vy + accel * dt).coerceIn(-1.1f, 1.3f)
        val y = y + speed * dt
        var passed = this.passed

        val walls = walls.map { (x, centre, gap) -> Triple(x - 0.62f * dt, centre, gap) }
            .map { (x, centre, gap) ->
                if (x < -0.15f) {
                    passed++
                    // A new wall's gap drifts from the last one rather than
                    // jumping, so the cave stays a cave.
                    val drift = (random.nextFloat() - 0.5f) * 0.4f
                    Triple(
                        2.0f,
                        (centre + drift).coerceIn(gapFor(passed), HEIGHT - gapFor(passed)),
                        gapFor(passed),
                    )
                } else {
                    Triple(x, centre, gap)
                }
            }

        val crashed = y < 0.02f || y > HEIGHT - 0.02f || walls.any { (x, centre, gap) ->
            abs(x - COPTER_X) < 0.06f && abs(y - centre) > gap / 2
        }
        return copy(y = y, vy = speed, walls = walls, passed = passed, over = crashed)
    }
}

@Composable
fun CopterScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(CopterWorld.new(random)) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "copter")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "copter", world.passed)
            best = ArcadeScores.best(context, "copter")
        }
    }

    GameShell(
        title = "Cave Copter",
        score = world.passed,
        best = best,
        status = when {
            world.over -> GameStatus.Over("Down she goes", "You passed ${world.passed} walls.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Action,
        labels = PadLabels(action = "HOLD TO CLIMB"),
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        // A tap gives a short burst of lift, which is far kinder on a touch
        // screen than requiring a genuine press-and-hold.
        onAction = { world = world.copy(vy = -0.75f, lifting = true) },
        aspect = CopterWorld.ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.tapAnywhere { world = world.copy(vy = -0.75f) }) {
            val unit = size.width
            drawStarField(28, seed = 7)

            world.walls.forEach { (x, centre, gap) ->
                val left = x * unit - unit * 0.05f
                val width = unit * 0.1f
                drawRect(
                    Arcade.Brown,
                    Offset(left, 0f),
                    Size(width, (centre - gap / 2) * unit),
                )
                drawRect(
                    Arcade.Brown,
                    Offset(left, (centre + gap / 2) * unit),
                    Size(width, size.height - (centre + gap / 2) * unit),
                )
            }

            val centre = Offset(CopterWorld.COPTER_X * unit, world.y * unit)
            drawRoundRect(
                Arcade.Amber,
                Offset(centre.x - unit * 0.045f, centre.y - unit * 0.022f),
                Size(unit * 0.09f, unit * 0.045f),
                CornerRadius(unit * 0.02f),
            )
            drawRect(
                Color(0xFF37474F),
                Offset(centre.x - unit * 0.06f, centre.y - unit * 0.036f),
                Size(unit * 0.12f, unit * 0.006f),
            )
            drawRect(
                Color(0xFF37474F),
                Offset(centre.x + unit * 0.035f, centre.y - unit * 0.005f),
                Size(unit * 0.05f, unit * 0.008f),
            )
        }
    }
}

// ------------------------------------------------------------------ Flap

data class FlapWorld(
    val y: Float = 0.5f,
    val vy: Float = 0f,
    val pipes: List<Pair<Float, Float>> = emptyList(),
    val score: Int = 0,
    val over: Boolean = false,
) {
    companion object {
        const val ASPECT = 0.72f
        val HEIGHT = 1f / ASPECT
        const val BIRD_X = 0.3f

        fun new() = FlapWorld(
            y = HEIGHT / 2,
            pipes = List(3) { index -> (1.2f + index * 0.7f) to HEIGHT / 2 },
        )
    }

    /** Wide to begin with, tightening slowly, with a floor that stays fair. */
    val gap: Float get() = (0.52f - score * 0.006f).coerceAtLeast(0.30f)

    fun flap() = if (over) this else copy(vy = -0.95f)

    fun step(dt: Float, random: Random): FlapWorld {
        if (over) return this
        val vy = (vy + 2.6f * dt).coerceAtMost(1.5f)
        val y = y + vy * dt
        var score = this.score

        val pipes = pipes.map { (x, centre) ->
            val moved = x - 0.62f * dt
            if (moved < -0.14f) {
                score++
                2.0f to (0.22f + random.nextFloat() * (HEIGHT - 0.44f))
            } else {
                moved to centre
            }
        }

        val crashed = y > HEIGHT - 0.03f || y < 0.03f || pipes.any { (x, centre) ->
            abs(x - BIRD_X) < 0.075f && abs(y - centre) > gap / 2
        }
        return copy(y = y, vy = vy, pipes = pipes, score = score, over = crashed)
    }
}

@Composable
fun FlapScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(FlapWorld.new()) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "flap")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, random)
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "flap", world.score)
            best = ArcadeScores.best(context, "flap")
        }
    }

    GameShell(
        title = "Flap",
        score = world.score,
        best = best,
        status = when {
            world.over -> GameStatus.Over("Clipped a pipe", "You passed ${world.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Action,
        labels = PadLabels(action = "FLAP"),
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onAction = { world = world.flap() },
        aspect = FlapWorld.ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.tapAnywhere { world = world.flap() }) {
            val unit = size.width

            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Arcade.Sky.copy(alpha = 0.35f), Arcade.Cloud.copy(alpha = 0.12f)),
                ),
                size = size,
            )

            world.pipes.forEach { (x, centre) ->
                val left = x * unit - unit * 0.07f
                val width = unit * 0.14f
                listOf(
                    0f to (centre - world.gap / 2) * unit,
                    (centre + world.gap / 2) * unit to size.height,
                ).forEach { (top, bottom) ->
                    drawRect(Arcade.Green, Offset(left, top), Size(width, bottom - top))
                    // A wider lip at the mouth of each pipe.
                    val lipY = if (top == 0f) bottom - unit * 0.03f else top
                    drawRect(
                        Arcade.Green.darken(0.18f),
                        Offset(left - unit * 0.012f, lipY),
                        Size(width + unit * 0.024f, unit * 0.03f),
                    )
                }
            }

            drawBird(
                center = Offset(FlapWorld.BIRD_X * unit, world.y * unit),
                radius = unit * 0.042f,
                flap = if (world.vy < 0) -1f else 1f,
                tilt = (world.vy * 22f).coerceIn(-25f, 55f),
            )
        }
    }
}

// ------------------------------------------------------------------ Ascend

data class Platform(val x: Float, val y: Float)

data class AscendWorld(
    val x: Float = 0.5f,
    val y: Float = 1.1f,
    val vy: Float = -1.1f,
    val platforms: List<Platform> = emptyList(),
    val height: Float = 0f,
    val over: Boolean = false,
) {
    companion object {
        const val ASPECT = 0.7f
        val HEIGHT = 1f / ASPECT
        const val PLATFORM_W = 0.22f

        fun new(random: Random) = AscendWorld(
            platforms = List(9) { index ->
                Platform(
                    x = if (index == 0) 0.5f else random.nextFloat() * (1f - PLATFORM_W),
                    y = HEIGHT - index * (HEIGHT / 9f),
                )
            },
        )
    }

    val score: Int get() = height.toInt()

    fun step(dt: Float, drift: Float, random: Random): AscendWorld {
        if (over) return this
        val vy = vy + 2.4f * dt
        var y = y + vy * dt
        // Wrapping round the sides is a feature, not a bug - it is often the
        // fastest route up.
        var x = (this.x + drift * dt + 1f) % 1f
        var platforms = platforms
        var height = height

        // Landing only ever happens on the way down, so you pass up through them.
        val landed = if (vy <= 0) {
            null
        } else {
            platforms.firstOrNull { platform ->
                y in platform.y - 0.03f..platform.y + 0.03f &&
                    x + 0.03f > platform.x && x - 0.03f < platform.x + PLATFORM_W
            }
        }
        val bounce = if (landed != null) -1.25f else vy

        // Scroll the world down once the player passes the upper third.
        if (y < HEIGHT * 0.38f) {
            val shift = HEIGHT * 0.38f - y
            y += shift
            height += shift * 30f
            platforms = platforms.map { it.copy(y = it.y + shift) }
                .map { platform ->
                    if (platform.y > HEIGHT) {
                        Platform(random.nextFloat() * (1f - PLATFORM_W), platform.y - HEIGHT)
                    } else {
                        platform
                    }
                }
        }

        return copy(
            x = x,
            y = y,
            vy = bounce,
            platforms = platforms,
            height = height,
            over = y > HEIGHT + 0.1f,
        )
    }
}

@Composable
fun AscendScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var world by remember(round) { mutableStateOf(AscendWorld.new(random)) }
    var drift by remember(round) { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "ascend")) }
    val countdown = rememberCountdown(round)

    FrameLoop(running = !world.over && !paused && countdown == 0) { dt ->
        world = world.step(dt, drift, random)
        // Steering decays, so a tap is a nudge rather than a permanent heading.
        drift *= 0.94f
    }
    LaunchedEffect(world.over) {
        if (world.over) {
            ArcadeScores.submit(context, "ascend", world.score)
            best = ArcadeScores.best(context, "ascend")
        }
    }

    GameShell(
        title = "Ascend",
        score = world.score,
        best = best,
        status = when {
            world.over -> GameStatus.Over("Missed the step", "You climbed ${world.score}.")
            paused -> GameStatus.Paused
            else -> null
        },
        pad = Pad.Horizontal,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        onDirection = { drift = if (it == Direction.Left) -0.85f else 0.85f },
        aspect = AscendWorld.ASPECT,
    ) { boardModifier ->
        GameCanvas(boardModifier.dragFraction { drift = (it - world.x) * 3.4f }) {
            val unit = size.width

            world.platforms.forEach { platform ->
                drawBevelBlock(
                    Offset(platform.x * unit, platform.y * unit),
                    Size(AscendWorld.PLATFORM_W * unit, unit * 0.028f),
                    Arcade.Green,
                )
            }

            val centre = Offset(world.x * unit, world.y * unit)
            drawGlossyBall(centre, unit * 0.035f, Arcade.Purple)
            listOf(-1, 1).forEach { side ->
                drawCircle(
                    Color.White,
                    unit * 0.012f,
                    Offset(centre.x + side * unit * 0.014f, centre.y - unit * 0.008f),
                )
            }
        }
    }
}
