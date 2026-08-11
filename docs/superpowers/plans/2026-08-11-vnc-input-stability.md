# RemoteX VNC Input Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate FIX 10 VNC scroll/cursor gesture regressions and add an explicit orientation toggle.

**Architecture:** Keep RFB transport unchanged. Correct relative input math in the pure Kotlin interpreter, isolate multi-touch lifecycle state in a pure guard, and make `VncSurfaceView` consume those abstractions. Keep orientation state in `VncScreen`.

**Tech Stack:** Kotlin 2.3.21, Android View `MotionEvent`, Jetpack Compose, JUnit, shell regression checks, GitHub Actions.

## Global Constraints

- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` unchanged.
- VNC RFB engine behavior unchanged except for the direction of wheel events generated from touch input.
- SSH/SFTP/database untouched.
- Trackpad remains default.
- VNC still opens landscape and fullscreen by default.

---

### Task 1: Reproduce input regressions

**Files:**
- Modify: `feature/vnc/src/test/kotlin/com/remotex/feature/vnc/VncPureTest.kt`
- Create: `scripts/tests/check-vnc-input-stability.sh`

- [ ] Add a micro-jitter regression that alternates `-0.2/+0.2` deltas and expects zero net movement.
- [ ] Add natural-scroll expectations: `dy=-40` => vertical step `+1`; `dy=+40` => `-1`.
- [ ] Add a multi-touch lifecycle expectation that suppresses one-finger movement after two pointers were observed.
- [ ] Run the new source contract against FIX 10 and observe RED.

### Task 2: Fix pointer and scroll math

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadGestureInterpreter.kt`

- [ ] Integrate deltas using per-axis fractional remainders and `roundToInt()`.
- [ ] Clear fractional remainder when movement is clamped at framebuffer bounds.
- [ ] Move scroll accumulation into `scroll(dx, dy, stepPx)` and invert finger deltas for natural scrolling.
- [ ] Add `resetGesture()` for gesture-boundary state reset.
- [ ] Run pure Kotlin regression harness and observe GREEN.

### Task 3: Guard multi-touch transitions

**Files:**
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadTouchGuard.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt`

- [ ] Begin/reset guard on `ACTION_DOWN`/gesture end.
- [ ] Mark multi-touch on pointer counts above one.
- [ ] Suppress one-finger pointer movement after multi-touch until a fresh `ACTION_DOWN`.
- [ ] Handle `ACTION_POINTER_UP` and rebaseline to remaining pointer coordinates.
- [ ] Replace duplicated Surface scroll accumulation with `TrackpadGestureInterpreter.scroll()`.

### Task 4: Add manual rotate control

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Persist requested orientation with `rememberSaveable`.
- [ ] Default to sensor-landscape.
- [ ] Add toolbar `Putar` action to toggle sensor-portrait/sensor-landscape.
- [ ] Restore unspecified orientation when VNC closes.
- [ ] Handle `orientation|screenSize` in `MainActivity` so rotation preserves the active remote session and does not trigger app lock.

### Task 5: Local-test fallback and CI coverage

**Files:**
- Modify: `scripts/tests/check-vnc-trackpad-polish.sh`
- Modify: `.github/workflows/build-debug.yml`
- Modify: `.github/workflows/release.yml`

- [ ] Make the existing trackpad script use a static fallback when `kotlinc`/Java are unavailable.
- [ ] Run the new input-stability and existing VNC-polish scripts in debug CI.
- [ ] Run the same source checks before release build.
- [ ] Run all available local source/pure tests and whitespace checks.
- [ ] Package FIX 11 patch and a full source backup; GitHub Actions remains the full Android build proof.
