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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * Text on a Canvas.
 *
 * Compose's drawText needs a TextMeasurer and a font-family resolver, which is a
 * lot of ceremony for a number on a tile. The platform Paint underneath does it
 * in one call, and these boards draw dozens of labels per frame.
 */
internal fun DrawScope.drawLabel(
    text: String,
    center: Offset,
    color: Color,
    sizePx: Float,
    bold: Boolean = true,
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = if (bold) {
                android.graphics.Typeface.DEFAULT_BOLD
            } else {
                android.graphics.Typeface.DEFAULT
            }
        }
        // Baseline offset so the text is optically centred on the point given.
        drawText(text, center.x, center.y - (paint.descent() + paint.ascent()) / 2, paint)
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

// ------------------------------------------------------------------ 2048

data class Board2048(
    val tiles: List<Int> = List(SIZE * SIZE) { 0 },
    val score: Int = 0,
) {
    companion object {
        const val SIZE = 4

        fun new(random: Random) = Board2048().spawn(random).spawn(random)
    }

    operator fun get(x: Int, y: Int) = tiles[y * SIZE + x]

    fun spawn(random: Random): Board2048 {
        val empty = tiles.indices.filter { tiles[it] == 0 }
        if (empty.isEmpty()) return this
        val index = empty[random.nextInt(empty.size)]
        // Nine times out of ten a 2, as in the original.
        return copy(tiles = tiles.toMutableList().also { it[index] = if (random.nextInt(10) == 0) 4 else 2 })
    }

    /** Slides a single line towards index 0, merging equal neighbours once each. */
    private fun collapse(line: List<Int>): Pair<List<Int>, Int> {
        val packed = line.filter { it != 0 }.toMutableList()
        var gained = 0
        var index = 0
        while (index < packed.size - 1) {
            if (packed[index] == packed[index + 1]) {
                packed[index] *= 2
                gained += packed[index]
                packed.removeAt(index + 1)
            }
            index++
        }
        while (packed.size < SIZE) packed.add(0)
        return packed to gained
    }

    fun slide(direction: Direction): Pair<Board2048, Boolean> {
        val next = MutableList(SIZE * SIZE) { 0 }
        var gained = 0
        for (index in 0 until SIZE) {
            val line = (0 until SIZE).map { step ->
                when (direction) {
                    Direction.Left -> this[step, index]
                    Direction.Right -> this[SIZE - 1 - step, index]
                    Direction.Up -> this[index, step]
                    Direction.Down -> this[index, SIZE - 1 - step]
                }
            }
            val (collapsed, points) = collapse(line)
            gained += points
            collapsed.forEachIndexed { step, value ->
                when (direction) {
                    Direction.Left -> next[index * SIZE + step] = value
                    Direction.Right -> next[index * SIZE + (SIZE - 1 - step)] = value
                    Direction.Up -> next[step * SIZE + index] = value
                    Direction.Down -> next[(SIZE - 1 - step) * SIZE + index] = value
                }
            }
        }
        val moved = next != tiles
        return copy(tiles = next, score = score + gained) to moved
    }

    /** Stuck only when no direction changes anything - not merely when full. */
    val stuck: Boolean
        get() = tiles.none { it == 0 } && Direction.entries.none { slide(it).second }

    val won: Boolean get() = tiles.any { it >= 2048 }
}

private fun tileColor(value: Int): Color = when (value) {
    2 -> Color(0xFFB0BEC5)
    4 -> Color(0xFF90A4AE)
    8 -> Arcade.Amber
    16 -> Arcade.Coral
    32 -> Arcade.Red
    64 -> Arcade.Pink
    128 -> Arcade.Purple
    256 -> Arcade.Indigo
    512 -> Arcade.Blue
    1024 -> Arcade.Teal
    else -> Arcade.Green
}

@Composable
fun Game2048Screen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var board by remember(round) { mutableStateOf(Board2048.new(random)) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "2048")) }

    fun push(direction: Direction) {
        val (next, moved) = board.slide(direction)
        if (moved) board = next.spawn(random)
    }

    LaunchedEffect(board.stuck) {
        if (board.stuck) {
            ArcadeScores.submit(context, "2048", board.score)
            best = ArcadeScores.best(context, "2048")
        }
    }

    GameShell(
        title = "2048",
        score = board.score,
        best = best,
        status = when {
            board.stuck -> GameStatus.Over("No moves left", "You scored ${board.score}.")
            board.won -> GameStatus.Won("2048!", "Keep going if you like.")
            else -> null
        },
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { round++ },
        onDirection = ::push,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections(::push)) {
            val cell = size.width / Board2048.SIZE
            for (y in 0 until Board2048.SIZE) {
                for (x in 0 until Board2048.SIZE) {
                    val value = board[x, y]
                    val topLeft = Offset(x * cell + 4f, y * cell + 4f)
                    val tileSize = Size(cell - 8f, cell - 8f)
                    if (value == 0) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.06f),
                            topLeft,
                            tileSize,
                            CornerRadius(cell * 0.14f),
                        )
                    } else {
                        drawBevelBlock(topLeft, tileSize, tileColor(value), cell * 0.14f)
                        drawLabel(
                            text = value.toString(),
                            center = Offset(x * cell + cell / 2, y * cell + cell / 2),
                            color = Color.Black.copy(alpha = 0.78f),
                            sizePx = cell * if (value >= 1024) 0.26f else 0.34f,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Minesweeper

data class MineField(
    val mines: Set<Cell>,
    val revealed: Set<Cell> = emptySet(),
    val flags: Set<Cell> = emptySet(),
    val lost: Boolean = false,
    val started: Boolean = false,
) {
    companion object {
        const val SIZE = 9
        const val MINES = 10

        fun empty() = MineField(mines = emptySet())

        /** Mines are laid after the first tap, which is why it is always safe. */
        fun lay(first: Cell, random: Random): Set<Cell> {
            val safe = neighbours(first) + first
            val mines = mutableSetOf<Cell>()
            while (mines.size < MINES) {
                val candidate = Cell(random.nextInt(SIZE), random.nextInt(SIZE))
                if (candidate !in safe) mines += candidate
            }
            return mines
        }

        fun neighbours(cell: Cell) = buildList {
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val neighbour = Cell(cell.x + dx, cell.y + dy)
                if (neighbour.x in 0 until SIZE && neighbour.y in 0 until SIZE) add(neighbour)
            }
        }
    }

    fun count(cell: Cell) = neighbours(cell).count { it in mines }

    val won: Boolean
        get() = started && !lost && revealed.size == SIZE * SIZE - MINES

    fun reveal(cell: Cell, random: Random): MineField {
        if (lost || won || cell in flags) return this
        val field = if (!started) copy(mines = lay(cell, random), started = true) else this
        if (cell in field.mines) return field.copy(revealed = field.revealed + cell, lost = true)

        // Flood-fill through the empty region, which is what makes the game
        // playable rather than a square-by-square slog.
        val opened = field.revealed.toMutableSet()
        val queue = ArrayDeque(listOf(cell))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!opened.add(current)) continue
            if (field.count(current) == 0) {
                neighbours(current).forEach { if (it !in opened && it !in field.flags) queue += it }
            }
        }
        return field.copy(revealed = opened)
    }

    fun flag(cell: Cell) =
        if (cell in revealed) this else copy(flags = if (cell in flags) flags - cell else flags + cell)
}

@Composable
fun MinesweeperScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var field by remember(round) { mutableStateOf(MineField.empty()) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "mines")) }
    val score = field.revealed.size * 10

    LaunchedEffect(field.won) {
        if (field.won) {
            ArcadeScores.submit(context, "mines", score + 200)
            best = ArcadeScores.best(context, "mines")
        }
    }

    GameShell(
        title = "Minesweeper",
        score = score,
        best = best,
        extra = "FLAGS ${MineField.MINES - field.flags.size}",
        status = when {
            field.lost -> GameStatus.Over("Boom", "Long-press to flag next time.")
            field.won -> GameStatus.Won("Field cleared", "Nicely deduced.")
            else -> null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(
                columns = MineField.SIZE,
                rows = MineField.SIZE,
                onTap = { field = field.reveal(it, random) },
                onLongPress = { field = field.flag(it) },
            ),
        ) {
            val cell = size.width / MineField.SIZE
            for (y in 0 until MineField.SIZE) {
                for (x in 0 until MineField.SIZE) {
                    val position = Cell(x, y)
                    val topLeft = Offset(x * cell + 1.5f, y * cell + 1.5f)
                    val tile = Size(cell - 3f, cell - 3f)
                    val open = position in field.revealed

                    if (!open) {
                        drawBevelBlock(topLeft, tile, Arcade.Slate, cell * 0.12f)
                        if (position in field.flags) {
                            drawLabel("⚑", Offset(x * cell + cell / 2, y * cell + cell / 2), Arcade.Red, cell * 0.5f)
                        }
                    } else {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.10f),
                            topLeft,
                            tile,
                            CornerRadius(cell * 0.12f),
                        )
                        val centre = Offset(x * cell + cell / 2, y * cell + cell / 2)
                        if (position in field.mines) {
                            drawCircle(Arcade.Red, cell * 0.24f, centre)
                        } else {
                            val count = field.count(position)
                            if (count > 0) {
                                drawLabel(
                                    text = count.toString(),
                                    center = centre,
                                    color = Arcade.series[(count - 1) % Arcade.series.size],
                                    sizePx = cell * 0.46f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Lights Out

@Composable
fun LightsOutScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    val size = 5
    var round by remember { mutableIntStateOf(0) }

    fun toggle(board: Set<Cell>, cell: Cell): Set<Cell> {
        val affected = listOf(cell) + Direction.entries.map { cell.move(it) }
        return affected
            .filter { it.x in 0 until size && it.y in 0 until size }
            .fold(board) { acc, position ->
                if (position in acc) acc - position else acc + position
            }
    }

    // Generated by making random moves from "all off", which guarantees the
    // board can be solved - random bit patterns often cannot.
    var lights by remember(round) {
        mutableStateOf(
            (0 until 8).fold(emptySet<Cell>()) { acc, _ ->
                toggle(acc, Cell(random.nextInt(size), random.nextInt(size)))
            },
        )
    }
    var moves by remember(round) { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "lights")) }
    val solved = lights.isEmpty()
    val score = if (solved) (400 - moves * 10).coerceAtLeast(50) else 0

    LaunchedEffect(solved) {
        if (solved && moves > 0) {
            ArcadeScores.submit(context, "lights", score)
            best = ArcadeScores.best(context, "lights")
        }
    }

    GameShell(
        title = "Lights Out",
        score = score,
        best = best,
        extra = "MOVES $moves",
        status = if (solved && moves > 0) {
            GameStatus.Won("All off", "Solved in $moves moves.")
        } else {
            null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(size, size) { cell ->
                if (solved) return@tapCells
                lights = toggle(lights, cell)
                moves++
            },
        ) {
            val cell = this.size.width / size
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val on = Cell(x, y) in lights
                    val topLeft = Offset(x * cell + 5f, y * cell + 5f)
                    val tile = Size(cell - 10f, cell - 10f)
                    if (on) {
                        // A glow ring makes "on" unmistakable at a glance.
                        drawRoundRect(
                            Arcade.Amber.copy(alpha = 0.28f),
                            Offset(topLeft.x - 4f, topLeft.y - 4f),
                            Size(tile.width + 8f, tile.height + 8f),
                            CornerRadius(cell * 0.28f),
                        )
                        drawBevelBlock(topLeft, tile, Arcade.Amber, cell * 0.22f)
                    } else {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.07f),
                            topLeft,
                            tile,
                            CornerRadius(cell * 0.22f),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Fifteen

@Composable
fun SlidePuzzleScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    val size = 4
    var round by remember { mutableIntStateOf(0) }

    // Shuffled by playing real moves backwards from the solved board, so every
    // deal is solvable - half of all random permutations are not.
    var tiles by remember(round) {
        mutableStateOf(
            buildList {
                var board = (1 until size * size).toList() + 0
                var gap = size * size - 1
                repeat(160) {
                    val moves = Direction.entries.mapNotNull { direction ->
                        val x = gap % size + direction.dx
                        val y = gap / size + direction.dy
                        if (x in 0 until size && y in 0 until size) y * size + x else null
                    }
                    val pick = moves[random.nextInt(moves.size)]
                    board = board.toMutableList().also {
                        it[gap] = it[pick]
                        it[pick] = 0
                    }
                    gap = pick
                }
                addAll(board)
            },
        )
    }
    var moves by remember(round) { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "slide")) }
    val solved = tiles == (1 until size * size).toList() + 0
    val score = if (solved) (900 - moves * 4).coerceAtLeast(100) else 0

    LaunchedEffect(solved) {
        if (solved && moves > 0) {
            ArcadeScores.submit(context, "slide", score)
            best = ArcadeScores.best(context, "slide")
        }
    }

    GameShell(
        title = "Fifteen",
        score = score,
        best = best,
        extra = "MOVES $moves",
        status = if (solved && moves > 0) {
            GameStatus.Won("Solved", "$moves moves.")
        } else {
            null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(size, size) { cell ->
                if (solved) return@tapCells
                val index = cell.y * size + cell.x
                val gap = tiles.indexOf(0)
                val adjacent = abs(index % size - gap % size) + abs(index / size - gap / size) == 1
                if (adjacent) {
                    tiles = tiles.toMutableList().also {
                        it[gap] = it[index]
                        it[index] = 0
                    }
                    moves++
                }
            },
        ) {
            val cell = this.size.width / size
            tiles.forEachIndexed { index, value ->
                if (value == 0) return@forEachIndexed
                val x = index % size
                val y = index / size
                val correct = value == index + 1
                drawBevelBlock(
                    Offset(x * cell + 4f, y * cell + 4f),
                    Size(cell - 8f, cell - 8f),
                    if (correct) Arcade.Teal else Arcade.Slate,
                    cell * 0.16f,
                )
                drawLabel(
                    text = value.toString(),
                    center = Offset(x * cell + cell / 2, y * cell + cell / 2),
                    color = Color.Black.copy(alpha = 0.8f),
                    sizePx = cell * 0.4f,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Warehouse (Sokoban)

data class SokobanLevel(val rows: List<String>)

data class SokobanState(
    val level: Int,
    val player: Cell,
    val crates: Set<Cell>,
    val walls: Set<Cell>,
    val goals: Set<Cell>,
    val width: Int,
    val height: Int,
    val moves: Int = 0,
    val history: List<Pair<Cell, Set<Cell>>> = emptyList(),
) {
    companion object {
        /** Six hand-built levels. '#' wall, '@' player, '$' crate, '.' goal, '*' crate on goal. */
        val LEVELS = listOf(
            SokobanLevel(
                listOf(
                    "#######",
                    "#  .  #",
                    "#  $  #",
                    "#  @  #",
                    "#######",
                ),
            ),
            SokobanLevel(
                listOf(
                    "########",
                    "#   .  #",
                    "#  $   #",
                    "#  @   #",
                    "#   $  #",
                    "#   .  #",
                    "########",
                ),
            ),
            SokobanLevel(
                listOf(
                    "########",
                    "#.     #",
                    "#  $#  #",
                    "#  @   #",
                    "#  $   #",
                    "#     .#",
                    "########",
                ),
            ),
            SokobanLevel(
                listOf(
                    "#########",
                    "#..     #",
                    "#  $$   #",
                    "#   @   #",
                    "#       #",
                    "#########",
                ),
            ),
            SokobanLevel(
                listOf(
                    "#########",
                    "#.  #   #",
                    "#  $$   #",
                    "#  @  # #",
                    "#   $   #",
                    "# ..    #",
                    "#########",
                ),
            ),
            SokobanLevel(
                listOf(
                    "##########",
                    "#...     #",
                    "#  $$$   #",
                    "#   @    #",
                    "#   #    #",
                    "#        #",
                    "##########",
                ),
            ),
        )

        fun load(index: Int): SokobanState {
            val rows = LEVELS[index % LEVELS.size].rows
            var player = Cell(1, 1)
            val crates = mutableSetOf<Cell>()
            val walls = mutableSetOf<Cell>()
            val goals = mutableSetOf<Cell>()
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, symbol ->
                    val cell = Cell(x, y)
                    when (symbol) {
                        '#' -> walls += cell
                        '@' -> player = cell
                        '$' -> crates += cell
                        '.' -> goals += cell
                        '*' -> { crates += cell; goals += cell }
                    }
                }
            }
            return SokobanState(
                level = index,
                player = player,
                crates = crates,
                walls = walls,
                goals = goals,
                width = rows.maxOf { it.length },
                height = rows.size,
            )
        }
    }

    val solved: Boolean get() = crates.isNotEmpty() && crates.all { it in goals }

    fun push(direction: Direction): SokobanState {
        if (solved) return this
        val next = player.move(direction)
        if (next in walls) return this
        if (next in crates) {
            val beyond = next.move(direction)
            if (beyond in walls || beyond in crates) return this
            return copy(
                player = next,
                crates = crates - next + beyond,
                moves = moves + 1,
                history = history + (player to crates),
            )
        }
        return copy(player = next, moves = moves + 1, history = history + (player to crates))
    }

    /** Unlimited undo: a wedged crate should cost a move, not the whole level. */
    fun undo(): SokobanState {
        val last = history.lastOrNull() ?: return this
        return copy(
            player = last.first,
            crates = last.second,
            moves = moves + 1,
            history = history.dropLast(1),
        )
    }
}

@Composable
fun SokobanScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    var state by remember(level) { mutableStateOf(SokobanState.load(level)) }
    var totalScore by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "sokoban")) }

    LaunchedEffect(state.solved) {
        if (state.solved) {
            totalScore += (300 - state.moves * 4).coerceAtLeast(60)
            ArcadeScores.submit(context, "sokoban", totalScore)
            best = ArcadeScores.best(context, "sokoban")
            delay(1200)
            if (level < SokobanState.LEVELS.lastIndex) level++
        }
    }

    GameShell(
        title = "Warehouse",
        score = totalScore,
        best = best,
        extra = "LEVEL ${level + 1}/${SokobanState.LEVELS.size}",
        status = when {
            state.solved && level == SokobanState.LEVELS.lastIndex ->
                GameStatus.Won("All levels done", "Total $totalScore.")

            state.solved -> GameStatus.Won("Level clear", "Next one coming up…")
            else -> null
        },
        pad = Pad.Directional,
        onExit = onExit,
        onRestart = { state = SokobanState.load(level) },
        onDirection = { state = state.push(it) },
        extraAction = "Undo" to { state = state.undo() },
        aspect = state.width.toFloat() / state.height,
    ) { boardModifier ->
        GameCanvas(boardModifier.swipeDirections { state = state.push(it) }) {
            val cell = size.width / state.width

            state.goals.forEach {
                drawCircle(
                    Arcade.Amber.copy(alpha = 0.5f),
                    cell * 0.18f,
                    Offset(it.x * cell + cell / 2, it.y * cell + cell / 2),
                )
            }
            state.walls.forEach {
                drawBevelBlock(
                    Offset(it.x * cell, it.y * cell),
                    Size(cell, cell),
                    Arcade.Slate,
                    cell * 0.1f,
                )
            }
            state.crates.forEach { crate ->
                val onGoal = crate in state.goals
                drawBevelBlock(
                    Offset(crate.x * cell + 3f, crate.y * cell + 3f),
                    Size(cell - 6f, cell - 6f),
                    if (onGoal) Arcade.Green else Arcade.Brown,
                    cell * 0.12f,
                )
                // Crate banding, so a box looks like a box.
                drawLine(
                    Color.Black.copy(alpha = 0.28f),
                    Offset(crate.x * cell + 3f, crate.y * cell + cell / 2),
                    Offset(crate.x * cell + cell - 3f, crate.y * cell + cell / 2),
                    strokeWidth = cell * 0.07f,
                )
            }
            val centre = Offset(state.player.x * cell + cell / 2, state.player.y * cell + cell / 2)
            drawCircle(Arcade.Sky, cell * 0.3f, centre)
            drawCircle(Color.Black.copy(alpha = 0.7f), cell * 0.06f, centre.copy(x = centre.x - cell * 0.1f))
            drawCircle(Color.Black.copy(alpha = 0.7f), cell * 0.06f, centre.copy(x = centre.x + cell * 0.1f))
        }
    }
}

// ------------------------------------------------------------------ Memory

@Composable
fun MemoryScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    val columns = 4
    val rows = 5
    var round by remember { mutableIntStateOf(0) }

    val symbols = remember(round) {
        val faces = listOf("★", "●", "▲", "■", "♦", "♥", "♣", "♠", "◆", "✚")
        (faces + faces).shuffled(random)
    }
    var matched by remember(round) { mutableStateOf(setOf<Int>()) }
    var facing by remember(round) { mutableStateOf(listOf<Int>()) }
    var turns by remember(round) { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "memory")) }
    val done = matched.size == symbols.size
    val score = if (done) (900 - turns * 12).coerceAtLeast(100) else matched.size * 10

    // A wrong pair stays visible for a beat - long enough to actually commit it
    // to memory, which is the entire point of the game.
    LaunchedEffect(facing) {
        if (facing.size == 2) {
            turns++
            if (symbols[facing[0]] == symbols[facing[1]]) {
                delay(320)
                matched = matched + facing.toSet()
            } else {
                delay(1100)
            }
            facing = emptyList()
        }
    }

    LaunchedEffect(done) {
        if (done) {
            ArcadeScores.submit(context, "memory", score)
            best = ArcadeScores.best(context, "memory")
        }
    }

    GameShell(
        title = "Memory",
        score = score,
        best = best,
        extra = "TURNS $turns",
        status = if (done) GameStatus.Won("All pairs found", "$turns turns.") else null,
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
        aspect = columns.toFloat() / rows,
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(columns, rows) { cell ->
                val index = cell.y * columns + cell.x
                if (index in matched || index in facing || facing.size == 2) return@tapCells
                facing = facing + index
            },
        ) {
            val cellW = size.width / columns
            val cellH = size.height / rows
            symbols.forEachIndexed { index, symbol ->
                val x = index % columns
                val y = index / columns
                val open = index in matched || index in facing
                val topLeft = Offset(x * cellW + 5f, y * cellH + 5f)
                val tile = Size(cellW - 10f, cellH - 10f)
                if (open) {
                    drawBevelBlock(
                        topLeft,
                        tile,
                        if (index in matched) Arcade.Green else Arcade.Cloud,
                        cellW * 0.14f,
                    )
                    drawLabel(
                        symbol,
                        Offset(x * cellW + cellW / 2, y * cellH + cellH / 2),
                        Color.Black.copy(alpha = 0.75f),
                        cellH * 0.42f,
                    )
                } else {
                    drawBevelBlock(topLeft, tile, Arcade.Indigo, cellW * 0.14f)
                    drawCircle(
                        Color.White.copy(alpha = 0.18f),
                        cellW * 0.16f,
                        Offset(x * cellW + cellW / 2, y * cellH + cellH / 2),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Simon

@Composable
fun SimonScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var sequence by remember(round) { mutableStateOf(listOf(random.nextInt(4))) }
    var progress by remember(round) { mutableIntStateOf(0) }
    var lit by remember(round) { mutableStateOf(-1) }
    var showing by remember(round) { mutableStateOf(true) }
    var over by remember(round) { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "simon")) }
    val score = (sequence.size - 1) * 10

    // Playback is deliberately unhurried and slows nothing down as it grows -
    // memory should be the challenge, not perception.
    LaunchedEffect(sequence, round) {
        showing = true
        delay(600)
        sequence.forEach { step ->
            lit = step
            delay(520)
            lit = -1
            delay(220)
        }
        showing = false
        progress = 0
    }

    LaunchedEffect(over) {
        if (over) {
            ArcadeScores.submit(context, "simon", score)
            best = ArcadeScores.best(context, "simon")
        }
    }

    val colors = listOf(Arcade.Green, Arcade.Red, Arcade.Amber, Arcade.Sky)

    GameShell(
        title = "Simon",
        score = score,
        best = best,
        extra = "ROUND ${sequence.size}",
        status = when {
            over -> GameStatus.Over("Wrong step", "You reached round ${sequence.size}.")
            showing -> null
            else -> null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(2, 2) { cell ->
                if (showing || over) return@tapCells
                val index = cell.y * 2 + cell.x
                lit = index
                if (sequence[progress] == index) {
                    if (progress == sequence.lastIndex) {
                        sequence = sequence + random.nextInt(4)
                    } else {
                        progress++
                    }
                } else {
                    over = true
                }
            },
        ) {
            val half = size.width / 2
            for (index in 0 until 4) {
                val x = index % 2
                val y = index / 2
                val active = lit == index
                drawRoundRect(
                    color = if (active) colors[index] else colors[index].copy(alpha = 0.35f),
                    topLeft = Offset(x * half + 8f, y * half + 8f),
                    size = Size(half - 16f, half - 16f),
                    cornerRadius = CornerRadius(half * 0.16f),
                )
                if (active) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.5f),
                        topLeft = Offset(x * half + 8f, y * half + 8f),
                        size = Size(half - 16f, half - 16f),
                        cornerRadius = CornerRadius(half * 0.16f),
                        style = Stroke(width = 5f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Flood

@Composable
fun FloodScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val random = remember { Random(System.nanoTime()) }
    val size = 9
    val palette = listOf(Arcade.Red, Arcade.Amber, Arcade.Green, Arcade.Sky, Arcade.Purple, Arcade.Pink)
    val limit = 22
    var round by remember { mutableIntStateOf(0) }
    var board by remember(round) {
        mutableStateOf(List(size * size) { random.nextInt(palette.size) })
    }
    var moves by remember(round) { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "flood")) }

    fun flood(colour: Int) {
        val start = board[0]
        if (colour == start) return
        val region = mutableSetOf(0)
        val queue = ArrayDeque(listOf(0))
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            listOf(index - 1, index + 1, index - size, index + size).forEach { neighbour ->
                if (neighbour !in board.indices) return@forEach
                // Guard the row wrap: index-1 from column 0 is not a neighbour.
                if (abs(neighbour % size - index % size) > 1) return@forEach
                if (board[neighbour] == start && region.add(neighbour)) queue += neighbour
            }
        }
        board = board.mapIndexed { index, value -> if (index in region) colour else value }
        moves++
    }

    val won = board.all { it == board[0] }
    val lost = !won && moves >= limit
    val score = if (won) (limit - moves + 1) * 40 else 0

    LaunchedEffect(won) {
        if (won) {
            ArcadeScores.submit(context, "flood", score)
            best = ArcadeScores.best(context, "flood")
        }
    }

    GameShell(
        title = "Flood",
        score = score,
        best = best,
        extra = "MOVES ${limit - moves}",
        status = when {
            won -> GameStatus.Won("Board filled", "With ${limit - moves} moves to spare.")
            lost -> GameStatus.Over("Out of moves", "So close.")
            else -> null
        },
        pad = Pad.Board,
        onExit = onExit,
        onRestart = { round++ },
        // One extra row for the palette, drawn as part of the board so the
        // colour buttons scale with it and never fall outside the shell.
        aspect = size.toFloat() / (size + 1.6f),
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(size, size + 2) { cell ->
                if (won || lost) return@tapCells
                // The bottom two rows are the palette strip.
                if (cell.y >= size) {
                    val index = (cell.x * palette.size) / size
                    flood(index.coerceIn(0, palette.lastIndex))
                }
            },
        ) {
            val cell = this.size.width / size
            board.forEachIndexed { index, colour ->
                drawBevelBlock(
                    Offset((index % size) * cell + 1f, (index / size) * cell + 1f),
                    Size(cell - 2f, cell - 2f),
                    palette[colour],
                    cell * 0.16f,
                )
            }

            val stripTop = size * cell + cell * 0.2f
            val stripHeight = this.size.height - stripTop - 2f
            val swatch = this.size.width / palette.size
            palette.forEachIndexed { index, colour ->
                drawBevelBlock(
                    Offset(index * swatch + 3f, stripTop),
                    Size(swatch - 6f, stripHeight),
                    colour,
                    swatch * 0.16f,
                )
                // The colour you already own is marked, so it is obvious that
                // picking it again would waste a move.
                if (index == board[0]) {
                    drawCircle(
                        Color.Black.copy(alpha = 0.5f),
                        stripHeight * 0.18f,
                        Offset(index * swatch + swatch / 2, stripTop + stripHeight / 2),
                    )
                }
            }
        }
    }
}
