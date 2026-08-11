# RemoteX FIX 16 VNC Video & Audio Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Tight VNC transport, shorten the framebuffer request cycle, prebuffer SSH PCM audio for better A/V alignment, and include FIX 15 Enter handling.

**Architecture:** Extend the existing custom RFB engine rather than replacing it. A bounded `TightDecoder` owns Tight state/zlib streams and writes decoded ARGB into the existing framebuffer. Quality profiles supply Tight JPEG/compression hints. Audio gains a pure sync policy and delayed `AudioTrack` start.

**Tech Stack:** Kotlin, Android 26+, Jetpack Compose, custom RFB 3.x client, `java.util.zip.Inflater`, Android `BitmapFactory`, Apache MINA SSHD, Android `AudioTrack`.

## Global Constraints

- Apply directly on top of FIX 14; include all FIX 15 changes.
- Do not change application ID/signing behaviour.
- Do not link GPL LibVNCClient/LibVNCServer.
- Keep Raw/Hextile/CopyRect fallback compatibility.
- Preserve VNC auth, input, rotation, SSH and SFTP behaviour.
- Treat all RFB rectangle/compressed lengths as untrusted.

---

### Task 1: Tight/quality contract tests

**Files:**
- Modify: `feature/vnc/src/test/kotlin/com/remotex/feature/vnc/VncQualityTest.kt`
- Create: `feature/vnc/src/test/kotlin/com/remotex/feature/vnc/TightCodecTest.kt`

- [ ] Write tests requiring Tight-enabled quality profiles and safe compact-length/filter helpers.
- [ ] Run pure/source test harness and confirm RED because Tight classes/profile fields do not exist.
- [ ] Add the minimum quality/profile and pure codec primitives.
- [ ] Re-run and confirm GREEN.

### Task 2: Bounded Tight decoder

**Files:**
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/TightCodec.kt`
- Create: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/protocol/TightDecoder.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt`

- [ ] Add a source regression test that requires Tight advertisement/dispatch and security bounds; confirm RED.
- [ ] Implement fill/JPEG/copy/palette/gradient, compact lengths and four persistent Inflater streams.
- [ ] Advertise Tight + per-profile quality/compression pseudo-encodings before fallback encodings.
- [ ] Re-run source/pure checks and confirm GREEN.

### Task 3: Earlier framebuffer request

**Files:**
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/engine/RfbVncEngine.kt`

- [ ] Add regression assertion that next request occurs before `framebuffer.copyOf()`/frame emission; confirm RED.
- [ ] Move performance/quality application and next request ahead of the UI snapshot while keeping one request outstanding.
- [ ] Re-run the check and existing VNC tests.

### Task 4: Audio prebuffer policy

**Files:**
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/AudioSyncPolicy.kt`
- Modify: `feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioTypes.kt`
- Modify: `feature/audio/src/main/kotlin/com/remotex/feature/audio/AndroidPcmPlayer.kt`
- Modify: `feature/audio/src/main/kotlin/com/remotex/feature/audio/SshPcmAudioEngine.kt`
- Modify: `feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioViewModel.kt`
- Modify: `app/src/main/kotlin/com/remotex/android/RemoteXApp.kt`

- [ ] Add pure sync-policy/source tests; confirm RED.
- [ ] Pass selected initial delay from latest VNC FPS to audio engine.
- [ ] Fill AudioTrack before `play()` and expose delay in `Playing` state.
- [ ] Re-run audio and app source regressions.

### Task 5: Regression/CI packaging

**Files:**
- Create: `scripts/tests/check-vnc-tight-video.sh`
- Create: `scripts/tests/check-remote-audio-sync.sh`
- Modify: `.github/workflows/build-debug.yml`
- Modify: `.github/workflows/release.yml`

- [ ] Run all FIX 11–16 source checks and `git diff --check`.
- [ ] Verify patch applies cleanly to untouched FIX 14.
- [ ] Package combined FIX 16 patch and full-source backup.
- [ ] Leave full Android compilation to GitHub Actions and report that limitation explicitly.
