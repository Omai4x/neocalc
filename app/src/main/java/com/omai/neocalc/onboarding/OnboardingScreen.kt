package com.omai.neocalc.onboarding

import com.omai.neocalc.ui.motionDuration
import com.omai.neocalc.ui.LocalWindowSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Whether the welcome tour has been seen. One boolean, in the same preferences
 * file the rest of the app's settings live in.
 */
object Onboarding {

    private const val PREFS = "neocalc.settings"
    private const val KEY = "onboarding_done"

    fun needed(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun complete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, true)
            .apply()
    }
}

/** One page of the tour: a headline, a preview, and what it can actually do. */
private data class Page(
    val eyebrow: String,
    val title: String,
    val body: String,
    val accent: Color,
    val features: List<Pair<ImageVector, String>>,
    val preview: @Composable () -> Unit,
)

@Composable
private fun pages(): List<Page> = listOf(
    Page(
        eyebrow = "WELCOME TO",
        title = "NeoCalc",
        body = "A calculator that also does money - properly, and offline.",
        accent = MaterialTheme.colorScheme.primary,
        features = listOf(
            Icons.Rounded.Calculate to "Scientific calculator with a memory and a running tape",
            Icons.Rounded.Public to "162 world currencies at live rates",
            Icons.Rounded.Straighten to "Length, mass, temperature and more",
        ),
        preview = {
            PhoneFrame(modifier = Modifier.fillMaxHeight()) { CalculatorPreview() }
        },
    ),
    Page(
        eyebrow = "CALCULATE",
        title = "Everything on one keypad",
        body = "Tap f(x) for trigonometry, logs, powers and roots. Every result " +
            "lands on the History tape, where one tap reuses it.",
        accent = Color(0xFF7C4DFF),
        features = listOf(
            Icons.Rounded.Functions to "Scientific functions, degrees or radians",
            Icons.Rounded.Save to "Memory that survives clearing the entry",
            Icons.Rounded.History to "A history tape you can reuse or convert",
        ),
        preview = {
            PhoneFrame(modifier = Modifier.fillMaxHeight()) { CalculatorPreview() }
        },
    ),
    Page(
        eyebrow = "CONVERT",
        title = "Money, with the flags",
        body = "Search any currency by code, name or country. Star the ones you " +
            "use and they sit at the top - and on the board below your result.",
        accent = Color(0xFF26C6DA),
        features = listOf(
            Icons.Rounded.Search to "Search pound, yen or NGN - all of them work",
            Icons.Rounded.Star to "Pin favourites to the top and to the board",
            Icons.Rounded.ContentPaste to "Spots a price on your clipboard and offers to convert it",
        ),
        preview = {
            PhoneFrame(modifier = Modifier.fillMaxHeight()) { SearchPreview() }
        },
    ),
    Page(
        eyebrow = "STAY CURRENT",
        title = "Works on a plane",
        body = "Rates are saved the moment they arrive, so a cold start with no " +
            "signal still gives you an answer - and tells you how old it is.",
        accent = Color(0xFF66BB6A),
        features = listOf(
            Icons.Rounded.CloudOff to "Last-known rates cached on the device",
            Icons.Rounded.ShowChart to "A 30-day trend under every pair",
            Icons.Rounded.Keyboard to "Type 12*3.5 straight into the amount field",
        ),
        preview = {
            PhoneFrame(modifier = Modifier.fillMaxHeight()) { ConverterPreview() }
        },
    ),
    Page(
        eyebrow = "ONE MORE THING",
        title = "Put it on your home screen",
        body = "Add the widget and your last converted pair is a glance away, " +
            "with no app launch at all.",
        accent = Color(0xFFFFB300),
        features = listOf(
            Icons.Rounded.Widgets to "Home-screen widget, refreshed through the day",
            Icons.Rounded.DarkMode to "Follows your system light and dark theme",
            Icons.Rounded.Lock to "No account, no tracking, no ads",
        ),
        preview = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(modifier = Modifier.fillMaxWidth(0.9f))
            }
        },
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pages = pages()
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val page = pages[pagerState.currentPage]
    val last = pagerState.currentPage == pages.lastIndex

    // The whole screen takes its tint from the page, so moving between them
    // feels like moving between parts of the app rather than sliding cards.
    val accent by animateColorAsState(page.accent, tween(motionDuration(420)), label = "accent")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), scheme.background, scheme.background),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NeoCalc",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            if (!last) {
                Surface(
                    onClick = onFinish,
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = scheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 8.dp,
        ) { index ->
            PageContent(pages[index])
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { index ->
                val selected = index == pagerState.currentPage
                // The active dot stretches into a pill, which reads as progress
                // rather than as a set of interchangeable dots.
                val width by animateDpAsState(
                    targetValue = if (selected) 26.dp else 8.dp,
                    animationSpec = tween(motionDuration(280)),
                    label = "dot",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else scheme.outline),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            onClick = {
                if (last) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            shape = RoundedCornerShape(18.dp),
            color = accent,
            contentColor = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (last) "Get started" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun PageContent(page: Page) {
    val window = LocalWindowSize.current
    // On a wide screen the preview and the copy sit side by side; on a phone
    // they stack. Same content either way, just given the room it has.
    if (window.atLeastMedium) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = window.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            PreviewStage(
                accent = page.accent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
            ) {
                PagePreview(page)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                PageCopy(page, centred = false)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = window.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PreviewStage(
            accent = page.accent,
            modifier = Modifier
                .fillMaxWidth()
                // The preview gets the lion's share of the page: it is the part
                // that actually shows what the app looks like.
                .weight(1f)
                .padding(bottom = 16.dp),
        ) {
            PagePreview(page)
        }
        PageCopy(page, centred = true)
    }
}

/**
 * Sizes the device preview from the height it has been given, so the frame is
 * always fully visible and as large as the space allows. Sizing from width
 * instead would push a tall phone frame off the bottom of a short page.
 */
@Composable
private fun PagePreview(page: Page) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) { page.preview() }
    }
}

@Composable
private fun PageCopy(page: Page, centred: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val alignment = if (centred) TextAlign.Center else TextAlign.Start
    Column(
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = page.eyebrow,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = page.accent,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = alignment,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = alignment,
        )
        Spacer(Modifier.height(14.dp))

        page.features.forEach { (icon, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(page.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = page.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
