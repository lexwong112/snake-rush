# Snake Rush 🐍

A classic Snake game for Android, developed as a staged **AI chain-relay project**:
multiple AI sessions each complete one phase and hand off a prompt to the next.

## Tech stack

| Layer       | Choice                              |
|-------------|-------------------------------------|
| Language    | Kotlin                              |
| UI          | Single custom `View` (Canvas 2D)    |
| Game engine | Pure Kotlin, zero Android deps      |
| Persistence | DataStore Preferences                |
| Build       | Gradle (Kotlin DSL) + AGP 8.5.2     |
| Min / Target| API 26 / API 35                     |

## Building

```bash
# Requires JDK 17+ and the Android SDK (set ANDROID_HOME or local.properties).
# On the arm64 sandbox use JDK 21 and the aapt2 override — see DESIGN.md
# "Environment quirks".
./gradlew assembleDebug          # build the APK
./gradlew test                   # run JVM unit tests (engine + logic)
```

## Project layout

```
app/src/main/java/com/snakerush/
├── MainActivity.kt      # thin host activity: HUD, overlays, D-pad, lifecycle
├── BestScoreStore.kt    # DataStore-backed best-score persistence
├── game/                # pure-Kotlin engine + logic (JVM-testable)
│   ├── Difficulty.kt    # easy / normal / hard speed presets
│   ├── Direction.kt     # 4-way movement enum
│   ├── GameState.kt     # MENU / PLAYING / PAUSED / GAME_OVER
│   ├── GridPoint.kt     # immutable board cell
│   ├── SnakeGame.kt     # all game rules + state
│   ├── TickAccumulator.kt   # fixed-tick loop accumulator
│   └── SwipeInterpreter.kt  # pure-logic gesture classifier
└── ui/
    └── GameView.kt      # Canvas renderer + Choreographer loop + touch input
app/src/test/java/com/snakerush/game/
├── SnakeGameTest.kt     # JVM unit tests for the engine
├── TickAccumulatorTest.kt   # JVM unit tests for the tick loop
└── SwipeInterpreterTest.kt  # JVM unit tests for gesture classification
```

## Roadmap (relay phases)

- **Phase 1 ✅ — Scaffold & engine.** Project skeleton, Gradle wrapper,
  launcher icon, pure-Kotlin `SnakeGame` engine with unit tests, minimal
  static renderer so the app launches. **Build verified:** 11/11 engine unit
  tests pass and `assembleDebug` produces `app-debug.apk`.
- **Phase 2 ✅ — Game loop & input.** Choreographer-driven fixed-tick loop
  with a time accumulator, swipe gestures + on-screen D-pad (both mapped to
  `setDirection`), score/best-score HUD, first-swipe start and
  tap-to-pause/resume. **Build verified:** 28/28 JVM unit tests pass and
  `assembleDebug` produces `app-debug.apk`.
- **Phase 3 ✅ — Game states & persistence (this commit).** Full-screen
  menu / pause / game-over overlays in lockstep with the engine state
  (`GameView.onStateChanged`), best score persisted with DataStore
  (`BestScoreStore`), and an easy / normal / hard difficulty selector that
  injects the speed curve into the engine via a `SnakeGame` constructor
  parameter. The Phase 2 GAME_OVER tap-to-reset shortcut and the on-board
  "Swipe to start" hint were removed (the overlays own those flows now).
  **Build verified:** 32/32 JVM unit tests pass and `assembleDebug` produces
  `app-debug.apk`.
- **Phase 4 — Polish & QA.** Sound, animations, edge-case unit tests,
  GitHub Actions CI build, release signing.

See [DESIGN.md](DESIGN.md) for architecture details and data structures.
