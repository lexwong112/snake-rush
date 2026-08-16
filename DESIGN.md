# Design notes

This document is the shared memory for the relay chain: it records the
architecture and every non-obvious decision so later phases don't have to
reverse-engineer them.

## Architecture

```
┌─────────────── Android UI layer ───────────────┐
│ MainActivity → GameView (custom View)          │
│   · Choreographer tick loop        (phase 2)   │
│   · onTouchEvent swipe / D-pad     (phase 2)   │
│   · Canvas 2D rendering            (phase 1: static) │
└──────────────────────┬─────────────────────────┘
                       │ drives
┌──────────────────────▼─────────────────────────┐
│ SnakeGame  (pure Kotlin, no Android imports)   │
│   · board geometry, snake, food, score, state  │
│   · setDirection(dir) + update() per tick      │
└────────────────────────────────────────────────┘
```

**Why a pure-Kotlin engine?** It is JVM-unit-testable without an emulator or
Robolectric, keeps the game rules free of Android lifecycle concerns, and the
UI layer stays a dumb driver. Keep it that way: **do not import Android types
into `com.snakerush.game`.**

## Board model

- Grid of `cols × rows` cells, `GridPoint(x, y)` — `(0,0)` top-left,
  `x` right, `y` down. Defaults: `18 × 24`.
- Snake stored **head-first** in `ArrayDeque<GridPoint>`; `head = first`,
  tail = `last`.
- `tickIntervalMillis` shrinks with score (`300ms − 6ms/point`, floor `90ms`).

## Key rules (engine contract)

1. `update()` is a no-op unless `state == PLAYING`.
2. A tick commits one queued direction, then moves the head one cell:
   - out of bounds → `GAME_OVER` ("Hit the wall!");
   - new head on the body → `GAME_OVER` ("Bit yourself!"), **except** the
     tail cell, which is vacated unless the snake grows this tick;
   - new head on food → grow by one, `+10` score, respawn food (if the board
     is full, that's a win → `GAME_OVER` with "Victory…").
3. **Input buffering (subtle!):** `setDirection` enqueues into a small
   `pending` deque (max 3). Reversal checks compare against the **last queued**
   direction, not the current one — so a quick "swipe down then left" within
   one tick turns DOWN first, then LEFT, instead of cancelling into a 180°
   self-reversal. See `SnakeGame.setDirection`.
4. `reset()` returns to `MENU` and rebuilds the initial 3-cell snake heading
   right from the board center.

## Phase-specific contracts

- **Phase 2 ✅ — Game loop & input (this commit).** `GameView` runs a
  `Choreographer`-driven loop that redraws every frame and steps the engine
  with a `TickAccumulator` (only `update()` when `elapsed ≥ tickIntervalMillis`).
  Input: swipe on `GameView` and an on-screen D-pad in `activity_main.xml`,
  both routed through `GameView.pressDirection(dir)`. First press in `MENU`
  calls `game.start()` (and queues the press itself); a tap toggles
  pause/resume via `GameView.togglePause()`. Score `TextViews` are wired in
  `MainActivity` (best score kept in memory; persistence is Phase 3).
- **Phase 3:** add overlay layouts in `activity_main.xml`'s root `FrameLayout`;
  persist best score (e.g. DataStore); replace the GAME_OVER tap-to-reset
  convenience in `GameView.togglePause()` with a proper game-over overlay.
- **Testing:** `internal` members (`placeFoodAt`, `loadStateForTesting`) exist
  only to build deterministic engine scenarios — keep them `internal`, don't
  expose them in public API.

## Phase 2 decisions (relay notes)

- **Fixed-tick loop.** One `Choreographer.FrameCallback` per frame:
  `advance()` then `invalidate()`. `TickAccumulator.drain(elapsedNanos,
  tickIntervalNanos)` returns how many whole ticks elapsed and keeps the
  remainder. The tick interval is re-read every frame, so `SnakeGame`'s
  speed-up-on-score applies immediately.
- **Loop lifecycle.** `startLoop()`/`stopLoop()` are idempotent and are called
  from `MainActivity.onResume()`/`onPause()` (plus `onAttachedToWindow`/
  `onDetachedFromWindow` as a safety net). `stopLoop()` does NOT change game
  state — it just freezes the sim. `startLoop()` re-bases `lastFrameNanos`,
  and the accumulator is reset whenever the state is not `PLAYING`, so a long
  background pause never bursts a backlog of ticks on resume.
- **Gesture thresholds.** `SwipeInterpreter` (pure logic): movement
  `< maxTapDistance (16px)` → `Tap`; between 16px and `minSwipeDistance
  (48px)` → `None`; ≥ 48px → `Swipe` on the dominant axis. These are
  constructor defaults, overridable in tests.
- **Input mapping.** Swipe and D-pad both end at `pressDirection(dir)`:
  `MENU → start()+setDirection`, `PLAYING → setDirection`, else no-op. Tap
  (ACTION_UP without travel) calls `togglePause()`: `PLAYING → pause()`,
  `PAUSED → resume()`, `GAME_OVER → reset()` (throwaway until Phase 3 adds a
  real overlay). `onDraw` only reads engine state — mutations happen in the
  frame callback only.
- **HUD.** `GameView.onScoreChanged(score)` fires after a scoring tick;
  `MainActivity` keeps the in-memory `bestScore` and formats both `TextViews`
  via `strings.xml` format args. Note: `android:text="@string/score_label"`
  would render the raw `%1$d` literal, so `MainActivity` sets initial text in
  code.
- **D-pad layout.** Root stays a `FrameLayout` (Phase 3 overlays rely on it).
  The D-pad overlays the bottom of the screen; `GameView.onSizeChanged`
  subtracts `R.dimen.board_bottom_reserve` (128dp) from the available height
  so the board never hides under the buttons — keep that dimen in sync with
  the D-pad's actual height in `activity_main.xml`.

## Conventions

- Package root `com.snakerush`; engine in `.game`, UI in `.ui`.
- Pure, JVM-testable logic goes in `.game` even when the UI layer consumes it
  (`TickAccumulator`, `SwipeInterpreter`). Android-only glue stays in `.ui` /
  `MainActivity`.
- Version catalog not used yet; plugin versions live in root `build.gradle.kts`.
- Colors live both in `res/values/colors.xml` and as constants in `GameView`
  (hot draw path avoids resource lookups).

## Environment quirks (arm64 sandbox)

- The sandbox is **aarch64 Linux**; Google's Maven `aapt2` artifact is
  x86_64-only, so resource linking fails out of the box (the daemon prints
  `Syntax error: "(" unexpected`). Workaround: copy the aarch64 `aapt2` from
  `lzhiyong/android-sdk-tools` release `35.0.2`
  (`android-sdk-tools-static-aarch64.zip`) into
  `$ANDROID_HOME/build-tools/34.0.0/aapt2`, then **pass the override as a
  `-P` project property on every `./gradlew` invocation**:
  `./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2`.
  ⚠️ AGP 8.5.2 reads this property from project properties only — putting it
  in `local.properties` does NOT work (verified empirically). It must not be
  committed; do not add it to `gradle.properties`.
- JDK 17 on this image is broken (missing `conf/security/java.security`);
  use JDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`). Gradle 8.9
  and the project both work fine on JDK 21.
