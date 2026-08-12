package com.omai.neocalc.games

import kotlin.random.Random

/**
 * "Chomp" - a compact maze chase in the Pac-Man tradition. Eat every pellet
 * without being caught; the four big pellets turn the ghosts edible for a while.
 *
 * Like [SnakeGame] this is a pure state machine, which is what makes ghost
 * behaviour testable: no screen, no timer, no randomness beyond what is passed in.
 */
data class Ghost(
    val cell: Cell,
    val direction: Direction,
    val home: Cell,
    /** Ticks left as edible; 0 means dangerous. */
    val frightened: Int = 0,
    val colorIndex: Int = 0,
)

enum class ChompOutcome { Playing, Lost, Won }

data class ChompState(
    val player: Cell,
    val direction: Direction,
    /** Held until the maze allows it, so a turn can be entered slightly early. */
    val queued: Direction? = null,
    val ghosts: List<Ghost>,
    val pellets: Set<Cell>,
    val powers: Set<Cell>,
    val score: Int = 0,
    val lives: Int = 3,
    val outcome: ChompOutcome = ChompOutcome.Playing,
    /** Counts steps so ghosts can be made to move slower than the player. */
    val tick: Int = 0,
) {
    val playing: Boolean get() = outcome == ChompOutcome.Playing

    companion object {

        /**
         * '#' wall, '.' pellet, 'o' power pellet, ' ' empty, 'P' player start,
         * 'G' ghost start. Kept as text because a maze is far easier to read,
         * edit and review this way than as a nested array of numbers.
         */
        val MAZE = listOf(
            "#################",
            "#.......#.......#",
            "#o##.##.#.##.##o#",
            "#.###.#...#.###.#",
            "#...............#",
            "#.##.#.###.#.##.#",
            "#....#..#..#....#",
            "####.##.#.##.####",
            "#......GGG......#",
            "####.##.#.##.####",
            "#.......#.......#",
            "#.##.##.#.##.##.#",
            "#o.#.....P.#...o#",
            "##.#.#.###.#.#.##",
            "#....#..#..#....#",
            "#.######.######.#",
            "#...............#",
            "#################",
        )

        val WIDTH = MAZE.first().length
        val HEIGHT = MAZE.size

        const val STEP_INTERVAL_MS = 165L

        /** How long a power pellet keeps the ghosts edible, in ticks. */
        const val FRIGHT_TICKS = 34

        const val PELLET_POINTS = 10
        const val POWER_POINTS = 50
        const val GHOST_POINTS = 200

        fun isWall(cell: Cell): Boolean {
            if (cell.y !in 0 until HEIGHT || cell.x !in 0 until WIDTH) return true
            return MAZE[cell.y][cell.x] == '#'
        }

        fun new(): ChompState {
            var player = Cell(1, 1)
            val ghostCells = mutableListOf<Cell>()
            val pellets = mutableSetOf<Cell>()
            val powers = mutableSetOf<Cell>()

            MAZE.forEachIndexed { y, row ->
                row.forEachIndexed { x, symbol ->
                    val cell = Cell(x, y)
                    when (symbol) {
                        '.' -> pellets += cell
                        'o' -> powers += cell
                        'P' -> player = cell
                        'G' -> ghostCells += cell
                    }
                }
            }

            return ChompState(
                player = player,
                direction = Direction.Left,
                ghosts = ghostCells.mapIndexed { index, cell ->
                    Ghost(
                        cell = cell,
                        direction = if (index % 2 == 0) Direction.Left else Direction.Right,
                        home = cell,
                        colorIndex = index,
                    )
                },
                pellets = pellets,
                powers = powers,
            )
        }
    }

    /** Queues a turn; it is taken on the first tick where the maze permits it. */
    fun turn(next: Direction): ChompState = if (!playing) this else copy(queued = next)

    fun step(random: Random = Random.Default): ChompState {
        if (!playing) return this

        // Take the queued turn as soon as it becomes legal, which is what lets a
        // player "pre-turn" into a corner instead of having to time it exactly.
        val heading = queued?.takeIf { !isWall(player.move(it)) } ?: direction
        val target = player.move(heading)
        val moved = if (isWall(target)) player else target

        var score = this.score
        val pellets = if (moved in this.pellets) {
            score += PELLET_POINTS
            this.pellets - moved
        } else {
            this.pellets
        }
        val ate = moved in powers
        val powers = if (ate) {
            score += POWER_POINTS
            this.powers - moved
        } else {
            this.powers
        }

        var ghosts = this.ghosts.map { ghost ->
            if (ate) ghost.copy(frightened = FRIGHT_TICKS) else ghost
        }

        // Ghosts move on three ticks out of four, so the player can always
        // outrun them in a straight line - a chase you cannot win isn't a game.
        ghosts = if (tick % 4 == 3) {
            ghosts.map { it.copy(frightened = (it.frightened - 1).coerceAtLeast(0)) }
        } else {
            ghosts.map { moveGhost(it, moved, random) }
        }

        // Collisions are checked after both sides move, and again for the
        // swap case: passing straight through a ghost must still count.
        var lives = this.lives
        var outcome = this.outcome
        val survivors = mutableListOf<Ghost>()
        for ((index, ghost) in ghosts.withIndex()) {
            val before = this.ghosts[index]
            val collided = ghost.cell == moved ||
                (ghost.cell == player && before.cell == moved)
            when {
                !collided -> survivors += ghost
                ghost.frightened > 0 -> {
                    score += GHOST_POINTS
                    survivors += ghost.copy(cell = ghost.home, frightened = 0)
                }

                else -> {
                    lives -= 1
                    survivors += ghost.copy(cell = ghost.home, frightened = 0)
                }
            }
        }

        val caught = lives < this.lives
        if (lives <= 0) outcome = ChompOutcome.Lost
        if (pellets.isEmpty() && powers.isEmpty()) outcome = ChompOutcome.Won

        return copy(
            player = if (caught) Companion.new().player else moved,
            direction = heading,
            queued = if (heading == queued) null else queued,
            ghosts = survivors,
            pellets = pellets,
            powers = powers,
            score = score,
            lives = lives,
            outcome = outcome,
            tick = tick + 1,
        )
    }

    /**
     * Ghosts head for the player when dangerous and away from them when edible.
     * Reversing is only allowed at a dead end, which is what keeps them moving
     * like patrols rather than jittering on the spot.
     */
    private fun moveGhost(ghost: Ghost, player: Cell, random: Random): Ghost {
        val options = Direction.entries
            .filter { !isWall(ghost.cell.move(it)) }
            .filter { it != ghost.direction.opposite() }
            .ifEmpty { listOf(ghost.direction.opposite()) }

        // A little randomness stops all ghosts from converging on one path and
        // makes them beatable without being predictable.
        val choice = if (random.nextInt(100) < 15) {
            options[random.nextInt(options.size)]
        } else {
            options.minByOrNull { direction ->
                val distance = ghost.cell.move(direction).distanceTo(player)
                if (ghost.frightened > 0) -distance else distance
            } ?: ghost.direction
        }
        return ghost.copy(cell = ghost.cell.move(choice), direction = choice)
    }
}
