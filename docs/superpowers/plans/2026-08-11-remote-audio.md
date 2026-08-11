# Remote Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stream Linux system audio to Android while a VNC session is open, using a separate SSH PCM side-channel.

**Architecture:** Extend the existing SSH transport with a non-interactive exec stream, add a dedicated audio feature module that converts the remote PCM stream into Android `AudioTrack`, and expose audio as an optional toggle in the VNC screen. Audio owns its own SSH engine/session and cannot terminate the RFB session.

**Tech Stack:** Kotlin, Android AudioTrack, Kotlin coroutines/Flow, Apache MINA SSHD 2.19.0, Jetpack Compose.

## Global Constraints
- compileSdk 36, targetSdk 36, minSdk 26.
- Remote audio is output-only; no microphone permission.
- RFB engine behavior remains unchanged.
- Audio is opt-in per VNC session and stops with the VNC screen.
- Remote command must be fixed and free of user interpolation.
- Full Android compile/build verification is performed by GitHub Actions.

---

### Task 1: Add SSH exec streaming primitive

**Files:**
- Modify: `feature/ssh/src/main/kotlin/com/remotex/feature/ssh/domain/SshTypes.kt`
- Create: `feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaExecChannel.kt`
- Modify: `feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/MinaSshEngine.kt`

**Interfaces:**
- Produces `ExecChannel.stdout`, `ExecChannel.stderr`, `ExecChannel.close()`.
- Produces `SshSessionHandle.openExec(command: String): ExecChannel`.

- [ ] Write source regression test that fails while `openExec` is missing.
- [ ] Verify test fails.
- [ ] Add domain interface and MINA exec implementation with no PTY.
- [ ] Verify source regression passes.

### Task 2: Add remote audio engine and Android PCM playback

**Files:**
- Create: `feature/audio/build.gradle.kts`
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioTypes.kt`
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioCommand.kt`
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/AndroidPcmPlayer.kt`
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/SshPcmAudioEngine.kt`
- Create: `feature/audio/src/main/kotlin/com/remotex/feature/audio/RemoteAudioViewModel.kt`
- Create: `feature/audio/src/test/kotlin/com/remotex/feature/audio/RemoteAudioCommandTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- `RemoteAudioViewModel.start(SshConnectionSpec)` starts audio.
- `RemoteAudioViewModel.stop()` stops audio.
- `RemoteAudioState` drives VNC UI.

- [ ] Write failing pure test for fixed capture command and stderr classification.
- [ ] Verify red state with `kotlinc` pure test runner.
- [ ] Implement command model and classification.
- [ ] Implement `AudioTrack` streaming player.
- [ ] Implement SSH audio engine and ViewModel.
- [ ] Verify pure test passes.

### Task 3: Integrate audio into VNC route and toolbar

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/remotex/android/RemoteXApp.kt`
- Modify: `feature/vnc/src/main/kotlin/com/remotex/feature/vnc/presentation/VncScreen.kt`

**Interfaces:**
- `VncScreen(..., audioState, onAudioToggle)` remains presentation-only.
- `VncRoute` resolves credentials and owns audio lifecycle.

- [ ] Write source regression test requiring `Suara`, `Bisukan`, SSH-disabled guard, and audio disconnect on disposal.
- [ ] Verify test fails.
- [ ] Add module dependency and VNC route coordination.
- [ ] Add toolbar control and status handling.
- [ ] Verify test passes.

### Task 4: CI regression checks and patch verification

**Files:**
- Create: `scripts/tests/check-remote-audio-source.sh`
- Modify: `.github/workflows/build-debug.yml`
- Modify: `.github/workflows/release.yml`

- [ ] Add source checks before implementation and observe failure.
- [ ] Wire checks into CI.
- [ ] Run all existing FIX 11 source checks plus new audio checks.
- [ ] Run `git diff --check` equivalent on generated patch.
- [ ] Dry-run patch against pristine FIX 11 source.
