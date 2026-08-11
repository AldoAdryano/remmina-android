# VNC Soft Keyboard Enter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Enter from Android software keyboards reliably send X11 Return in VNC sessions.

**Architecture:** Keep the existing hidden Compose text input and RFB key path. Normalize every software-keyboard representation of Enter at the Compose boundary before it reaches `VncInputEvent.Key`.

**Tech Stack:** Kotlin, Jetpack Compose, Android IME APIs, RFB/X11 keysyms.

## Global Constraints
- Do not modify SSH, SFTP, audio, pointer, scroll, orientation, or adaptive display quality behavior.
- X11 Return keysym is `0xFF0D`.
- Keep GitHub debug and release workflows protected by the regression check.

---

### Task 1: Reproduce and guard the missing software-keyboard Enter path

**Files:**
- Create: `scripts/tests/check-vnc-soft-keyboard-enter.sh`

- [ ] Add assertions for Return constant, newline normalization, IME actions, and the toolbar Enter fallback.
- [ ] Run the check against FIX 14 and verify it fails.

### Task 2: Normalize Enter at the VNC input boundary

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/input/KeySymMapper.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`

- [ ] Reuse one Return keysym constant for native Android Enter.
- [ ] Translate inserted newline/carriage-return characters to Return.
- [ ] Handle Done, Go, Next, Search, and Send IME actions as Return.
- [ ] Add toolbar `Enter` using the existing `dispatchKey` function.
- [ ] Run the regression check and verify it passes.

### Task 3: Protect CI and regressions

**Files:**
- Modify: `.github/workflows/build-debug.yml`
- Modify: `.github/workflows/release.yml`

- [ ] Run the new check in both workflows.
- [ ] Run VNC quality, performance, input-stability, audio, polish, and SFTP source checks.
- [ ] Let GitHub Actions prove `testDebugUnitTest`, `lintDebug`, and APK assembly.
