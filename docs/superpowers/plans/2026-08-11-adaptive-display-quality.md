# Adaptive Display Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add runtime VNC quality profiles, safe adaptive switching, and an FPS/mode overlay while retaining FIX 13 performance improvements.

**Architecture:** Quality selection is domain state; a pure controller decides Auto transitions; the RFB engine applies profile changes only at framebuffer boundaries and publishes stats; ViewModel/Compose only expose and render that state.

**Tech Stack:** Kotlin, coroutines/StateFlow, Jetpack Compose Material3, custom RFB engine.

## Global Constraints
- Default quality: BALANCED.
- PERFORMANCE uses RGB565 + Hextile-first.
- BALANCED uses 32-bit true color + Hextile-first.
- HIGH uses 32-bit true color + RAW-first.
- AUTO starts BALANCED and only adapts between BALANCED/PERFORMANCE.
- Pixel-format changes happen only between framebuffer updates.
- Existing VNC input/audio/SSH/SFTP/signing behavior remains unchanged.

---

### Task 1: Quality domain and adaptive controller
**Files:**
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/VncQuality.kt`
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/quality/AdaptiveQualityController.kt`
- Create: `scripts/tests/check-vnc-quality-pure.sh`

- [ ] Write pure regression harness for profile mapping and Auto hysteresis.
- [ ] Run it and observe RED because quality classes do not exist.
- [ ] Implement enums/profile mapping/controller.
- [ ] Run harness and observe GREEN.

### Task 2: Safe runtime RFB quality switching and FPS stats
**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/VncEngine.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncViewModel.kt`
- Create: `scripts/tests/check-vnc-quality-source.sh`

- [ ] Add selected/effective quality and performance stats flows.
- [ ] Queue requested quality and apply only after current framebuffer update.
- [ ] Recreate Hextile decoder when pixel format changes.
- [ ] Send profile-specific encoding order and force a full refresh.
- [ ] Measure changed-frame FPS in one-second windows and feed Auto controller.

### Task 3: Compose quality menu and indicator
**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`

- [ ] Add Material3 dropdown quality menu.
- [ ] Default label to Seimbang and expose all four modes.
- [ ] Add compact FPS/mode indicator while connected.
- [ ] Keep toolbar auto-hide behavior unchanged.

### Task 4: Regression verification
**Files:**
- Modify: `.github/workflows/build-debug.yml`
- Use existing regression scripts.

- [ ] Run quality pure/source checks.
- [ ] Run FIX 11/12/13 source regressions.
- [ ] Run `git diff --check` equivalent against base.
- [ ] Add FIX14 checks to GitHub Actions before Android unit/lint/build steps.
