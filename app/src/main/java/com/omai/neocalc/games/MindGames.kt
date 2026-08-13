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
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ------------------------------------------------------------------ Tic-Tac-Toe

/** 0 empty, 1 player (X), 2 opponent (O). */
typealias Grid = List<Int>

object TicTacToe {
    val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6),
    )

    fun winner(grid: Grid): Int =
        LINES.firstOrNull { line -> grid[line[0]] != 0 && line.all { grid[it] == grid[line[0]] } }
            ?.let { grid[it[0]] } ?: 0

    fun full(grid: Grid) = grid.none { it == 0 }

    /**
     * Full minimax - the board is small enough to search exhaustively, so the
     * opponent is genuinely unbeatable. Depth is scored so it prefers a quick
     * win and a slow loss, which is what makes it feel like it is trying.
     */
    fun best(grid: Grid, player: Int): Int {
        var bestScore = Int.MIN_VALUE
        var move = grid.indexOfFirst { it == 0 }
        grid.indices.filter { grid[it] == 0 }.forEach { candidate ->
            val next = grid.toMutableList().also { it[candidate] = player }
            val score = -negamax(next, if (player == 1) 2 else 1, 1)
            if (score > bestScore) {
                bestScore = score
                move = candidate
            }
        }
        return move
    }

    private fun negamax(grid: Grid, player: Int, depth: Int): Int {
        val winner = winner(grid)
        if (winner != 0) return if (winner == player) 10 - depth else depth - 10
        if (full(grid)) return 0
        return grid.indices.filter { grid[it] == 0 }.maxOf { candidate ->
            val next = grid.toMutableList().also { it[candidate] = player }
 -negamax(next, if (player == 1) 2 else 1, depth + 1)
        }
    }
}

@Composable
fun TicTacToeScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    var round by remember { mutableIntStateOf(0) }
    var grid by remember(round) { mutableStateOf(List(9) { 0 }) }
    var wins by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "tictactoe")) }
    val winner = TicTacToe.winner(grid)
    val finished = winner != 0 || TicTacToe.full(grid)
    val score = wins * 100 + draws * 30

    LaunchedEffect(grid) {
        // The opponent takes a beat before replying, so its move is visible as
        // a move rather than appearing in the same frame as yours.
        if (winner == 0 && !TicTacToe.full(grid) && grid.count { it != 0 } % 2 == 1) {
            delay(380)
            grid = grid.toMutableList().also { it[TicTacToe.best(grid, 2)] = 2 }
        }
    }

    LaunchedEffect(finished) {
        if (finished) {
            if (winner == 1) wins++ else if (winner == 0) draws++
            ArcadeScores.submit(context, "tictactoe", score)
            best = ArcadeScores.best(context, "tictactoe")
        }
    }

    GameShell(
        title = "Tic-Tac-Toe",
        score = score,
        best = best,
        extra = "DRAWS $draws",
        status = when {
            winner == 1 -> GameStatus.Won("You win", "Against a perfect player.")
            winner == 2 -> GameStatus.Over("You lose", "It never blunders.")
            finished -> GameStatus.Won("Draw", "The best result available.")
            else -> null
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(3, 3) { cell ->
                val index = cell.y * 3 + cell.x
                if (grid[index] == 0 && !finished && grid.count { it != 0 } % 2 == 0) {
                    grid = grid.toMutableList().also { it[index] = 1 }
                }
            },
        ) {
            val cell = size.width / 3
            for (line in 1 until 3) {
                drawLine(
                    Color.White.copy(alpha = 0.2f),
                    Offset(line * cell, cell * 0.1f),
                    Offset(line * cell, size.width - cell * 0.1f),
                    strokeWidth = 4f,
                )
                drawLine(
                    Color.White.copy(alpha = 0.2f),
                    Offset(cell * 0.1f, line * cell),
                    Offset(size.width - cell * 0.1f, line * cell),
                    strokeWidth = 4f,
                )
            }
            grid.forEachIndexed { index, value ->
                if (value == 0) return@forEachIndexed
                val centre = Offset(
                    (index % 3) * cell + cell / 2,
                    (index / 3) * cell + cell / 2,
                )
                val arm = cell * 0.26f
                if (value == 1) {
                    listOf(1f to 1f, 1f to -1f).forEach { (sx, sy) ->
                        drawLine(
                            Arcade.Coral,
                            Offset(centre.x - arm * sx, centre.y - arm * sy),
                            Offset(centre.x + arm * sx, centre.y + arm * sy),
                            strokeWidth = cell * 0.09f,
                        )
                    }
                } else {
                    drawCircle(Arcade.Sky, arm, centre, style = Stroke(width = cell * 0.09f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Connect Four

data class ConnectFour(
    val cells: List<Int> = List(COLUMNS * ROWS) { 0 },
) {
    companion object {
        const val COLUMNS = 7
        const val ROWS = 6

        fun winnerOf(cells: List<Int>): Int {
            for (y in 0 until ROWS) for (x in 0 until COLUMNS) {
                val player = cells[y * COLUMNS + x]
                if (player == 0) continue
                listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1).forEach { (dx, dy) ->
                    val line = (0 until 4).map { step -> (x + dx * step) to (y + dy * step) }
                    if (line.all { (lx, ly) ->
                            lx in 0 until COLUMNS && ly in 0 until ROWS &&
                                cells[ly * COLUMNS + lx] == player
                        }
                    ) {
                        return player
                    }
                }
            }
            return 0
        }
    }

    operator fun get(x: Int, y: Int) = cells[y * COLUMNS + x]

    val winner: Int get() = winnerOf(cells)
    val full: Boolean get() = cells.none { it == 0 }

    fun drop(column: Int, player: Int): ConnectFour? {
        val row = (ROWS - 1 downTo 0).firstOrNull { this[column, it] == 0 } ?: return null
        return copy(cells = cells.toMutableList().also { it[row * COLUMNS + column] = player })
    }

    /**
     * Three-ply search: take a win, block a loss, otherwise prefer the centre.
     * Deep enough to punish careless play, shallow enough to stay beatable.
     */
    fun bestColumn(player: Int, random: Random): Int {
        val opponent = if (player == 1) 2 else 1
        (0 until COLUMNS).forEach { column ->
            drop(column, player)?.let { if (it.winner == player) return column }
        }
        (0 until COLUMNS).forEach { column ->
            drop(column, opponent)?.let { if (it.winner == opponent) return column }
        }
        // Avoid handing the opponent an immediate win with the reply.
        val safe = (0 until COLUMNS).filter { column ->
            val after = drop(column, player) ?: return@filter false
            (0 until COLUMNS).none { reply ->
                after.drop(reply, opponent)?.winner == opponent
            }
        }
        val candidates = safe.ifEmpty { (0 until COLUMNS).filter { drop(it, player) != null } }
        if (candidates.isEmpty()) return 0
        // Centre columns are worth more; ties broken randomly so it varies.
        return candidates.shuffled(random).maxByOrNull { COLUMNS / 2 - abs(it - COLUMNS / 2) } ?: 0
    }
}

@Composable
fun ConnectFourScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var board by remember(round) { mutableStateOf(ConnectFour()) }
    var wins by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "connect4")) }
    val winner = board.winner
    val finished = winner != 0 || board.full

    LaunchedEffect(board) {
        if (winner == 0 && !board.full && board.cells.count { it != 0 } % 2 == 1) {
            delay(420)
            board = board.drop(board.bestColumn(2, random), 2) ?: board
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            if (winner == 1) wins++
            ArcadeScores.submit(context, "connect4", wins * 100)
            best = ArcadeScores.best(context, "connect4")
        }
    }

    GameShell(
        title = "Connect Four",
        score = wins * 100,
        best = best,
        status = when {
            winner == 1 -> GameStatus.Won("Four in a row", "Nicely built.")
            winner == 2 -> GameStatus.Over("They got four", "Watch the diagonals.")
            finished -> GameStatus.Won("Full board", "A draw.")
            else -> null
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++ },
        aspect = ConnectFour.COLUMNS.toFloat() / ConnectFour.ROWS,
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(ConnectFour.COLUMNS, ConnectFour.ROWS) { cell ->
                if (!finished && board.cells.count { it != 0 } % 2 == 0) {
                    board = board.drop(cell.x, 1) ?: board
                }
            },
        ) {
            val cell = size.width / ConnectFour.COLUMNS
            drawRoundRect(Arcade.Blue.copy(alpha = 0.45f), Offset.Zero, size, CornerRadius(cell * 0.2f))
            for (y in 0 until ConnectFour.ROWS) {
                for (x in 0 until ConnectFour.COLUMNS) {
                    val centre = Offset(x * cell + cell / 2, y * cell + cell / 2)
                    when (board[x, y]) {
                        0 -> drawCircle(Color.Black.copy(alpha = 0.35f), cell * 0.38f, centre)
                        1 -> drawGlossyBall(centre, cell * 0.38f, Arcade.Red)
                        else -> drawGlossyBall(centre, cell * 0.38f, Arcade.Amber)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Reversi

data class Reversi(val cells: List<Int> = start()) {
    companion object {
        const val SIZE = 8

        fun start(): List<Int> = MutableList(SIZE * SIZE) { 0 }.also {
            it[27] = 2; it[28] = 1; it[35] = 1; it[36] = 2
        }

        private val DIRECTIONS = listOf(
 -1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1,
        )
    }

    operator fun get(x: Int, y: Int) = cells[y * SIZE + x]

    /** The discs a move at (x, y) would flip; empty means the move is illegal. */
    fun gains(x: Int, y: Int, player: Int): List<Int> {
        if (this[x, y] != 0) return emptyList()
        val opponent = if (player == 1) 2 else 1
        return DIRECTIONS.flatMap { (dx, dy) ->
            val run = mutableListOf<Int>()
            var cx = x + dx
            var cy = y + dy
            while (cx in 0 until SIZE && cy in 0 until SIZE && this[cx, cy] == opponent) {
                run += cy * SIZE + cx
                cx += dx
                cy += dy
            }
            // Only counts when the run is closed by one of your own discs.
            if (cx in 0 until SIZE && cy in 0 until SIZE && this[cx, cy] == player) run else emptyList()
        }
    }

    fun moves(player: Int): List<Int> =
        (0 until SIZE * SIZE).filter { gains(it % SIZE, it / SIZE, player).isNotEmpty() }

    fun play(index: Int, player: Int): Reversi {
        val flips = gains(index % SIZE, index / SIZE, player)
        if (flips.isEmpty()) return this
        return copy(
            cells = cells.toMutableList().also { next ->
                next[index] = player
                flips.forEach { next[it] = player }
            },
        )
    }

    fun count(player: Int) = cells.count { it == player }

    /**
     * Greedy with a positional weighting: corners are permanent, the squares
     * next to them hand a corner over. That single idea is most of Reversi.
     */
    fun bestMove(player: Int): Int? {
        val moves = moves(player)
        if (moves.isEmpty()) return null
        fun weight(index: Int): Int {
            val x = index % SIZE
            val y = index / SIZE
            val corner = (x == 0 || x == SIZE - 1) && (y == 0 || y == SIZE - 1)
            val nextToCorner = (x <= 1 || x >= SIZE - 2) && (y <= 1 || y >= SIZE - 2)
            val edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1
            return when {
                corner -> 60
                nextToCorner -> -18
                edge -> 8
                else -> 0
            }
        }
        return moves.maxByOrNull { weight(it) + gains(it % SIZE, it / SIZE, player).size }
    }
}

@Composable
fun ReversiScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    var round by remember { mutableIntStateOf(0) }
    var board by remember(round) { mutableStateOf(Reversi()) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "reversi")) }
    var turn by remember(round) { mutableIntStateOf(1) }

    val playerMoves = board.moves(1)
    val aiMoves = board.moves(2)
    val finished = playerMoves.isEmpty() && aiMoves.isEmpty()
    val score = board.count(1)

    LaunchedEffect(board, turn) {
        when {
            finished -> Unit
            turn == 2 && aiMoves.isNotEmpty() -> {
                delay(450)
                board.bestMove(2)?.let { board = board.play(it, 2) }
                turn = 1
            }
            // A player with no legal move passes rather than the game stalling.
            turn == 2 -> turn = 1
            turn == 1 && playerMoves.isEmpty() -> turn = 2
        }
    }

    LaunchedEffect(finished) {
        if (finished && board.count(1) > board.count(2)) {
            ArcadeScores.submit(context, "reversi", score)
            best = ArcadeScores.best(context, "reversi")
        }
    }

    GameShell(
        title = "Reversi",
        score = score,
        best = best,
        extra = "THEM ${board.count(2)}",
        status = when {
            !finished -> null
            board.count(1) > board.count(2) ->
                GameStatus.Won("You win", "${board.count(1)} - ${board.count(2)}")

            board.count(1) < board.count(2) ->
                GameStatus.Over("You lose", "${board.count(1)} - ${board.count(2)}")

            else -> GameStatus.Won("Dead heat", "${board.count(1)} apiece.")
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++ },
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(Reversi.SIZE, Reversi.SIZE) { cell ->
                val index = cell.y * Reversi.SIZE + cell.x
                if (turn == 1 && index in playerMoves) {
                    board = board.play(index, 1)
                    turn = 2
                }
            },
        ) {
            val cell = size.width / Reversi.SIZE
            drawRect(Color(0xFF1B5E20).copy(alpha = 0.75f), Offset.Zero, size)
            for (line in 1 until Reversi.SIZE) {
                drawLine(Color.Black.copy(alpha = 0.3f), Offset(line * cell, 0f), Offset(line * cell, size.height))
                drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, line * cell), Offset(size.width, line * cell))
            }
            board.cells.forEachIndexed { index, value ->
                val centre = Offset(
                    (index % Reversi.SIZE) * cell + cell / 2,
                    (index / Reversi.SIZE) * cell + cell / 2,
                )
                when {
                    value == 1 -> drawGlossyBall(centre, cell * 0.38f, Color(0xFF212121))
                    value == 2 -> drawGlossyBall(centre, cell * 0.38f, Arcade.Cloud)
                    // Legal moves are marked, which turns a confusing board into
                    // a readable one without playing the game for you.
                    turn == 1 && index in playerMoves ->
                        drawCircle(Arcade.Lime.copy(alpha = 0.5f), cell * 0.12f, centre)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Nim

@Composable
fun NimScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    // Not the classic 1-3-5-7: that has a nim-sum of zero, which means the
    // player moving first loses to perfect play every single time. 1-3-5-6 is a
    // winning position, so the game is actually winnable.
    var rows by remember(round) { mutableStateOf(listOf(1, 3, 5, 6)) }
    var yourTurn by remember(round) { mutableStateOf(true) }
    var wins by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "nim")) }
    val empty = rows.all { it == 0 }

    LaunchedEffect(rows, yourTurn) {
        if (!yourTurn && !empty) {
            delay(600)
            // Perfect play: leave a nim-sum of zero. When it is already zero the
            // position is lost, so it takes one and waits for a mistake.
            val nimSum = rows.fold(0) { acc, value -> acc xor value }
            rows = if (nimSum == 0) {
                val index = rows.indexOfFirst { it > 0 }
                rows.toMutableList().also { it[index] = it[index] - 1 }
            } else {
                val index = rows.indexOfFirst { it xor nimSum < it }
                rows.toMutableList().also { it[index] = it[index] xor nimSum }
            }
            yourTurn = true
        }
    }

    LaunchedEffect(empty) {
        if (empty) {
            // Whoever took the last match wins, so the loser is on turn now.
            if (!yourTurn) wins++
            ArcadeScores.submit(context, "nim", wins * 100)
            best = ArcadeScores.best(context, "nim")
        }
    }

    GameShell(
        title = "Nim",
        score = wins * 100,
        best = best,
        status = when {
            !empty -> null
            !yourTurn -> GameStatus.Won("You took the last one", "Perfect play.")
            else -> GameStatus.Over("They took the last one", "Try leaving an even split.")
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++ },
        aspect = 1.1f,
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(8, rows.size) { cell ->
                if (!yourTurn || empty) return@tapCells
                val row = cell.y
                // Tapping a match takes it and everything to its right in the row.
                val remaining = rows[row]
                if (cell.x < remaining) {
                    rows = rows.toMutableList().also { it[row] = cell.x }
                    yourTurn = false
                }
            },
        ) {
            val cellW = size.width / 8
            val cellH = size.height / rows.size
            rows.forEachIndexed { row, count ->
                repeat(count) { index ->
                    val x = index * cellW + cellW / 2
                    val y = row * cellH + cellH / 2
                    drawRoundRect(
                        Arcade.Cloud,
                        Offset(x - cellW * 0.1f, y - cellH * 0.3f),
                        Size(cellW * 0.2f, cellH * 0.6f),
                        CornerRadius(cellW * 0.1f),
                    )
                    // The head of the match - the detail that makes it a match.
                    drawCircle(Arcade.Red, cellW * 0.15f, Offset(x, y - cellH * 0.28f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Math Blitz

private data class Question(val prompt: String, val answer: Int, val options: List<Int>)

private fun buildQuestion(streak: Int, random: Random): Question {
    val level = (streak / 4).coerceAtMost(4)
    val a = random.nextInt(2, 6 + level * 8)
    val b = random.nextInt(2, 5 + level * 5)
    val (prompt, answer) = when (random.nextInt(if (level < 2) 2 else 4)) {
        0 -> "$a + $b" to a + b
        1 -> "${a + b} − $b" to a
        2 -> "$a × $b" to a * b
        else -> "${a * b} ÷ $b" to a
    }
    // Distractors sit close to the answer, so the question can't be eyeballed.
    val wrong = buildSet {
        while (size < 3) {
            val delta = random.nextInt(1, 6 + level * 3) * if (random.nextBoolean()) 1 else -1
            val candidate = answer + delta
            if (candidate != answer && candidate >= 0) add(candidate)
        }
    }
    return Question(prompt, answer, (wrong + answer).shuffled(random))
}

@Composable
fun MathBlitzScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    var streak by remember(round) { mutableIntStateOf(0) }
    var score by remember(round) { mutableIntStateOf(0) }
    var question by remember(round) { mutableStateOf(buildQuestion(0, random)) }
    var timeLeft by remember(round) { mutableStateOf(12f) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "mathblitz")) }
    val countdown = rememberCountdown(round, seconds = 2)
    val over = timeLeft <= 0f

    FrameLoop(running = !over && !paused && countdown == 0) { dt -> timeLeft -= dt }
    LaunchedEffect(over) {
        if (over) {
            ArcadeScores.submit(context, "mathblitz", score)
            best = ArcadeScores.best(context, "mathblitz")
        }
    }

    GameShell(
        title = "Math Blitz",
        score = score,
        best = best,
        extra = "STREAK $streak",
        status = when {
            over -> GameStatus.Over("Out of time", "You scored $score.")
            paused -> GameStatus.Paused
            else -> null
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++; paused = false },
        countdown = countdown,
        paused = paused,
        onPause = { paused = !paused },
        aspect = 1f,
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(2, 3) { cell ->
                if (over || countdown > 0) return@tapCells
                // The top row is the prompt; the four answers are below it.
                if (cell.y == 0) return@tapCells
                val index = (cell.y - 1) * 2 + cell.x
                val chosen = question.options.getOrNull(index) ?: return@tapCells
                if (chosen == question.answer) {
                    streak++
                    score += 10 + streak * 2
                    // Right answers buy time back, capped so it can't snowball.
                    timeLeft = (timeLeft + 2.2f).coerceAtMost(15f)
                } else {
                    streak = 0
                    timeLeft -= 1.6f
                }
                question = buildQuestion(streak, random)
            },
        ) {
            val third = size.height / 3
            drawLabel(
                text = question.prompt,
                center = Offset(size.width / 2, third / 2),
                color = Arcade.Cloud,
                sizePx = third * 0.42f,
            )
            // The timer bar under the prompt is the whole pressure of the game.
            drawRoundRect(
                Arcade.Slate.copy(alpha = 0.5f),
                Offset(size.width * 0.1f, third * 0.82f),
                Size(size.width * 0.8f, third * 0.09f),
                CornerRadius(third * 0.05f),
            )
            drawRoundRect(
                if (timeLeft < 4f) Arcade.Red else Arcade.Green,
                Offset(size.width * 0.1f, third * 0.82f),
                Size(size.width * 0.8f * (timeLeft / 15f).coerceIn(0f, 1f), third * 0.09f),
                CornerRadius(third * 0.05f),
            )

            question.options.forEachIndexed { index, option ->
                val x = index % 2
                val y = index / 2 + 1
                drawBevelBlock(
                    Offset(x * size.width / 2 + 8f, y * third + 8f),
                    Size(size.width / 2 - 16f, third - 16f),
                    Arcade.series[index % Arcade.series.size],
                    24f,
                )
                drawLabel(
                    text = option.toString(),
                    center = Offset(x * size.width / 2 + size.width / 4, y * third + third / 2),
                    color = Color.Black.copy(alpha = 0.8f),
                    sizePx = third * 0.3f,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Word Guess

/** Exposed as an object so the word list itself can be tested for shape. */
internal object WordGuessWords {
    val all: List<String> get() = WORD_LIST
}

private val WORD_LIST = listOf(
    "APPLE", "BRAVE", "CHART", "DRIFT", "EAGER", "FLINT", "GRACE", "HONEY",
    "INDEX", "JOLLY", "KNEEL", "LEMON", "MIRTH", "NOBLE", "OCEAN", "PLUMB",
    "QUIET", "RIVER", "STONE", "TIGER", "ULTRA", "VIVID", "WHALE", "YIELD",
    "ZEBRA", "BLAZE", "CRISP", "DWELL", "ELBOW", "FROST", "GLIDE", "HEARD",
    "IVORY", "JUMPY", "KIOSK", "LUNAR", "MOTOR", "NURSE", "ORBIT", "PIANO",
    "QUERY", "ROAST", "SHINE", "TRUCE", "UNITY", "VAULT", "WINCE", "YOUTH",
)

/** Green = right place, Amber = wrong place, Slate = absent. */
private fun scoreGuess(guess: String, answer: String): List<Int> {
    val marks = MutableList(5) { 0 }
    val pool = answer.toMutableList()
    // Exact matches are claimed first, or a repeated letter double-counts.
    guess.forEachIndexed { index, letter ->
        if (answer[index] == letter) {
            marks[index] = 2
            pool.remove(letter)
        }
    }
    guess.forEachIndexed { index, letter ->
        if (marks[index] == 0 && pool.remove(letter)) marks[index] = 1
    }
    return marks
}

@Composable
fun WordGuessScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controls = remember { ControlState() }
    val random = remember { Random(System.nanoTime()) }
    var round by remember { mutableIntStateOf(0) }
    val answer by remember(round) { mutableStateOf(WORD_LIST[random.nextInt(WORD_LIST.size)]) }
    var guesses by remember(round) { mutableStateOf(listOf<String>()) }
    var current by remember(round) { mutableStateOf("") }
    var best by remember { mutableIntStateOf(ArcadeScores.best(context, "word")) }

    val solved = guesses.lastOrNull() == answer
    val exhausted = guesses.size >= 6 && !solved
    val score = if (solved) (7 - guesses.size) * 100 else 0

    LaunchedEffect(solved) {
        if (solved) {
            ArcadeScores.submit(context, "word", score)
            best = ArcadeScores.best(context, "word")
        }
    }

    // Six rows of five, then four rows of an on-screen keyboard.
    val keyRows = listOf("QWERTYU", "IOPASDF", "GHJKLZX", "CVBNM←✓")

    GameShell(
        title = "Word Guess",
        score = score,
        best = best,
        extra = "TRY ${(guesses.size + 1).coerceAtMost(6)}/6",
        status = when {
            solved -> GameStatus.Won("Got it", "$answer in ${guesses.size}.")
            exhausted -> GameStatus.Over("Out of guesses", "It was $answer.")
            else -> null
        },
        controls = Controls.Board,
        state = controls,
        onExit = onExit,
        onRestart = { round++ },
        aspect = 0.62f,
    ) { boardModifier ->
        GameCanvas(
            boardModifier.tapCells(7, 10) { cell ->
                if (solved || exhausted || cell.y < 6) return@tapCells
                val row = keyRows.getOrNull(cell.y - 6) ?: return@tapCells
                when (val key = row.getOrNull(cell.x) ?: return@tapCells) {
                    '←' -> current = current.dropLast(1)
                    '✓' -> if (current.length == 5 && current in WORD_LIST) {
                        guesses = guesses + current
                        current = ""
                    }

                    else -> if (current.length < 5) current += key
                }
            },
        ) {
            val cellW = size.width / 7
            val rowH = size.height / 10

            // Guess rows
            repeat(6) { row ->
                val word = guesses.getOrNull(row) ?: if (row == guesses.size) current else ""
                val marks = guesses.getOrNull(row)?.let { scoreGuess(it, answer) }
                repeat(5) { column ->
                    val left = size.width * 0.07f + column * (size.width * 0.172f)
                    val topLeft = Offset(left, row * rowH + 3f)
                    val tile = Size(size.width * 0.155f, rowH - 6f)
                    val colour = when (marks?.getOrNull(column)) {
                        2 -> Arcade.Green
                        1 -> Arcade.Amber
                        0 -> Arcade.Slate
                        else -> null
                    }
                    if (colour != null) {
                        drawBevelBlock(topLeft, tile, colour, 10f)
                    } else {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.08f),
                            topLeft,
                            tile,
                            CornerRadius(10f),
                            style = Stroke(width = 2.5f),
                        )
                    }
                    word.getOrNull(column)?.let { letter ->
                        drawLabel(
                            letter.toString(),
                            Offset(topLeft.x + tile.width / 2, topLeft.y + tile.height / 2),
                            if (colour != null) Color.Black.copy(alpha = 0.8f) else Arcade.Cloud,
                            rowH * 0.46f,
                        )
                    }
                }
            }

            // Keyboard
            keyRows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { column, key ->
                    val topLeft = Offset(column * cellW + 3f, (6 + rowIndex) * rowH + 3f)
                    val tile = Size(cellW - 6f, rowH - 6f)
                    val used = guesses.flatMap { guess ->
                        scoreGuess(guess, answer).mapIndexedNotNull { index, mark ->
                            if (guess[index] == key) mark else null
                        }
                    }.maxOrNull()
                    val colour = when {
                        key == '✓' -> Arcade.Green
                        key == '←' -> Arcade.Coral
                        used == 2 -> Arcade.Green
                        used == 1 -> Arcade.Amber
                        used == 0 -> Arcade.Slate.darken(0.3f)
                        else -> Arcade.Slate
                    }
                    drawBevelBlock(topLeft, tile, colour, 8f)
                    drawLabel(
                        key.toString(),
                        Offset(topLeft.x + tile.width / 2, topLeft.y + tile.height / 2),
                        Color.Black.copy(alpha = 0.78f),
                        rowH * 0.38f,
                    )
                }
            }
        }
    }
}
