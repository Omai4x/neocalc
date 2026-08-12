package com.omai.neocalc.about

/**
 * The app's legal text and release history, kept as data rather than as a web
 * page.
 *
 * Both screens are read offline, and a bundled document cannot change under the
 * user or vanish when a domain lapses. It also means the policy the user agreed
 * to is exactly the one shipped in that build.
 *
 * Written for an app that genuinely collects nothing: if that ever stops being
 * true, this file has to change in the same commit.
 */
object Legal {

    /** Bump when the wording changes materially, so acceptance can be re-asked. */
    const val VERSION = 1

    const val EFFECTIVE = "12 August 2026"

    data class Section(val heading: String, val body: String)

    val PRIVACY: List<Section> = listOf(
        Section(
            "The short version",
            "NeoCalc has no accounts, no analytics, no advertising and no trackers. " +
                "Nothing you type is sent anywhere, and nothing about you is collected, " +
                "stored on a server, or sold.",
        ),
        Section(
            "What stays on your device",
            "Your calculations, history tape, pinned currencies, saved exchange rates, " +
                "custom units, rate alerts, bill splits and game scores are written to " +
                "this device's private storage. They are readable only by this app, they " +
                "are removed when you uninstall it, and they are never uploaded. Cloud " +
                "backup is switched off for this app's data, so none of it reaches Google " +
                "Drive; moving to a new phone with Android's direct transfer does copy it, " +
                "handset to handset, without passing through a server. You can also export " +
                "it yourself at any time from About.",
        ),
        Section(
            "What leaves your device",
            "Only requests for exchange rates. When you convert a currency the app asks " +
                "a public rate service for the day's rates for one base currency. That " +
                "request contains no identifier and no personal data. The providers are " +
                "open.er-api.com, api.frankfurter.app and api.coingecko.com; their own " +
                "servers will see your IP address, as they would for any web request.",
        ),
        Section(
            "Clipboard",
            "When you open the converter the app reads the clipboard once to see whether " +
                "it holds a price it could offer to convert. The contents are examined in " +
                "memory on your device, are never stored, and are never transmitted. On " +
                "Android 12 and later the system shows its own notice when this happens.",
        ),
        Section(
            "Camera",
            "Scanning a price uses the camera and on-device text recognition. The photo " +
                "and the recognised text stay on the device and are not saved or uploaded. " +
                "The camera is used only while you are actively scanning.",
        ),
        Section(
            "Notifications",
            "If you create a rate alert the app checks that pair periodically in the " +
                "background and posts a notification when your target is crossed. You can " +
                "delete alerts at any time, and revoking the notification permission stops " +
                "them without affecting anything else.",
        ),
        Section(
            "Children",
            "The app is suitable for all ages and collects no data from anyone, including " +
                "children.",
        ),
        Section(
            "Changes",
            "If this policy changes, the updated text ships inside the app and the " +
                "effective date above changes with it.",
        ),
    )

    val TERMS: List<Section> = listOf(
        Section(
            "Using the app",
            "NeoCalc is provided free of charge for personal use. You may use it however " +
                "you like, on as many devices as you like. There is nothing to sign up " +
                "for and nothing to pay.",
        ),
        Section(
            "Exchange rates are indicative",
            "Rates come from free public reference sources and are provided for " +
                "information only. They are daily reference figures, not dealing prices, " +
                "and they do not include any spread, commission or fee that a bank or a " +
                "bureau would charge you. Saved rates may be hours or days old, and the " +
                "app tells you when they are. Do not rely on them for trading, accounting, " +
                "tax, or any decision where being wrong would cost you money.",
        ),
        Section(
            "Crypto and gold",
            "Digital asset prices and the gold figure are third-party quotes and can move " +
                "sharply between checks. Gold is derived from a tokenised gold price and " +
                "is a proxy for the spot price of a troy ounce, not a bullion dealer's " +
                "quote.",
        ),
        Section(
            "Rate alerts",
            "Alerts depend on background execution and a network connection, both of " +
                "which the operating system may delay or withhold to save battery. An " +
                "alert may arrive late or not at all, and must not be treated as a " +
                "reliable trigger for anything that matters.",
        ),
        Section(
            "Accuracy",
            "The calculator, converter and bill splitter are tested, but no software is " +
                "free of defects. Check anything important. The app is provided as is, " +
                "without warranty of any kind, and its authors are not liable for any loss " +
                "arising from its use, to the extent the law allows.",
        ),
        Section(
            "Third-party services",
            "Rates are retrieved from services operated by other people. Their " +
                "availability, accuracy and terms are theirs, not ours, and they can " +
                "change or stop at any time.",
        ),
        Section(
            "The arcade",
            "The games are included for amusement. Scores are stored on your device only " +
                "and are not submitted anywhere.",
        ),
    )
}

/** One shipped version, newest first. */
data class Release(
    val version: String,
    val date: String,
    val headline: String,
    val changes: List<String>,
)

object ReleaseNotes {

    val ALL: List<Release> = listOf(
        Release(
            version = "1.3.1",
            date = "13 August 2026",
            headline = "Fixes a crash on launch.",
            changes = listOf(
                "1.3 could not start at all once it was minified for release: the optimiser removed a class that the background scheduler looks up by name, and the app died before drawing a frame. It is kept now.",
                "Rate alerts would have failed the same way even after launching, because the worker is also found by name. Also kept.",
            ),
        ),
        Release(
            version = "1.3",
            date = "12 August 2026",
            headline = "A new look, built around the numbers.",
            changes = listOf(
                "Redesigned around operation orange on warm black: the operator keys are the only ones that change what the next digit means, so they are the only ones that pull the eye.",
                "Digits no longer shift as a result updates. Every number now uses tabular figures.",
                "One typeface throughout, with a display tuned for long results.",
                "Every colour pair in the app now meets WCAG AA contrast in both light and dark.",
                "Animations stop completely when your device is set to remove them.",
            ),
        ),
        Release(
            version = "1.2",
            date = "12 August 2026",
            headline = "The maths is right, and your data is yours.",
            changes = listOf(
                "The keypad now respects operator precedence: 2 + 3 × 4 is 14, not 20.",
                "Brackets on the main keypad, with a depth indicator you can tap to close.",
                "Split a bill by what each person actually ordered, not just evenly.",
                "The calculator shows a live conversion of whatever is on screen.",
                "Export everything to a file, import it back, or save your history as CSV.",
                "Cloud backup is switched off for this app's data, so it stays on your device.",
                "The widget now shows your pinned currencies as well as your last pair.",
                "Tablets show the calculator and converter side by side.",
                "The keypad has haptics.",
                "Spanish and French translations.",
            ),
        ),
        Release(
            version = "1.1",
            date = "12 August 2026",
            headline = "Money, but cleverer.",
            changes = listOf(
                "New Split tab: tip, tax, round-up, and a penny-exact share for everyone.",
                "Ask in plain words: \"300 dollars in naira\", \"20% off 45\", \"15 miles in km\".",
                "Scan a price with the camera; the photo never leaves your device.",
                "Share text into the app from anywhere, or use the Quick Settings tile.",
                "Rate alerts that watch a pair in the background and tell you once when it crosses.",
                "Bitcoin, Ethereum and gold alongside the 162 currencies.",
                "Define your own units, like 1 crate = 24 bottles.",
                "Achievements across the arcade, with 29 more games added.",
                "The calculator display now groups thousands, shrinks to fit, and switches to exponent form.",
                "Icons throughout in place of emoji, and a layout that adapts to phones, tablets and split screen.",
                "Privacy policy, terms and release notes now live in the app, readable offline.",
            ),
        ),
        Release(
            version = "1.0",
            date = "12 August 2026",
            headline = "The first release.",
            changes = listOf(
                "Scientific calculator with memory, degrees and radians, and a history tape.",
                "Currency conversion across 162 currencies with searchable flags, plus crypto and gold.",
                "Offline rate cache, so a cold start with no signal still answers.",
                "30-day trend sparkline under every currency pair.",
                "Multi-currency board driven by the currencies you pin.",
                "Bill splitter with tip, tax, round-up and an exact penny split.",
                "Natural language input: \"300 dollars in naira\", \"20% off 45\".",
                "Scan a price with the camera, or share text into the app from anywhere.",
                "Rate alerts that watch a pair in the background.",
                "Custom units you define yourself.",
                "Home-screen widget and a Quick Settings tile.",
                "Unit conversion across length, mass, temperature, area, volume and more.",
                "Follows your system light and dark theme.",
                "A hidden arcade, for when the sums are done.",
            ),
        ),
    )
}
