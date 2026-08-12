package com.omai.neocalc.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeGameTest {

    private val seeded = Random(7)

    private fun state(
        body: List<Cell>,
        direction: Direction,
        food: Cell = Cell(15, 15),
    ) = SnakeState(body = body, direction = direction, food = food, gridWidth = 8, gridHeight = 8)

    @Test
    fun `a step moves the head and drops the tail`() {
        val before = state(listOf(Cell(3, 3), Cell(2, 3), Cell(1, 3)), Direction.Right)
        val after = before.step(seeded)
        assertEquals(Cell(4, 3), after.head)
        assertEquals(3, after.body.size)
        assertEquals(0, after.score)
    }

    @Test
    fun `eating grows the snake and scores`() {
        val before = state(listOf(Cell(3, 3), Cell(2, 3)), Direction.Right, food = Cell(4, 3))
        val after = before.step(seeded)
        assertEquals(3, after.body.size)
        assertEquals(1, after.score)
        // The new food must not land under the snake.
        assertFalse(after.food in after.body)
    }

    @Test
    fun `walls are fatal`() {
        val before = state(listOf(Cell(7, 3), Cell(6, 3)), Direction.Right)
        assertFalse(before.step(seeded).alive)
    }

    @Test
    fun `running into your own body is fatal`() {
        val body = listOf(Cell(3, 3), Cell(3, 4), Cell(2, 4), Cell(2, 3), Cell(1, 3))
        val after = state(body, Direction.Down).step(seeded)
        assertFalse(after.alive)
    }

    @Test
    fun `the cell the tail is vacating is not a collision`() {
        // Head chases its own last segment; by the time it arrives the tail is gone.
        val body = listOf(Cell(2, 2), Cell(2, 3), Cell(3, 3), Cell(3, 2))
        val after = state(body, Direction.Right).step(seeded)
        assertTrue(after.alive)
        assertEquals(Cell(3, 2), after.head)
    }

    @Test
    fun `a snake cannot reverse into itself`() {
        val before = state(listOf(Cell(3, 3), Cell(2, 3)), Direction.Right)
        assertEquals(Direction.Right, before.turn(Direction.Left).direction)
        assertEquals(Direction.Up, before.turn(Direction.Up).direction)
    }

    @Test
    fun `a dead snake ignores input and stays dead`() {
        val dead = state(listOf(Cell(3, 3)), Direction.Right).copy(alive = false)
        assertEquals(dead, dead.step(seeded))
        assertEquals(dead, dead.turn(Direction.Up))
    }

    @Test
    fun `the game speeds up as the score climbs, but only to a floor`() {
        val slow = SnakeState.new(seeded)
        val fast = slow.copy(score = 200)
        assertTrue(fast.intervalMs < slow.intervalMs)
        assertEquals(SnakeState.MIN_INTERVAL_MS, fast.intervalMs)
    }

    @Test
    fun `food always spawns on a free cell`() {
        val body = listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        repeat(50) { seed ->
            val food = SnakeState.spawnFood(body, 4, 4, Random(seed))
            assertFalse("seed $seed put food under the snake", food in body)
            assertTrue(food.x in 0 until 4 && food.y in 0 until 4)
        }
    }
}
