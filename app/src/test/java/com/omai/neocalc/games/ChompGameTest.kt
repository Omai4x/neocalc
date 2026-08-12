package com.omai.neocalc.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChompGameTest {

    private val seeded = Random(11)

    @Test
    fun `the maze is rectangular and enclosed`() {
        assertTrue(ChompState.MAZE.all { it.length == ChompState.WIDTH })
        assertTrue(ChompState.MAZE.first().all { it == '#' })
        assertTrue(ChompState.MAZE.last().all { it == '#' })
        assertTrue(ChompState.MAZE.all { it.first() == '#' && it.last() == '#' })
    }

    @Test
    fun `a new game has pellets, lives and a player standing in a corridor`() {
        val state = ChompState.new()
        assertTrue(state.pellets.isNotEmpty())
        assertEquals(4, state.powers.size)
        assertEquals(3, state.lives)
        assertTrue(state.playing)
        assertFalse(ChompState.isWall(state.player))
        assertTrue(state.ghosts.isNotEmpty())
        assertTrue(state.ghosts.none { ChompState.isWall(it.cell) })
    }

    @Test
    fun `walking into a wall leaves the player where they are`() {
        val state = ChompState.new().copy(ghosts = emptyList())
        // Straight up from the start is the maze's outer structure.
        val blocked = state.copy(player = Cell(1, 1), direction = Direction.Up)
        assertEquals(Cell(1, 1), blocked.step(seeded).player)
    }

    @Test
    fun `eating a pellet scores and removes it`() {
        val base = ChompState.new().copy(ghosts = emptyList())
        val target = Cell(2, 1)
        val state = base.copy(
            player = Cell(1, 1),
            direction = Direction.Right,
            pellets = setOf(target),
            powers = emptySet(),
        )
        val after = state.step(seeded)
        assertEquals(ChompState.PELLET_POINTS, after.score)
        assertFalse(target in after.pellets)
        // Clearing the board is a win, not a quiet continuation.
        assertEquals(ChompOutcome.Won, after.outcome)
    }

    @Test
    fun `a power pellet frightens every ghost`() {
        val base = ChompState.new()
        val state = base.copy(
            player = Cell(1, 1),
            direction = Direction.Right,
            powers = setOf(Cell(2, 1)),
        )
        val after = state.step(seeded)
        assertTrue(after.ghosts.all { it.frightened > 0 })
        assertTrue(after.score >= ChompState.POWER_POINTS)
    }

    @Test
    fun `a dangerous ghost costs a life and sends everyone home`() {
        val base = ChompState.new()
        val ghost = base.ghosts.first().copy(cell = Cell(2, 1), frightened = 0)
        val state = base.copy(
            player = Cell(1, 1),
            direction = Direction.Right,
            ghosts = listOf(ghost),
            // Ghosts skip their move on this tick, so the collision is purely
            // the player walking into them.
            tick = 3,
        )
        val after = state.step(seeded)
        assertEquals(2, after.lives)
        assertEquals(ghost.home, after.ghosts.first().cell)
    }

    @Test
    fun `an edible ghost is worth points instead of a life`() {
        val base = ChompState.new()
        val ghost = base.ghosts.first().copy(cell = Cell(2, 1), frightened = 10)
        val state = base.copy(
            player = Cell(1, 1),
            direction = Direction.Right,
            ghosts = listOf(ghost),
            tick = 3,
        )
        val after = state.step(seeded)
        assertEquals(3, after.lives)
        assertTrue(after.score >= ChompState.GHOST_POINTS)
        assertEquals(ghost.home, after.ghosts.first().cell)
    }

    @Test
    fun `the last life ends the game`() {
        val base = ChompState.new()
        val ghost = base.ghosts.first().copy(cell = Cell(2, 1), frightened = 0)
        val state = base.copy(
            player = Cell(1, 1),
            direction = Direction.Right,
            ghosts = listOf(ghost),
            lives = 1,
            tick = 3,
        )
        val after = state.step(seeded)
        assertEquals(ChompOutcome.Lost, after.outcome)
        // A finished game is frozen: further steps change nothing.
        assertEquals(after, after.step(seeded))
    }

    @Test
    fun `a queued turn is taken as soon as the maze allows it`() {
        val state = ChompState.new().copy(ghosts = emptyList())
        // Facing a wall, queue a legal direction: it should be adopted at once.
        val queued = state.copy(player = Cell(1, 1), direction = Direction.Up)
            .turn(Direction.Right)
            .step(seeded)
        assertEquals(Direction.Right, queued.direction)
        assertEquals(Cell(2, 1), queued.player)
    }

    @Test
    fun `ghosts stay inside the maze however long the game runs`() {
        var state = ChompState.new()
        repeat(400) {
            state = state.step(seeded)
            assertTrue(state.ghosts.none { ChompState.isWall(it.cell) })
            assertFalse(ChompState.isWall(state.player))
        }
    }

    @Test
    fun `frightened ghosts run away rather than towards the player`() {
        val base = ChompState.new()
        val ghost = base.ghosts.first().copy(cell = Cell(1, 4), frightened = 30)
        // A deterministic seed that avoids the random-wander branch.
        val state = base.copy(
            player = Cell(1, 1),
            ghosts = listOf(ghost),
            pellets = base.pellets,
            tick = 0,
        )
        val after = state.step(Random(3))
        assertNotEquals(Cell(1, 3), after.ghosts.first().cell)
    }

    @Test
    fun `every pellet is reachable, so the maze can actually be cleared`() {
        val state = ChompState.new()
        val seen = mutableSetOf(state.player)
        val queue = ArrayDeque(listOf(state.player))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            Direction.entries
                .map { cell.move(it) }
                .filter { !ChompState.isWall(it) && seen.add(it) }
                .forEach { queue.addLast(it) }
        }
        val unreachable = (state.pellets + state.powers) - seen
        assertEquals(emptySet<Cell>(), unreachable)
        // The ghosts have to be able to leave their start, too.
        assertTrue(state.ghosts.all { it.cell in seen })
    }
}
