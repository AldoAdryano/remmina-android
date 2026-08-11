# Watch Mode + High Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add timestamped 720p30 A/V Watch Mode over SSH and harden VNC High mode against large Tight/JPEG allocation spikes.

**Architecture:** Keep VNC connected as the interactive control plane. Add an independent SSH/FFmpeg MPEG-TS media plane decoded by Media3 ExoPlayer, and reduce Tight JPEG per-frame allocations by reusing decode scratch buffers.

**Tech Stack:** Kotlin, Jetpack Compose, Apache MINA SSHD, AndroidX Media3 1.10.1, ExoPlayer, FFmpeg on remote Linux, RFB Tight.

## Global Constraints
- compileSdk 36, targetSdk 36, minSdk 26.
- No new inbound network port on the remote host.
- Reuse existing SSH authentication/known-host policy.
- Watch Mode failure must never disconnect the VNC session.
- Keep stable signing workflow unchanged.

---

### Task 1: Pure Watch Mode command and pipe tests
**Files:**
- Create `feature/watch/src/main/kotlin/com/remotex/feature/watch/RemoteWatchCommand.kt`
- Create `feature/watch/src/main/kotlin/com/remotex/feature/watch/RemoteWatchPipe.kt`
- Create tests under `feature/watch/src/test/...`

- [ ] Write tests that require FFmpeg prerequisite checks, x11grab, Pulse monitor, H.264, AAC, 30 FPS, max 1280 scaling, and MPEG-TS stdout.
- [ ] Verify tests fail before production files exist.
- [ ] Implement command builder and bounded pipe.
- [ ] Verify pure tests pass.

### Task 2: SSH + Media3 watch engine
**Files:**
- Create `feature/watch/build.gradle.kts`
- Create `RemoteWatchTypes.kt`, `SshPipeDataSource.kt`, `RemoteWatchController.kt`
- Modify `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] Add stable Media3 1.10.1 dependencies.
- [ ] Start FFmpeg over an SSH exec channel and feed stdout to a bounded DataSource.
- [ ] Configure ExoPlayer with 2500/5000 ms min/max buffer and 1800 ms playback start.
- [ ] Map SSH/FFmpeg/Media3 errors to user-facing states without touching VNC.

### Task 3: Compose integration
**Files:**
- Modify `RemoteXApp.kt`
- Modify `VncScreen.kt`
- Create `app/src/main/kotlin/com/remotex/android/WatchPlayerSurface.kt`

- [ ] Add `Menonton` toolbar action.
- [ ] Reuse saved/Always Ask SSH credentials.
- [ ] Stop PCM audio before Watch Mode starts.
- [ ] Overlay the video player while VNC stays connected.
- [ ] Add `Keluar Menonton` floating action and status text.

### Task 4: High mode decoder hardening
**Files:**
- Modify `TightDecoder.kt`
- Modify `VncQuality.kt`
- Modify `RfbVncEngine.kt`
- Add/modify tests.

- [ ] Write failing tests/source checks for quality cap and reusable buffers.
- [ ] Reuse JPEG compressed buffer, Bitmap when compatible, and ARGB scratch IntArray.
- [ ] Change High Tight JPEG quality from 9 to 8.
- [ ] Convert High decode allocation failure into a Balanced fallback event + recoverable reconnect.

### Task 5: Regression + CI checks
**Files:**
- Add `scripts/tests/check-watch-mode.sh`
- Add `scripts/tests/check-vnc-high-hardening.sh`
- Modify GitHub workflows to run both source checks.

- [ ] Run new pure/source checks.
- [ ] Run all existing VNC/audio/SSH regression checks.
- [ ] Run `git diff --check`.
- [ ] Produce patch relative to FIX16 and full-source backup.
