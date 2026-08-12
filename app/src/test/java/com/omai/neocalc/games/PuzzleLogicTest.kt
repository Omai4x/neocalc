package com.omai.neocalc.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Board2048Test {

    private fun board(vararg values: Int) = Board2048(values.toList())

    @Test
    fun `equal neighbours merge into their sum`() {
        val start = board(
            2, 2, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        val (after, moved) = start.slide(Direction.Left)
        assertTrue(moved)
        assertEquals(4, after.tiles[0])
        assertEquals(4, after.score)
    }

    @Test
    fun `a tile merges only once per slide`() {
        // 2,2,2,2 becomes 4,4 - not 8.
        val start = board(
            2, 2, 2, 2,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        val (after, _) = start.slide(Direction.Left)
        assertEquals(listOf(4, 4, 0, 0), after.tiles.take(4))
    }

    @Test
    fun `sliding into a wall with nothing to merge is not a move`() {
        val start = board(
            2, 4, 8, 16,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        assertFalse(start.slide(Direction.Left).second)
        assertTrue(start.slide(Direction.Down).second)
    }

    @Test
    fun `a full board is only stuck when no direction helps`() {
        val checker = board(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        assertTrue(checker.stuck)

        val mergeable = board(
            2, 2, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        assertFalse(mergeable.stuck)
    }

    @Test
    fun `a new board has exactly two tiles`() {
        repeat(20) { seed ->
            val fresh = Board2048.new(Random(seed))
            assertEquals(2, fresh.tiles.count { it != 0 })
            assertTrue(fresh.tiles.all { it == 0 || it == 2 || it == 4 })
        }
    }
}

class MineFieldTest {

    @Test
    fun `the first tap is never a mine`() {
        repeat(40) { seed ->
            val first = Cell(seed % MineField.SIZE, (seed * 3) % MineField.SIZE)
            val field = MineField.empty().reveal(first, Random(seed))
            assertFalse("seed $seed blew up on the first tap", field.lost)
            assertTrue(first in field.revealed)
        }
    }

    @Test
    fun `the field always holds exactly the advertised number of mines`() {
        val field = MineField.empty().reveal(Cell(4, 4), Random(1))
        assertEquals(MineField.MINES, field.mines.size)
    }

    @Test
    fun `flags block a reveal, so a marked square cannot be opened by accident`() {
        val field = MineField.empty()
            .reveal(Cell(0, 0), Random(2))
            .flag(Cell(8, 8))
        assertEquals(field.revealed, field.reveal(Cell(8, 8), Random(2)).revealed)
    }

    @Test
    fun `winning means every safe square is open`() {
        var field = MineField.empty().reveal(Cell(4, 4), Random(5))
        (0 until MineField.SIZE).forEach { y ->
            (0 until MineField.SIZE).forEach { x ->
                val cell = Cell(x, y)
                if (cell !in field.mines) field = field.reveal(cell, Random(5))
            }
        }
        assertTrue(field.won)
        assertFalse(field.lost)
    }
}

class TetrisTest {

    private val random = Random(3)

    @Test
    fun `a piece stops at the floor and spawns the next one`() {
        var state = TetrisState.new(random)
        repeat(40) { state = state.drop(random) }
        assertTrue(state.well.isNotEmpty())
    }

    @Test
    fun `a full row is cleared and everything above it falls`() {
        // One row short of complete, with a marker sitting above the gap.
        val nearlyFull = (0 until TetrisState.WIDTH - 1)
            .associate { Cell(it, TetrisState.HEIGHT - 1) to Arcade.Red } +
            (Cell(TetrisState.WIDTH - 1, TetrisState.HEIGHT - 3) to Arcade.Green)

        val state = TetrisState(
            well = nearlyFull,
            piece = Piece.I,
            rotation = 1,
            position = Cell(TetrisState.WIDTH - 3, TetrisState.HEIGHT - 4),
        )
        var next = state
        repeat(6) { next = next.drop(random) }

        assertEquals(1, next.lines)
        assertTrue(next.score >= 100)
        // The marker was above the cleared row, so it has moved down one.
        assertTrue(next.well.keys.any { it.y == TetrisState.HEIGHT - 2 })
    }

    @Test
    fun `rotation kicks off the wall instead of being ignored`() {
        val againstWall = TetrisState(piece = Piece.I, rotation = 1, position = Cell(-2, 5))
        val rotated = againstWall.rotate()
        assertTrue(rotated.blocks().all { it.x >= 0 })
    }

    @Test
    fun `speed increases with lines cleared but never below the floor`() {
        val slow = TetrisState()
        val fast = TetrisState(lines = 200)
        assertTrue(fast.intervalMs < slow.intervalMs)
        assertEquals(120L, fast.intervalMs)
    }
}

class SokobanTest {

    @Test
    fun `every level has as many crates as goals and a player on a free square`() {
        SokobanState.LEVELS.indices.forEach { index ->
            val level = SokobanState.load(index)
            assertEquals("level $index", level.goals.size, level.crates.size)
            assertFalse(level.player in level.walls)
            assertTrue(level.crates.none { it in level.walls })
            assertFalse(level.solved)
        }
    }

    @Test
    fun `a crate is pushed, never pulled`() {
        val level = SokobanState.load(0)
        val crate = level.crates.first()
        // Standing below the crate and moving up pushes it up.
        val pushed = level.copy(player = Cell(crate.x, crate.y + 1)).push(Direction.Up)
        assertTrue(Cell(crate.x, crate.y - 1) in pushed.crates)
        assertEquals(crate, pushed.player)
    }

    @Test
    fun `a crate against a wall does not move`() {
        val level = SokobanState.load(0)
        val crate = level.crates.first()
        val blocked = level.copy(
            player = Cell(crate.x, crate.y + 1),
            walls = level.walls + Cell(crate.x, crate.y - 1),
        ).push(Direction.Up)
        assertEquals(level.crates, blocked.crates)
    }

    @Test
    fun `undo restores the previous position exactly`() {
        val level = SokobanState.load(1)
        val moved = level.push(Direction.Up).push(Direction.Left)
        val undone = moved.undo().undo()
        assertEquals(level.player, undone.player)
        assertEquals(level.crates, undone.crates)
    }

    @Test
    fun `the first level can be solved by pushing the crate onto its goal`() {
        var state = SokobanState.load(0)
        repeat(3) { state = state.push(Direction.Up) }
        assertTrue(state.solved)
    }
}

class StackTest {

    @Test
    fun `overhang is trimmed from the landed slab`() {
        val world = StackWorld(
            landed = listOf(Slab(0.3f, 0.4f)),
            moving = Slab(0.5f, 0.4f),
        )
        val after = world.drop()
        assertEquals(0.5f, after.landed.last().x, 1e-4f)
        assertEquals(0.2f, after.landed.last().width, 1e-4f)
    }

    @Test
    fun `a perfect landing keeps the full width`() {
        val world = StackWorld(
            landed = listOf(Slab(0.3f, 0.4f)),
            moving = Slab(0.305f, 0.4f),
        )
        val after = world.drop()
        assertEquals(0.4f, after.landed.last().width, 1e-4f)
        assertEquals(3, after.score)
    }

    @Test
    fun `missing the tower entirely ends the game`() {
        val world = StackWorld(
            landed = listOf(Slab(0.0f, 0.2f)),
            moving = Slab(0.7f, 0.2f),
        )
        assertTrue(world.drop().over)
    }

    @Test
    fun `the slab bounces between the walls`() {
        var world = StackWorld(moving = Slab(0.98f, 0.4f), direction = 1)
        world = world.step(0.1f)
        assertEquals(-1, world.direction)
        assertTrue(world.moving.x + world.moving.width <= 1f + 1e-4f)
    }
}

class FloodAndLightsTest {

    @Test
    fun `a light toggle flips the cell and its orthogonal neighbours`() {
        // Mirrors LightsOutScreen's rule; five cells change, diagonals do not.
        val size = 5
        fun toggle(board: Set<Cell>, cell: Cell): Set<Cell> {
            val affected = listOf(cell) + Direction.entries.map { cell.move(it) }
            return affected
                .filter { it.x in 0 until size && it.y in 0 until size }
                .fold(board) { acc, position ->
                    if (position in acc) acc - position else acc + position
                }
        }

        val centre = toggle(emptySet(), Cell(2, 2))
        assertEquals(5, centre.size)
        assertFalse(Cell(1, 1) in centre)
        // Toggling twice is the identity, which is what makes the puzzle solvable.
        assertEquals(emptySet<Cell>(), toggle(centre, Cell(2, 2)))

        val corner = toggle(emptySet(), Cell(0, 0))
        assertEquals(3, corner.size)
    }
}

class TronTest {

    @Test
    fun `a cycle cannot reverse into its own trail`() {
        val state = TronState.new()
        assertEquals(Direction.Right, state.turn(Direction.Left).direction(true))
        assertEquals(Direction.Up, state.turn(Direction.Up).direction(true))
    }

    @Test
    fun `driving into a wall ends the round`() {
        var state = TronState.new().copy(player = Cell(1, 1), playerDir = Direction.Left)
        state = state.step(Random(1))
        state = state.step(Random(1))
        assertEquals(ChompOutcome.Lost, state.outcome)
    }

    @Test
    fun `both cycles leave a trail behind them`() {
        var state = TronState.new()
        val before = state.trails.size
        state = state.step(Random(4))
        assertTrue(state.trails.size > before)
        assertNotNull(state.trails[state.player])
    }
}

/** Small readability helper for the Tron assertions above. */
private fun TronState.direction(mine: Boolean): Direction = if (mine) playerDir else rivalDir
