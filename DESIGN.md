# Design notes

This document is the shared memory for the relay chain: it records the
architecture and every non-obvious decision so later phases don't have to
reverse-engineer them.

## Architecture

```
┌─────────────── Android UI layer ───────────────┐
│ MainActivity → GameView (custom View)          │
│   · Choreographer tick loop          (phase 2) │
│   · onTouchEvent swipe / D-pad       (phase 2) │
│   · Canvas 2D rendering + FX        (phases 1,4)│
│   · onStateChanged → overlay sync    (phase 3) │
│   · DataStore best score + difficulty (phase 3)│
│   · SoundPool SFX via SoundPlayer     (phase 4)│
│ Overlays: menu / pause / game-over (phase 3)  │
└──────────────────────┬─────────────────────────┘
                       │ drives
┌──────────────────────▼─────────────────────────┐
│ SnakeGame  (pure Kotlin, no Android imports)   │
│   · board geometry, snake, food, score, state  │
│   · setDirection(dir) + update() per tick      │
│   · difficulty: tick curve config (phase 3)    │
│   · onFoodEaten / onGameOver events  (phase 4) │
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
- `tickIntervalMillis` shrinks with score along the chosen `Difficulty`
  curve: `base − score × speedUp`, floored at `min`. `NORMAL` = the original
  formula (`300ms − 6ms/point`, floor `90ms`), `EASY` starts slower (360ms,
  −4/point, floor 130ms), `HARD` faster (250ms, −8/point, floor 80ms).

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

- **Phase 2 ✅ — Game loop & input.** `GameView` runs a `Choreographer`-driven
  loop that redraws every frame and steps the engine with a `TickAccumulator`
  (only `update()` when `elapsed ≥ tickIntervalMillis`). Input: swipe on
  `GameView` and an on-screen D-pad in `activity_main.xml`, both routed through
  `GameView.pressDirection(dir)`. A tap toggles pause/resume via
  `GameView.togglePause()`. Score `TextViews` are wired in `MainActivity`.
- **Phase 3 ✅ — Game states & persistence (this commit).**
  - Overlays: `activity_main.xml`'s root `FrameLayout` has three full-screen,
    semi-transparent, `clickable` overlays — menu / pause / game-over. Exactly
    one is visible at a time, driven by `GameView.onStateChanged` →
    `MainActivity.syncOverlays(state)`. While visible an overlay covers the
    board, HUD and D-pad, so it owns all touch input (the `GameView` and D-pad
    underneath are inert). The D-pad is additionally set `GONE` outside
    `PLAYING`.
  - Overlay buttons: menu `Start` → `GameView.startGame()`; pause `Resume` →
    `resumeGame()`, `Restart` → `restartGame(difficulty)`, `Menu` →
    `newGame(difficulty)`; game-over `Restart`/`Menu` likewise. The Phase 2
    `GAME_OVER → reset()` shortcut in `togglePause()` was **removed** and the
    on-board "Swipe to start" hint was dropped (the menu overlay replaces it).
    `pressDirection` keeps its `MENU → start()` branch as an unreachable-in-
    practice safety net.
  - Persistence: `BestScoreStore` (DataStore Preferences, file
    `snake_rush_prefs`) stores the all-time best as a monotonic write
    (`recordScore` only increases). `MainActivity` seeds its in-memory
    `bestScore` from the store's `Flow` and writes on every score change.
  - Difficulty: `Difficulty` enum (easy/normal/hard) is a new `SnakeGame`
    constructor parameter (default `NORMAL`, preserving the original tick
    formula and all prior call sites). `GameView.newGame(difficulty)` /
    `restartGame(difficulty)` rebuild the engine with the chosen curve.
- **Testing:** `internal` members (`placeFoodAt`, `loadStateForTesting`) exist
  only to build deterministic engine scenarios — keep them `internal`, don't
  expose them in public API.
- **Phase 4 ✅ — Sound, animation, CI & release prep (this commit).**
  - *Sound event flow.* The engine stays Android-free; it exposes two pure
    Kotlin callback vars: `SnakeGame.onFoodEaten(GridPoint)` fires with the
    consumed cell **before** respawn (so on a full board `onGameOver` follows
    it), and `SnakeGame.onGameOver(reason)` fires from `gameOver()`. `GameView`
    forwards both through its own `onFoodEaten` / `onGameOver` and also uses
    the eat event to start its burst effect; state-driven cues (start / pause)
    ride the existing `onStateChanged`. `MainActivity` maps all of it to
    `SoundPlayer` (`SoundPool`, max 2 streams) over four synthesized WAV clips
    in `res/raw`, regenerated deterministically by `tools/generate_sounds.py`
    (stdlib-only, byte-identical on re-run). No audio library added.
  - *Animation timing.* Effects are anchored to the Choreographer vsync time
    (`renderTimeNanos`), never to tick counts, so cosmetics cannot perturb the
    fixed-tick simulation. One-shot effects (food-eaten expanding ring,
    game-over shake) age against absolute timestamps and self-expire. Looping
    effects (food pulse, head bounce) use `animTimeNanos`, a clock that
    advances only while `PLAYING` and is **reset otherwise** — identical
    convention to `TickAccumulator` — so a pause can never make them jump and
    no animation time accumulates in MENU / PAUSED / GAME_OVER. `onDraw` still
    only reads; effect cleanup happens in `advance()`.
  - *CI.* `.github/workflows/ci.yml` builds on `ubuntu-latest` with JDK 17:
    `./gradlew test assembleDebug` on push to `main` and on PRs, plus
    `assembleRelease` (unsigned) to catch R8 / resource-shrink regressions.
    No `-Pandroid.aapt2FromMavenOverride` is used in CI — x86_64 hosts ship the
    official `aapt2`, and that override is documented as a purely local
    arm64-sandbox workaround (do not add it to `gradle.properties`).
  - *Release signing.* The `release` build type has `isMinifyEnabled = true`
    and `isShrinkResources = true`. It is signed **only** when a gitignored
    `keystore.properties` exists (generated by `tools/generate_keystore.sh`),
    otherwise it packages unsigned — so `assembleRelease` works everywhere
    without secrets. The keystore and its passwords are never committed;
    `keystore.properties` and `keystore/` are in `.gitignore`. For Play
    publishing, feed the credentials via CI secrets instead.

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
  `MENU → start()+setDirection` (kept as a safety net; the menu overlay
  normally owns that transition), `PLAYING → setDirection`, else no-op. Tap
  (ACTION_UP without travel) calls `togglePause()`: `PLAYING → pause()`,
  `PAUSED → resume()`, MENU/GAME_OVER are no-ops (overlays own those flows
  since Phase 3). `onDraw` only reads engine state — mutations happen in the
  frame callback only.
- **HUD.** `GameView.onScoreChanged(score)` fires after a scoring tick;
  `MainActivity` formats both `TextViews` via `strings.xml` format args. Note:
  `android:text="@string/score_label"` would render the raw `%1$d` literal,
  so `MainActivity` sets initial text in code. Since Phase 3, `bestScore` is
  backed by DataStore (see Phase 3 decisions).
- **D-pad layout.** Root stays a `FrameLayout` (overlays rely on it).
  The D-pad overlays the bottom of the screen; `GameView.onSizeChanged`
  subtracts `R.dimen.board_bottom_reserve` (128dp) from the available height
  so the board never hides under the buttons — keep that dimen in sync with
  the D-pad's actual height in `activity_main.xml`. Since Phase 3 the D-pad
  is `GONE` whenever an overlay is shown (not `PLAYING`).

## Phase 3 decisions (relay notes)

- **Overlay state sync.** State transitions originate in the engine but are
  *applied* from three places in `GameView`: the frame callback (`advance()`
  sees `GAME_OVER` from `update()`), the tap handler (`togglePause()`), and
  the round-lifecycle methods (`startGame`/`resumeGame`/`newGame`/
  `restartGame`/`pressDirection`). All funnel through `syncState()`, which
  fires `onStateChanged` once per actual transition; `MainActivity.syncOverlays`
  is the single place that maps state → visibility. `newGame()` always
  notifies (even MENU→MENU) so the menu's best-score label and HUD refresh
  when difficulty changes.
- **Overlay touch ownership.** Each overlay root is a full-screen
  `FrameLayout` with `background=overlay_bg` (≈90% opaque), `clickable=true`
  and `focusable=true`. `View.GONE` overlays never participate in touch
  dispatch, so there is zero interference during `PLAYING`; while visible, an
  overlay consumes everything except its own buttons. The score HUD sits
  *under* the overlays and is intentionally hidden while they show.
- **DataStore.** `BestScoreStore` is a tiny wrapper over DataStore Preferences
  (`snake_rush_prefs`, top-level `preferencesDataStore` delegate keyed on
  `Context`). `bestScore: Flow<Int>` feeds the HUD reactively;
  `recordScore(score)` is a monotonic upsert, so racing writes can only raise
  the stored value. `MainActivity` runs collection/writes on a manually-owned
  `uiScope` (`SupervisorJob` + `Dispatchers.Main.immediate`) cancelled in
  `onDestroy` — the activity extends plain `Activity`, so there is no
  `lifecycleScope` (adding `ComponentActivity`/lifecycle-runtime would be the
  alternative; not needed for one preference).
- **Difficulty injection.** `Difficulty` lives in `.game` and is a constructor
  parameter of `SnakeGame` (default `NORMAL`), so `SnakeGame()` keeps its
  documented tick formula and all Phase 1/2 tests still hold. The `NORMAL`
  entry references the companion constants (`BASE_TICK_MILLIS` etc.) — they
  are `const val`, inlined at compile time, so there is no class-init cycle.
  Changing difficulty builds a fresh engine via `GameView.newGame` /
  `restartGame` (the engine's `difficulty` is immutable per round). Unit tests
  assert `NORMAL` == baseline, ordering (easy < normal < hard in speed), and
  that every difficulty respects its own floor.
- **New dependencies** (Phase 3): `androidx.datastore:datastore-preferences`
  (1.1.1) and `org.jetbrains.kotlinx:kotlinx-coroutines-android` (1.7.3, for
  `Dispatchers.Main`). Everything else stays dependency-free. The
  `libdatastore_shared_counter.so` strip warning during packaging is benign.

## Conventions

- Package root `com.snakerush`; engine in `.game`, UI in `.ui`.
- Pure, JVM-testable logic goes in `.game` even when the UI layer consumes it
  (`TickAccumulator`, `SwipeInterpreter`, `Difficulty`). Android-only glue
  stays in `.ui` / `MainActivity` / `BestScoreStore` / `SoundPlayer` (DataStore
  and SoundPool are Android APIs).
- Gameplay stays deterministic and fixed-tick; anything visual that must move
  at display rate uses the frame timestamp (`renderTimeNanos` / `animTimeNanos`
  in `GameView`), and animation clocks never advance outside `PLAYING`.
- All SFX are synthesized clips in `res/raw` regenerated by a checked-in
  script — hand-authoring binary audio or pulling an audio library is not
  needed.
- Version catalog not used yet; plugin versions live in root `build.gradle.kts`.
- Colors live both in `res/values/colors.xml` and as constants in `GameView`
  (hot draw path avoids resource lookups). Overlay chrome (buttons, texts)
  uses resources, not draw-path constants.

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
