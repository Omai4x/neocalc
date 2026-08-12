package com.omai.neocalc.games

import android.content.Context

/**
 * Something worth doing across the arcade as a whole.
 *
 * Thirty-one separate high scores is a pile of numbers; a handful of named goals
 * is a reason to open the second game. Everything is stored on the device and
 * submitted nowhere - see the privacy policy, which says exactly that.
 */
data class Achievement(
    val id: String,
    val title: String,
    val detail: String,
    /** Evaluated against the scores already on disk. */
    val test: (Progress) -> Boolean,
)

/** A snapshot of the arcade, so a rule can be written without touching storage. */
data class Progress(
    val bestByGame: Map<String, Int>,
    val gamesPlayed: Int,
) {
    fun best(game: String): Int = bestByGame[game] ?: 0
}

object Achievements {

    val ALL: List<Achievement> = listOf(
        Achievement(
            "first_game", "Coin inserted", "Finish any game",
        ) { it.gamesPlayed >= 1 },
        Achievement(
            "five_games", "Browser", "Play five different games",
        ) { it.gamesPlayed >= 5 },
        Achievement(
            "half_the_shelf", "Regular", "Play half the arcade",
        ) { it.gamesPlayed >= ARCADE_CATALOG.size / 2 },
        Achievement(
            "completionist", "Completionist", "Play every game once",
        ) { it.gamesPlayed >= ARCADE_CATALOG.size },
        Achievement(
            "snake_20", "Long in the tooth", "Reach 20 in Snake",
        ) { it.best("snake") >= 20 },
        Achievement(
            "chomp_1000", "Maze runner", "Score 1000 in Chomp",
        ) { it.best("chomp") >= 1000 },
        Achievement(
            "blocks_5000", "Stacker", "Score 5000 in Blocks",
        ) { it.best("blocks") >= 5000 },
        Achievement(
            "flap_20", "Frequent flyer", "Pass 20 pipes in Flap",
        ) { it.best("flap") >= 20 },
        Achievement(
            "2048_win", "Two thousand and forty-eight", "Score 20000 in 2048",
        ) { it.best("2048") >= 20000 },
        Achievement(
            "mines_clear", "Bomb disposal", "Clear a minefield",
        ) { it.best("mines") >= 200 },
        Achievement(
            "ttt_draw", "Unbeatable met its match", "Draw or beat Tic-Tac-Toe",
        ) { it.best("tictactoe") >= 30 },
        Achievement(
            "word_first_try", "Wordsmith", "Solve Word Guess in three or fewer",
        ) { it.best("word") >= 400 },
        Achievement(
            "reflex_fast", "Quick draw", "Average under 300ms in Reflex",
        ) { it.best("reflex") >= 900 },
        Achievement(
            "sokoban_all", "Warehouse manager", "Clear every Warehouse level",
        ) { it.best("sokoban") >= 900 },
    )

    fun progress(context: Context): Progress {
        val scores = ARCADE_CATALOG.associate { it.key to ArcadeScores.best(context, it.key) }
        return Progress(
            bestByGame = scores,
            // A game counts as played once it has a score, which is the only
            // signal stored; it is enough and costs no extra bookkeeping.
            gamesPlayed = scores.count { it.value > 0 },
        )
    }

    fun earned(context: Context): Set<String> {
        val progress = progress(context)
        return ALL.filter { it.test(progress) }.map { it.id }.toSet()
    }
}
