# Snake Rush 🐍

A classic Snake game for Android, developed as a staged **AI chain-relay project**:
multiple AI sessions each complete one phase and hand off a prompt to the next.

## Tech stack

| Layer       | Choice                              |
|-------------|-------------------------------------|
| Language    | Kotlin                              |
| UI          | Single custom `View` (Canvas 2D)    |
| Game engine | Pure Kotlin, zero Android deps      |
| Build       | Gradle (Kotlin DSL) + AGP 8.5.2     |
| Min / Target| API 26 / API 35                     |

## Building

```bash
# Requires JDK 17 and the Android SDK (set ANDROID_HOME or local.properties).
./gradlew assembleDebug          # build the APK
./gradlew test                   # run JVM unit tests (engine)
```

## Project layout

```
app/src/main/java/com/snakerush/
├── MainActivity.kt      # thin host activity
├── game/                # pure-Kotlin engine (JVM-testable)
│   ├── Direction.kt     # 4-way movement enum
│   ├── GameState.kt     # MENU / PLAYING / PAUSED / GAME_OVER
│   ├── GridPoint.kt     # immutable board cell
│   └── SnakeGame.kt     # all game rules + state
└── ui/
    └── GameView.kt      # Canvas renderer (static in phase 1)
app/src/test/java/com/snakerush/game/
└── SnakeGameTest.kt     # JVM unit tests for the engine
```

## Roadmap (relay phases)

- **Phase 1 ✅ — Scaffold & engine (this commit).** Project skeleton, Gradle
  wrapper, launcher icon, pure-Kotlin `SnakeGame` engine with unit tests,
  minimal static renderer so the app launches. **Build verified:** 11/11
  engine unit tests pass and `assembleDebug` produces `app-debug.apk`.
- **Phase 2 — Game loop & input.** Choreographer-driven fixed-tick loop,
  swipe + on-screen D-pad input, score HUD wiring, start/pause.
- **Phase 3 — Game states & persistence.** Menu / pause / game-over overlays,
  best-score storage (DataStore), difficulty settings.
- **Phase 4 — Polish & QA.** Sound, animations, edge-case unit tests,
  GitHub Actions CI build, release signing.

See [DESIGN.md](DESIGN.md) for architecture details and data structures.
