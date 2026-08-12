package com.omai.neocalc.games

import kotlin.random.Random

/**
 * Snake, as a pure state machine: [step] takes a state and returns the next one,
 * so the whole game is testable without a screen, a clock, or a touch event.
 */
data class SnakeState(
    /** Head first. The tail end is what gets dropped when the snake hasn't eaten. */
    val body: List<Cell>,
    val direction: Direction,
    val food: Cell,
    val score: Int = 0,
    val alive: Boolean = true,
    val gridWidth: Int = GRID,
    val gridHeight: Int = GRID,
) {
    val head: Cell get() = body.first()

    companion object {
        const val GRID = 17

        /** Milliseconds per step at the start, and the floor it speeds up to. */
        const val START_INTERVAL_MS = 190L
        const val MIN_INTERVAL_MS = 70L

        fun new(random: Random = Random.Default): SnakeState {
            val start = Cell(GRID / 2, GRID / 2)
            val body = listOf(start, start.move(Direction.Left), start.move(Direction.Left).move(Direction.Left))
            return SnakeState(
                body = body,
                direction = Direction.Right,
                food = spawnFood(body, GRID, GRID, random),
            )
        }

        /**
         * Only ever places food on a free cell. Picking at random and retrying
         * would stall as the board fills, so this enumerates what is actually
         * left and chooses from that.
         */
        internal fun spawnFood(
            body: List<Cell>,
            width: Int,
            height: Int,
            random: Random,
        ): Cell {
            val taken = body.toSet()
            val free = buildList {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val cell = Cell(x, y)
                        if (cell !in taken) add(cell)
                    }
                }
            }
            // A full board has nowhere to put food; the head cell is harmless
            // because the game is already won at that point.
            return if (free.isEmpty()) body.first() else free[random.nextInt(free.size)]
        }
    }

    /** The interval between steps, which shortens as the snake grows. */
    val intervalMs: Long
        get() = (START_INTERVAL_MS - score / 2 * 6L).coerceAtLeast(MIN_INTERVAL_MS)

    /**
     * A turn is only accepted if it isn't a reversal: a snake that can double back
     * onto its own neck dies to a mistyped swipe rather than to a mistake.
     */
    fun turn(next: Direction): SnakeState =
        if (next == direction.opposite() || !alive) this else copy(direction = next)

    fun step(random: Random = Random.Default): SnakeState {
        if (!alive) return this

        val next = head.move(direction)

        val hitWall = next.x !in 0 until gridWidth || next.y !in 0 until gridHeight
        // The tail cell is about to move out of the way, so running into it is
        // not a collision - without this exception every turn at full length dies.
        val hitSelf = next in body.dropLast(1)
        if (hitWall || hitSelf) return copy(alive = false)

        val eating = next == food
        val body = buildList {
            add(next)
            addAll(if (eating) this@SnakeState.body else this@SnakeState.body.dropLast(1))
        }
        return copy(
            body = body,
            score = if (eating) score + 1 else score,
            food = if (eating) spawnFood(body, gridWidth, gridHeight, random) else food,
        )
    }
}
