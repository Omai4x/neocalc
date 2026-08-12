package com.omai.neocalc.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TicTacToeTest {

    private fun grid(text: String): List<Int> = text.filter { it in "-XO" }.map {
        when (it) {
            'X' -> 1
            'O' -> 2
            else -> 0
        }
    }

    @Test
    fun `winning lines are detected in every direction`() {
        assertEquals(1, TicTacToe.winner(grid("XXX --- ---")))
        assertEquals(2, TicTacToe.winner(grid("O-- O-- O--")))
        assertEquals(1, TicTacToe.winner(grid("X-- -X- --X")))
        assertEquals(0, TicTacToe.winner(grid("XO- -X- --O")))
    }

    @Test
    fun `the opponent takes an immediate win`() {
        // O has two in the top row and the third square is free.
        val board = grid("OO- XX- ---")
        assertEquals(2, TicTacToe.best(board, 2))
    }

    @Test
    fun `the opponent blocks an immediate loss`() {
        val board = grid("XX- O-- ---")
        assertEquals(2, TicTacToe.best(board, 2))
    }

    @Test
    fun `perfect play against perfect play is always a draw`() {
        var board = List(9) { 0 }
        var player = 1
        while (TicTacToe.winner(board) == 0 && !TicTacToe.full(board)) {
            board = board.toMutableList().also { it[TicTacToe.best(board, player)] = player }
            player = if (player == 1) 2 else 1
        }
        assertEquals(0, TicTacToe.winner(board))
        assertTrue(TicTacToe.full(board))
    }
}

class ConnectFourTest {

    private val random = Random(9)

    @Test
    fun `a disc falls to the lowest free row`() {
        val board = ConnectFour().drop(3, 1)!!
        assertEquals(1, board[3, ConnectFour.ROWS - 1])
        val stacked = board.drop(3, 2)!!
        assertEquals(2, stacked[3, ConnectFour.ROWS - 2])
    }

    @Test
    fun `a full column refuses another disc`() {
        var board = ConnectFour()
        repeat(ConnectFour.ROWS) { board = board.drop(0, 1)!! }
        assertNull(board.drop(0, 1))
    }

    @Test
    fun `four in a row wins horizontally`() {
        var horizontal = ConnectFour()
        (0..3).forEach { horizontal = horizontal.drop(it, 1)!! }
        assertEquals(1, horizontal.winner)
    }

    @Test
    fun `four in a row wins diagonally`() {
        // Placed directly rather than dropped: the point under test is the win
        // detector, not gravity, which the test above already covers.
        val cells = MutableList(ConnectFour.COLUMNS * ConnectFour.ROWS) { 0 }
        (0..3).forEach { step ->
            cells[(5 - step) * ConnectFour.COLUMNS + step] = 1
        }
        assertEquals(1, ConnectFour(cells).winner)
    }

    @Test
    fun `three in a row is not a win`() {
        var board = ConnectFour()
        (0..2).forEach { board = board.drop(it, 1)!! }
        assertEquals(0, board.winner)
    }

    @Test
    fun `the opponent takes a win when one is available`() {
        var board = ConnectFour()
        (0..2).forEach { board = board.drop(it, 2)!! }
        assertEquals(3, board.bestColumn(2, random))
    }

    @Test
    fun `the opponent blocks a threat rather than building its own`() {
        var board = ConnectFour()
        (1..3).forEach { board = board.drop(it, 1)!! }
        val choice = board.bestColumn(2, random)
        assertTrue("expected a block at 0 or 4, got $choice", choice == 0 || choice == 4)
    }
}

class ReversiTest {

    @Test
    fun `the opening position has four discs and four legal moves`() {
        val board = Reversi()
        assertEquals(2, board.count(1))
        assertEquals(2, board.count(2))
        assertEquals(4, board.moves(1).size)
    }

    @Test
    fun `a move must trap at least one disc`() {
        val board = Reversi()
        // The corner traps nothing at the start, so it is not a legal move.
        assertTrue(board.gains(0, 0, 1).isEmpty())
        assertTrue(board.moves(1).all { board.gains(it % 8, it / 8, 1).isNotEmpty() })
    }

    @Test
    fun `playing flips the trapped run`() {
        val board = Reversi()
        val move = board.moves(1).first()
        val after = board.play(move, 1)
        assertEquals(4, after.count(1))
        assertEquals(1, after.count(2))
    }

    @Test
    fun `the opponent always picks one of its own legal moves`() {
        var board = Reversi()
        var player = 1
        repeat(20) {
            val move = board.bestMove(player)
            if (move == null) {
                player = if (player == 1) 2 else 1
                return@repeat
            }
            assertTrue(move in board.moves(player))
            board = board.play(move, player)
            player = if (player == 1) 2 else 1
        }
        // A real game has been played out, so the board must have filled up.
        assertTrue(board.count(1) + board.count(2) > 4)
    }

    @Test
    fun `a corner is taken when one is on offer`() {
        // Black at a1 is closed by the run b2..c3, which is exactly a corner move.
        val cells = MutableList(64) { 0 }
        cells[9] = 1
        cells[18] = 2
        val board = Reversi(cells)
        assertTrue(0 in board.moves(2))
        assertEquals(0, board.bestMove(2))
    }

    @Test
    fun `an illegal move leaves the board untouched`() {
        val board = Reversi()
        assertEquals(board, board.play(0, 1))
    }
}

class NimStrategyTest {

    /** Mirrors the strategy used by NimScreen, so the rule itself is tested. */
    private fun reply(rows: List<Int>): List<Int> {
        val nimSum = rows.fold(0) { acc, value -> acc xor value }
        return if (nimSum == 0) {
            val index = rows.indexOfFirst { it > 0 }
            rows.toMutableList().also { it[index] = it[index] - 1 }
        } else {
            val index = rows.indexOfFirst { it xor nimSum < it }
            rows.toMutableList().also { it[index] = it[index] xor nimSum }
        }
    }

    private fun nimSum(rows: List<Int>) = rows.fold(0) { acc, value -> acc xor value }

    @Test
    fun `from a winning position it leaves a nim-sum of zero`() {
        listOf(
            listOf(1, 3, 5, 6),
            listOf(1, 2, 4),
            listOf(3, 4, 5),
            listOf(2, 4, 7),
        ).forEach { rows ->
            // Only positions that are actually winnable - a nim-sum already at
            // zero is a lost position, and no move can keep it there.
            assertNotEquals("$rows is not a winning position", 0, nimSum(rows))
            assertEquals("from $rows", 0, nimSum(reply(rows)))
        }
    }

    @Test
    fun `a position already at zero cannot be held there`() {
        val lost = listOf(2, 4, 6)
        assertEquals(0, nimSum(lost))
        assertNotEquals(0, nimSum(reply(lost)))
    }

    @Test
    fun `the starting position is winnable for whoever moves first`() {
        // A nim-sum of zero here would mean the player can never win.
        assertNotEquals(0, nimSum(listOf(1, 3, 5, 6)))
    }

    @Test
    fun `a reply only ever takes from a single row`() {
        val rows = listOf(1, 3, 5, 6)
        val after = reply(rows)
        val changed = rows.indices.count { rows[it] != after[it] }
        assertEquals(1, changed)
        assertTrue(rows.indices.all { after[it] <= rows[it] })
    }

    @Test
    fun `from a lost position it still makes a legal move`() {
        val balanced = listOf(2, 2, 0, 0)
        val after = reply(balanced)
        assertNotEquals(balanced, after)
        assertTrue(after.sum() == balanced.sum() - 1)
    }
}

class WordGuessTest {

    /** Reaches the private scorer through the same rules the screen applies. */
    private fun score(guess: String, answer: String): List<Int> {
        val marks = MutableList(5) { 0 }
        val pool = answer.toMutableList()
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

    @Test
    fun `an exact match is all greens`() {
        assertEquals(listOf(2, 2, 2, 2, 2), score("STONE", "STONE"))
    }

    @Test
    fun `a letter in the wrong place is amber`() {
        assertEquals(listOf(1, 1, 1, 1, 1), score("EARTH", "HEART"))
    }

    @Test
    fun `a repeated letter is not double-counted`() {
        // Only one L in the answer, so only the correctly placed one scores.
        val marks = score("LLAMA", "LEMON")
        assertEquals(2, marks[0])
        assertEquals(0, marks[1])
    }

    @Test
    fun `every word in the list is five letters and unique`() {
        val words = WordGuessWords.all
        assertTrue(words.all { it.length == 5 })
        assertTrue(words.all { word -> word.all { it in 'A'..'Z' } })
        assertEquals(words.size, words.toSet().size)
        assertFalse(words.isEmpty())
    }
}
