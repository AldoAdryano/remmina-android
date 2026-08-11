# RemoteX VNC Polishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the working FIX 9 VNC client into a fullscreen, overlay-controlled, more precise mobile remote desktop without changing the RFB connection engine.

**Architecture:** Keep protocol and ViewModel responsibilities unchanged. Move all viewport/control polish into `VncScreen` and `VncSurfaceView`, and centralize trackpad movement acceleration in the pure Kotlin `TrackpadGestureInterpreter` so it can be regression tested without Android.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose BOM 2026.06.00, Android View gesture APIs, Material 3, JUnit/static shell regression checks.

## Global Constraints

- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` remain unchanged.
- VNC RFB authentication, encodings, reconnect policy, clipboard protocol, and screenshot persistence remain unchanged.
- `TRACKPAD` remains the default input mode.
- `FIT_SCREEN` remains the default scale mode.
- No SSH/SFTP/database changes.
- Full Android build verification is performed by GitHub Actions; local checks must not claim a full APK build.

---

### Task 1: Trackpad acceleration regression

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/TrackpadGestureInterpreter.kt`
- Modify: `feature/vnc/src/test/kotlin/com/remotex/feature/vnc/VncPureTest.kt`
- Create: `scripts/tests/check-vnc-trackpad-polish.sh`

**Interfaces:**
- Consumes: `TrackpadGestureInterpreter.move(...)`
- Produces: constructor parameters `pointerSpeed`, `acceleration`, `accelerationDistance`; bounded movement multiplier.

- [ ] Write a standalone Kotlin regression harness that expects short movement to receive less acceleration than long movement and expects clamping to framebuffer bounds.
- [ ] Run the harness and confirm RED because the acceleration constructor/API does not exist yet.
- [ ] Implement bounded acceleration in `TrackpadGestureInterpreter`.
- [ ] Update JUnit coverage to lock in precise movement, accelerated movement, clamping, and right click.
- [ ] Run the standalone regression harness and confirm GREEN.

### Task 2: Fullscreen overlay controls

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`
- Create: `scripts/tests/check-vnc-polish-source.sh`

**Interfaces:**
- Consumes: existing `VncViewModel` state/commands.
- Produces: fullscreen-by-default VNC screen, floating auto-hide controls, persistent reopen handle, transient status messages.

- [ ] Write a static regression check that expects fullscreen default, overlay controls, a 3500 ms hide delay, and a persistent controls handle.
- [ ] Run the check and confirm RED against FIX 9.
- [ ] Refactor the top-level permanent `Column` toolbar into a full-screen `Box` with `AndroidView` as the base layer.
- [ ] Add auto-hide expanded controls and a compact persistent handle.
- [ ] Preserve all existing actions: back, modifiers, Tab/Esc, input mode, scale mode, fullscreen toggle, keyboard, clipboard, screenshot, disconnect.
- [ ] Add automatic expiry for status messages.
- [ ] Run the static regression check and confirm GREEN.

### Task 3: Fill-screen viewport and gesture tolerance

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/domain/VncTypes.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`
- Modify: `scripts/tests/check-vnc-polish-source.sh`

**Interfaces:**
- Produces: `VncScaleMode.FILL_SCREEN`; `VncSurfaceView` uses `TrackpadGestureInterpreter`; tap threshold uses `ViewConfiguration.scaledTouchSlop`.

- [ ] Extend the static regression check to require `FILL_SCREEN`, max-ratio center crop, `TrackpadGestureInterpreter` usage, and touch-slop movement classification.
- [ ] Run the check and confirm RED.
- [ ] Add `FILL_SCREEN` and center-crop destination rectangle using `maxOf(vw / rw, vh / rh)`.
- [ ] Replace fixed 1.25x pointer math with `TrackpadGestureInterpreter.move`.
- [ ] Replace the 2-pixel gesture threshold with Android touch slop and reduce the scroll step threshold to 40 px.
- [ ] Expose `Isi Layar` in the scaling cycle.
- [ ] Run the regression checks and confirm GREEN.

### Task 4: Regression verification and patch packaging

**Files:**
- Verify: all `scripts/tests/check-*.sh`
- Create: FIX 10 patch and full source backup outside the repository tree.

**Interfaces:**
- Produces: patch applicable cleanly to FIX 9 and a full backup source ZIP.

- [ ] Run all source/static regression scripts.
- [ ] Run `git diff --no-index --check` equivalent checks for whitespace and generate a clean FIX 9 -> FIX 10 patch.
- [ ] Dry-run the patch against a fresh FIX 9 extraction.
- [ ] Package FIX 10 source ZIP.
- [ ] Report that local static/pure checks passed and GitHub Actions is still required to prove the Android build.
