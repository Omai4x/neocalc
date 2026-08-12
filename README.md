# NeoCalc

A calculator that also does money, for Android. Scientific keypad with real
operator precedence, 162 currencies that keep working offline, unit conversion,
and a bill splitter that adds up to the penny.

No account, no analytics, no advertising, no trackers.

**[neocalc landing page](https://omai4x.github.io/neocalc/)** ·
[Privacy](https://omai4x.github.io/neocalc/privacy.html) ·
[Terms](https://omai4x.github.io/neocalc/terms.html)

## What it does

- **Calculator** — trigonometry, logs, powers, roots, factorials, memory,
  degrees or radians. Multiplication binds tighter than addition, brackets are
  on the main keypad, and results are grouped, auto-sized and switched to
  exponent form when they need to be.
- **Currency** — 162 currencies with searchable country flags, plus Bitcoin,
  Ethereum and gold. Pin the ones you use and they appear on a board under your
  result. A 30-day trend sits under every pair.
- **Offline** — rates are cached the moment they arrive, so a cold start with no
  signal still answers and says how old the numbers are.
- **Split** — tip, tax, round-up, and an exact penny distribution. Or split by
  what each person actually ordered.
- **Plain words** — "300 dollars in naira", "20% off 45", "15 miles in km".
  Scan a price with the camera, or share text in from any app.
- **Alerts** — watch a pair in the background; told once when it crosses.
- **Units** — length, mass, temperature, area, volume, speed, data and more,
  plus units you define yourself.
- **Widget and tile** — last converted pair on the home screen, and a Quick
  Settings shortcut.

There is also an arcade in there somewhere.

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # 205 unit tests
./gradlew assembleRelease        # per-ABI release APKs
./gradlew bundleRelease          # Play Store bundle
```

A release build needs a `keystore.properties` in the project root:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

That file and the keystore itself are gitignored and must stay that way.

> ABI splits and app bundles are mutually exclusive in AGP, so the splits block
> switches itself off when a `bundle*` task is requested. Run `assembleRelease`
> and `bundleRelease` as separate commands, not in one invocation.

## Layout

| Path | What |
| --- | --- |
| `calculator/` | Engine (pure state machine), keypad, display formatting |
| `convert/` | Rate providers, cache, pickers, board, trend, custom units |
| `split/` | Bill splitting, even and itemised |
| `smart/` | Natural language, share target, Quick Settings tile, OCR |
| `alerts/` | Rate alerts and the background worker |
| `games/` | 31 games behind an easter egg |
| `about/` | Privacy, terms, release notes |
| `ui/` | Theme tokens, window size, motion |
| `docs/` | The landing page, served by GitHub Pages |

Design tokens and the reasoning behind them are in
`design-system/neocalc/MASTER.md`.

## Tech

Kotlin, Jetpack Compose, Material 3. minSdk 23. The rate providers are keyless
and the JSON parsing is `org.json`, so live rates cost no dependency. The only
libraries beyond Compose are WorkManager (background alerts) and ML Kit text
recognition (on-device price scanning).

## Licence

Apache 2.0 — see [LICENSE](LICENSE).
