package com.omai.neocalc.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has asked the system to stop animating.
 *
 * Android expresses this as an animator duration scale of zero, set either in
 * Developer options or by the accessibility "Remove animations" setting. Compose
 * has no equivalent of the web's prefers-reduced-motion, so the value is read
 * once and published, and the animated parts of the app consult it instead of
 * each reaching for a Context.
 *
 * Reduced motion means *no* motion here, not slower motion: someone who turns
 * this on is usually doing it because movement makes them ill, and a gentler
 * version of the same movement does not help them.
 */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

@Composable
fun WithReducedMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Read once per composition root: the setting changes about as often as a
    // device is reconfigured, and polling it per frame would cost more than it
    // could ever save.
    val reduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/**
 * A duration in milliseconds, or zero when the user has asked for no motion.
 * Written this way so a call site reads as an ordinary animation spec.
 */
@Composable
fun motionDuration(millis: Int): Int = if (LocalReducedMotion.current) 0 else millis
