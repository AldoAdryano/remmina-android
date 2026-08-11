# RemoteX FIX 13 VNC Performance & Audio Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise perceived VNC frame rate and reduce audio latency without changing RemoteX connection semantics.

**Architecture:** Add Hextile + RGB565 at the RFB transport layer, retain safe full frame snapshots, reuse one Android bitmap for dirty-region updates, and tune the SSH PCM playback path for low latency. Existing Raw/CopyRect, VNC input, SSH/SFTP and signing remain fallback/stable paths.

**Tech Stack:** Kotlin/JVM, Android View/Bitmap, Java DataInputStream, RFB/RFC 6143, Android AudioTrack, Apache MINA SSHD, coroutines.

## Global Constraints
- compileSdk 36, targetSdk 36, minSdk 26.
- No new Android permissions.
- Preserve Raw and CopyRect fallback.
- Preserve FIX 11 input-stability behavior.
- Preserve FIX 12 PCM 48 kHz stereo SSH audio transport.

---

### Task 1: RFB performance transport
**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/RfbPixelFormat.kt`
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/HextileDecoder.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt`
- Test: `scripts/tests/check-vnc-performance.sh`

- [ ] Write failing pure tests for RGB565 decode and Hextile background/subrect decoding.
- [ ] Run the check and observe RED because Hextile/performance format is absent.
- [ ] Implement RGB565 and Hextile, preferring Hextile in SetEncodings.
- [ ] Run the pure test and existing VNC source checks.

### Task 2: Reusable bitmap rendering
**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncSurfaceView.kt`

- [ ] Add source regression assertions requiring reusable bitmap updates while retaining safe full snapshots.
- [ ] Observe RED before implementation.
- [ ] Keep safe full snapshots, reuse one Surface bitmap, and update only the server-reported dirty rectangle.
- [ ] Verify screenshot behavior remains compatible with full-frame snapshots.

### Task 3: Low-latency audio
**Files:**
- Modify: `feature/audio/src/main/kotlin/com/remotex/feature/audio/AndroidPcmPlayer.kt`
- Modify: `feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaExecChannel.kt`
- Modify: `scripts/tests/check-remote-audio-source.sh`

- [ ] Add failing assertions for 60 ms target, low-latency performance mode, and 4 KiB exec chunks.
- [ ] Observe RED.
- [ ] Implement the smallest audio/SSH changes.
- [ ] Run audio and SSH regressions.

### Task 4: CI and regression verification
**Files:**
- Modify: `.github/workflows/build-debug.yml`

- [ ] Add FIX 13 performance source check to debug CI.
- [ ] Run `git diff --check` and all available source/pure regression scripts.
- [ ] Produce patch against FIX 12; GitHub Actions remains the authoritative Android compile/lint/APK verification.
