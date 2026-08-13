package com.omai.neocalc.games

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import com.omai.neocalc.ui.LocalWindowSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How the hub groups the catalogue. Order here is the order of the filter row. */
enum class GameCategory(val label: String) {
    All("All"),
    Arcade("Arcade"),
    Reflex("Reflex"),
    Puzzle("Puzzle"),
    Mind("Mind"),
}

/**
 * One game in the catalogue.
 *
 * The copy lives here in Kotlin rather than in strings.xml: this is a hidden
 * easter egg, and 150 more translatable strings for it would outweigh the whole
 * rest of the app's resources. The shared chrome the player actually needs to
 * read - errors, the calculator, the converter - stays in resources.
 */
data class GameEntry(
    val key: String,
    val title: String,
    val tagline: String,
    val glyph: String,
    val category: GameCategory,
    val accent: Color,
    /** Three or four lines shown on the intro screen before the first round. */
    val howTo: List<String>,
    val content: @Composable (onExit: () -> Unit) -> Unit,
)

/**
 * Every game, in the order they appear. Composables are referenced rather than
 * invoked, so building this list costs nothing until one is chosen.
 */
val ARCADE_CATALOG: List<GameEntry> = listOf(
    GameEntry(
        "snake", "Snake", "Eat, grow, don't bite yourself", "🐍",
        GameCategory.Arcade, Arcade.Green,
        listOf(
            "Steer with the D-pad or swipe the board.",
            "Every apple makes you one segment longer.",
            "Walls and your own body are fatal - speed creeps up as you grow.",
        ),
    ) { SnakeGameScreen(it) },
    GameEntry(
        "chomp", "Chomp", "Clear the maze, dodge the ghosts", "🟡",
        GameCategory.Arcade, Arcade.Yellow,
        listOf(
            "Eat every pellet to clear the maze.",
            "The four big pellets make ghosts edible for a while.",
            "You can turn early - the turn is taken as soon as it fits.",
        ),
    ) { ChompGameScreen(it) },
    GameEntry(
        "breakout", "Brick Breaker", "Clear the wall, keep the ball up", "🧱",
        GameCategory.Arcade, Arcade.Coral,
        listOf(
            "Slide the paddle, or drag anywhere on the board.",
            "Where the ball hits the paddle decides the angle it leaves at.",
            "Each cleared wall comes back slightly faster.",
        ),
    ) { BreakoutScreen(it) },
    GameEntry(
        "pong", "Pong", "First to eleven", "🏓",
        GameCategory.Arcade, Arcade.Sky,
        listOf(
            "Move your paddle along the bottom.",
            "The opponent tracks the ball, but not perfectly.",
            "Rallies speed the ball up; a point resets it.",
        ),
    ) { PongScreen(it) },
    GameEntry(
        "invaders", "Invaders", "Hold the line", "👾",
        GameCategory.Arcade, Arcade.Lime,
        listOf(
            "Move left and right, tap FIRE to shoot.",
            "The fleet drops a row and speeds up every time it reaches the edge.",
            "Bunkers absorb fire from both sides - yours included.",
        ),
    ) { InvadersScreen(it) },
    GameEntry(
        "asteroids", "Asteroids", "Drift, turn, shoot", "☄️",
        GameCategory.Arcade, Arcade.Slate,
        listOf(
            "Turn with ↺ ↻, thrust with ▲, and FIRE to shoot.",
            "Big rocks split into smaller, faster ones.",
            "You reappear with a moment of shielding after a hit.",
        ),
    ) { AsteroidsScreen(it) },
    GameEntry(
        "tron", "Light Cycles", "Outlast the wall you're both building", "🏍️",
        GameCategory.Arcade, Arcade.Teal,
        listOf(
            "You and the opponent leave a solid trail behind you.",
            "Crash into any trail - including your own - and the round is over.",
            "Cut off space rather than chasing.",
        ),
    ) { TronScreen(it) },
    GameEntry(
        "blocks", "Blocks", "Fit the falling pieces", "🟦",
        GameCategory.Arcade, Arcade.Indigo,
        listOf(
            "◀ ▶ move, ▲ rotates, ▼ drops the piece faster.",
            "Complete a row to clear it; four at once scores the most.",
            "The fall speeds up one step every ten lines.",
        ),
    ) { TetrisScreen(it) },
    GameEntry(
        "hop", "Road Hop", "Cross without getting flattened", "🐸",
        GameCategory.Arcade, Arcade.Green,
        listOf(
            "Hop one lane at a time towards the top.",
            "Traffic never fills a lane completely - there is always a gap.",
            "Reaching the top banks the row and starts a faster one.",
        ),
    ) { FroggerScreen(it) },
    GameEntry(
        "racer", "Lane Racer", "Weave through the traffic", "🏎️",
        GameCategory.Arcade, Arcade.Red,
        listOf(
            "Change lane with ◀ ▶ - you can hold a lane as long as you like.",
            "Traffic thickens the longer you survive.",
            "Distance is the score; near misses are free.",
        ),
    ) { RacerScreen(it) },
    GameEntry(
        "copter", "Cave Copter", "Hold to climb, release to fall", "🚁",
        GameCategory.Arcade, Arcade.Amber,
        listOf(
            "Tap and hold the big button to rise.",
            "The cave narrows gradually, never suddenly.",
            "Every wall you pass is a point.",
        ),
    ) { CopterScreen(it) },
    GameEntry(
        "flap", "Flap", "One tap, endless pipes", "🐤",
        GameCategory.Arcade, Arcade.Yellow,
        listOf(
            "Tap to flap; gravity does the rest.",
            "The first few gaps are wide, and they narrow slowly.",
            "The board is the button, too.",
        ),
    ) { FlapScreen(it) },
    GameEntry(
        "ascend", "Ascend", "Bounce your way up", "🪜",
        GameCategory.Arcade, Arcade.Purple,
        listOf(
            "You bounce automatically - steer left and right.",
            "Height is the score, and you never fall through a platform.",
            "Wrapping round the sides is allowed and often faster.",
        ),
    ) { AscendScreen(it) },

    GameEntry(
        "stack", "Stack", "Land each slab square", "🏗️",
        GameCategory.Reflex, Arcade.Teal,
        listOf(
            "Tap to drop the moving slab.",
            "Overhang is trimmed away, so sloppy stacking narrows the tower.",
            "A perfect landing gives the width back.",
        ),
    ) { StackScreen(it) },
    GameEntry(
        "sharpshooter", "Sharpshooter", "Hit the targets, spare the friendlies", "🎯",
        GameCategory.Reflex, Arcade.Red,
        listOf(
            "Tap a target to hit it.",
            "Gold targets are worth triple; blue ones cost you.",
            "Targets shrink and quicken as your score climbs.",
        ),
    ) { SharpshooterScreen(it) },
    GameEntry(
        "mole", "Whack-a-Mole", "Sixty seconds of quick hands", "🕳️",
        GameCategory.Reflex, Arcade.Brown,
        listOf(
            "Tap a mole while it is up.",
            "Moles stay up longer early on and get brisker later.",
            "Golden moles are worth five.",
        ),
    ) { MoleScreen(it) },
    GameEntry(
        "reflex", "Reflex", "How fast are you, really?", "⚡",
        GameCategory.Reflex, Arcade.Amber,
        listOf(
            "Wait for the board to turn green, then tap.",
            "Tapping early costs you the round, not the game.",
            "Your score is based on your best five reactions.",
        ),
    ) { ReactionScreen(it) },

    GameEntry(
        "2048", "2048", "Slide, merge, repeat", "🔢",
        GameCategory.Puzzle, Arcade.Amber,
        listOf(
            "Swipe or use the D-pad - every tile slides at once.",
            "Equal tiles merge into their sum.",
            "There is no timer. Take as long as you like.",
        ),
    ) { Game2048Screen(it) },
    GameEntry(
        "mines", "Minesweeper", "Deduce, don't guess", "💣",
        GameCategory.Puzzle, Arcade.Slate,
        listOf(
            "Tap to reveal, long-press to flag.",
            "A number is how many mines touch that square.",
            "Your first tap is always safe.",
        ),
    ) { MinesweeperScreen(it) },
    GameEntry(
        "lights", "Lights Out", "Turn them all off", "💡",
        GameCategory.Puzzle, Arcade.Yellow,
        listOf(
            "Tapping a light flips it and its four neighbours.",
            "Every board is generated from a solved one, so all are solvable.",
            "Fewer moves scores higher.",
        ),
    ) { LightsOutScreen(it) },
    GameEntry(
        "slide", "Fifteen", "Order from chaos", "🔀",
        GameCategory.Puzzle, Arcade.Sky,
        listOf(
            "Tap a tile next to the gap to slide it in.",
            "Get 1-15 in order with the gap last.",
            "Shuffles are made from real moves, so every board is solvable.",
        ),
    ) { SlidePuzzleScreen(it) },
    GameEntry(
        "sokoban", "Warehouse", "Push every crate onto a mark", "📦",
        GameCategory.Puzzle, Arcade.Brown,
        listOf(
            "You push crates; you can never pull one.",
            "Undo is always available, so a wedged crate isn't a lost level.",
            "Six levels, each a little wider.",
        ),
    ) { SokobanScreen(it) },
    GameEntry(
        "memory", "Memory", "Find the pairs", "🃏",
        GameCategory.Puzzle, Arcade.Pink,
        listOf(
            "Turn two cards; matching pairs stay face up.",
            "Wrong pairs stay visible long enough to actually memorise.",
            "Fewer turns scores higher.",
        ),
    ) { MemoryScreen(it) },
    GameEntry(
        "simon", "Simon", "Repeat the sequence", "🎵",
        GameCategory.Puzzle, Arcade.Green,
        listOf(
            "Watch the sequence, then tap it back.",
            "One new step is added each round.",
            "The playback is deliberately unhurried.",
        ),
    ) { SimonScreen(it) },
    GameEntry(
        "flood", "Flood", "Take over the board", "🌊",
        GameCategory.Puzzle, Arcade.Blue,
        listOf(
            "Pick a colour; your region in the top-left takes it.",
            "Neighbouring squares of that colour join your region.",
            "Fill the board within the move limit.",
        ),
    ) { FloodScreen(it) },

    GameEntry(
        "tictactoe", "Tic-Tac-Toe", "Perfect play, if you can find it", "❌",
        GameCategory.Mind, Arcade.Coral,
        listOf(
            "You are X and always move first.",
            "The opponent searches the whole game - it will never blunder.",
            "A draw against it is a win, really.",
        ),
    ) { TicTacToeScreen(it) },
    GameEntry(
        "connect4", "Connect Four", "Four in a row, any direction", "🔴",
        GameCategory.Mind, Arcade.Red,
        listOf(
            "Tap a column to drop your disc.",
            "The opponent looks a few moves ahead.",
            "Diagonals win more games than columns do.",
        ),
    ) { ConnectFourScreen(it) },
    GameEntry(
        "reversi", "Reversi", "Flip your way to the majority", "⚫",
        GameCategory.Mind, Arcade.Green,
        listOf(
            "A move must trap at least one of the opponent's discs.",
            "Trapped discs flip to your colour.",
            "Corners can never be flipped back - take them.",
        ),
    ) { ReversiScreen(it) },
    GameEntry(
        "nim", "Nim", "Take the last match", "🔥",
        GameCategory.Mind, Arcade.Amber,
        listOf(
            "Take any number of matches, but from one row only.",
            "Whoever takes the last match wins.",
            "The opponent plays the perfect strategy when you leave it one.",
        ),
    ) { NimScreen(it) },
    GameEntry(
        "mathblitz", "Math Blitz", "The calculator strikes back", "➗",
        GameCategory.Mind, Arcade.Indigo,
        listOf(
            "Pick the right answer before the bar runs out.",
            "Right answers extend the clock; wrong ones only cost time.",
            "Questions get harder as your streak grows.",
        ),
    ) { MathBlitzScreen(it) },
    GameEntry(
        "word", "Word Guess", "Five letters, six tries", "🔤",
        GameCategory.Mind, Arcade.Teal,
        listOf(
            "Green means right letter, right place.",
            "Amber means right letter, wrong place.",
            "Only real words from the built-in list are accepted.",
        ),
    ) { WordGuessScreen(it) },
)

/**
 * The hidden arcade. Reached by long-pressing the calculator display while it
 * reads the magic number - see CalculatorScreen.
 */
@Composable
fun ArcadeScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val entry = ARCADE_CATALOG.firstOrNull { it.key == openKey }

    // Back unwinds one layer at a time - game, then intro, then out of the
    // arcade - which is what a player expects from a hardware back button.
    BackHandler {
        when {
            playing -> playing = false
            entry != null -> openKey = null
            else -> onExit()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // No Scaffold here, so the d-pad would otherwise sit on the gesture
            // bar and the close button under the clock.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when {
            entry == null -> GameHub(onPick = { openKey = it.key }, onExit = onExit)

            !playing -> GameIntro(
                entry = entry,
                onPlay = { playing = true },
                onBack = { openKey = null },
                onExit = onExit,
            )

            else -> entry.content { playing = false }
        }
    }
}

// ------------------------------------------------------------------ Hub

@Composable
private fun GameHub(onPick: (GameEntry) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var category by rememberSaveable { mutableStateOf(GameCategory.All) }

    val earned = remember { Achievements.earned(context) }
    val games = remember(category) {
        if (category == GameCategory.All) {
            ARCADE_CATALOG
        } else {
            ARCADE_CATALOG.filter { it.category == category }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ArcadeTopBar(
            title = "NeoCalc Arcade",
            subtitle = "${ARCADE_CATALOG.size} games · ${ArcadeScores.played(context)} played",
            onExit = onExit,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameCategory.entries.forEach { option ->
                CategoryChip(
                    label = option.label,
                    selected = option == category,
                    onClick = { category = option },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(LocalWindowSize.current.gridColumns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AchievementStrip(earned = earned, total = Achievements.ALL.size)
            }
            items(games, key = { it.key }) { game ->
                GameCard(
                    game = game,
                    best = ArcadeScores.best(context, game.key),
                    onClick = { onPick(game) },
                )
            }
        }
    }

}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun GameCard(game: GameEntry, best: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Column {
            // A tinted band per game, so the grid reads as a shelf of different
            // things rather than thirty-one identical cards.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(game.accent.copy(alpha = 0.30f), game.accent.copy(alpha = 0.08f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = game.glyph, fontSize = 34.sp)
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (best > 0) "Best $best" else game.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (best > 0) game.accent else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A single row summarising the arcade as a whole. Named goals give the shelf a
 * spine; thirty-one unrelated high scores do not.
 */
@Composable
private fun AchievementStrip(earned: Set<String>, total: Int) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ACHIEVEMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${earned.size} / $total",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(earned.size.toFloat() / total.coerceAtLeast(1))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Achievements.ALL.forEach { achievement ->
                    val done = achievement.id in earned
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (done) {
                                        scheme.primary
                                    } else {
                                        scheme.surfaceVariant
                                    },
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = achievement.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (done) scheme.onSurface else scheme.onSurfaceVariant,
                            )
                            Text(
                                text = achievement.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Intro

/**
 * Every game gets the same introduction: what it is, how it is played, and one
 * unmissable button. Dropping straight into an unfamiliar game is the fastest
 * way to lose a player who only found this by accident.
 */
@Composable
private fun GameIntro(
    entry: GameEntry,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val best = ArcadeScores.best(context, entry.key)

    Column(modifier = Modifier.fillMaxSize()) {
        ArcadeTopBar(title = entry.title, onExit = onExit, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(entry.accent.copy(alpha = 0.38f), entry.accent.copy(alpha = 0.10f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = entry.glyph, fontSize = 60.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HOW TO PLAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = entry.accent,
                    )
                    Spacer(Modifier.height(10.dp))
                    entry.howTo.forEachIndexed { index, line ->
                        Row(modifier = Modifier.padding(vertical = 5.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(entry.accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = entry.accent,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (best > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your best: $best",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(22.dp))
            Surface(
                onClick = onPlay,
                shape = RoundedCornerShape(18.dp),
                color = entry.accent,
                contentColor = Color.Black.copy(alpha = 0.82f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "PLAY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
