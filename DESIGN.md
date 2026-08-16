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

- **Phase 2 (next):** add a `Choreographer`-driven loop in `GameView`
  (accumulator pattern: only step `update()` when `elapsed ≥ tickIntervalMillis`,
  redraw every frame). Input: swipe on `GameView` and/or a D-pad overlay.
  On first swipe in `MENU`, call `game.start()`. Wire the score `TextViews`
  in `activity_main.xml`.
- **Phase 3:** add overlay layouts in `activity_main.xml`'s root `FrameLayout`;
  persist best score (e.g. DataStore); pause on `onPause()`.
- **Testing:** `internal` members (`placeFoodAt`, `loadStateForTesting`) exist
  only to build deterministic engine scenarios — keep them `internal`, don't
  expose them in public API.

## Conventions

- Package root `com.snakerush`; engine in `.game`, UI in `.ui`.
- Version catalog not used yet; plugin versions live in root `build.gradle.kts`.
- Colors live both in `res/values/colors.xml` and as constants in `GameView`
  (hot draw path avoids resource lookups).

## Environment quirks (arm64 sandbox)

- The Phase 1 sandbox is **aarch64 Linux**; Google's Maven `aapt2` binary is
  x86_64-only, so resource linking fails out of the box. Workaround used:
  copy the aarch64 `aapt2` from `lzhiyong/android-sdk-tools` release
  `35.0.2` (`android-sdk-tools-static-aarch64.zip`) into
  `$ANDROID_HOME/build-tools/34.0.0/aapt2` and set
  `android.aapt2FromMavenOverride=...` in `local.properties` (or
  `gradle.properties`). This is machine-specific and must NOT be committed.
- JDK 17 on this image is broken (missing `conf/security/java.security`);
  use JDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`). Gradle 8.9
  and the project both work fine on JDK 21.
