package com.omai.neocalc

import com.omai.neocalc.smart.SmartIntake
import com.omai.neocalc.alerts.RateAlerts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.omai.neocalc.onboarding.Onboarding
import com.omai.neocalc.onboarding.OnboardingScreen
import com.omai.neocalc.ui.WithReducedMotion
import com.omai.neocalc.ui.WithWindowSize
import com.omai.neocalc.ui.theme.FirstTestAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Anything already scheduled keeps running; this only matters on a
        // first launch after the alerts feature was added.
        if (RateAlerts.all(this).isNotEmpty()) RateAlerts.schedule(this)
        RateAlerts.ensureChannel(this)

        setContent {
            val context = LocalContext.current
            // Read once on launch; completing the tour flips it for good, so a
            // returning user goes straight to the calculator.
            var showTour by remember { mutableStateOf(Onboarding.needed(context)) }
            var shared by remember { mutableStateOf(intent?.getStringExtra(SmartIntake.EXTRA_TEXT)) }

            // No in-app control: the appearance follows the system setting and
            // recomposes on its own when the device switches.
            FirstTestAppTheme(darkTheme = isSystemInDarkTheme()) {
                // Measured once at the root; every screen reads it from there
                // rather than measuring itself.
                WithWindowSize {
                    WithReducedMotion {
                    if (showTour) {
                        OnboardingScreen(
                            onFinish = {
                                Onboarding.complete(context)
                                showTour = false
                            },
                        )
                    } else {
                        CalculatorApp(
                            onReplayTour = { showTour = true },
                            // Text shared in from another app, the share sheet or
                            // the Quick Settings tile.
                            sharedText = shared,
                            onSharedConsumed = { shared = null },
                        )
                    }
                    }
                }
            }
        }
    }
}
