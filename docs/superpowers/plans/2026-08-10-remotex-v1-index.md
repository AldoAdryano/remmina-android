# RemoteX Android V1 Implementation Plan Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement these plans task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build RemoteX Android V1 as a public MIT-licensed, mobile-first Android remote administration client with VNC, SSH, and SFTP, built primarily through GitHub Actions.

**Architecture:** The project is a multi-module Native Android application. Protocol implementations are isolated behind interfaces so VNC, SSH, and SFTP can evolve independently from Compose UI and storage. The plans are deliberately split so each stage produces a buildable, testable artifact.

**Tech Stack:** Kotlin, Jetpack Compose, AGP 9.3.x, Gradle 9.5.x, JDK 17, NDK 28.2.13676358 for the terminal JNI layer, Room 2.8.4 + KSP2, Android Keystore, DataStore 1.2.1, Apache MINA SSHD 2.19.0, ConnectBot termlib 0.1.0 source-vendored under Apache-2.0, Vernacular VNC compatibility spike, JobScheduler UIDT on Android 14+ with WorkManager fallback on Android 8–13, GitHub Actions.

## Global Constraints

- Application ID: `com.remotex.android`.
- App name: `RemoteX`.
- License: MIT for RemoteX-owned source.
- Repository: public.
- Third-party source keeps its original license and notices.
- `minSdk = 26`.
- `compileSdk = 36`.
- `targetSdk = 36`.
- Primary target device: POCO X7 Pro running Android 16.
- UI: Jetpack Compose.
- Default UI language: Indonesian; public repository README: English.
- Theme default: follow system.
- Sensitive credentials must never be stored in plaintext.
- Android Keystore must protect encryption keys.
- No personal Jetson IP, username, password, or private key may be hard-coded.
- VNC default input mode: Trackpad.
- VNC right-click: two-finger tap.
- VNC default scaling: Fit Screen.
- VNC automatic reconnect: maximum three retries.
- VNC session screenshot is included in V1 and must save through Android-supported media/storage APIs without broad storage permission.
- Active remote sessions should keep the display awake and, where appropriate, hold a scoped Wi-Fi lock that is always released at session end.
- SSH must verify host fingerprints and must not silently accept changed host keys.
- Exported profiles exclude credentials and private keys by default.
- Local logs auto-delete after seven days and must never record secrets or clipboard contents.
- User-started SFTP transfers that need to continue in background use User-Initiated Data Transfer jobs on API 34+; API 26–33 use a WorkManager/foreground-compatible fallback.
- No analytics, telemetry, ads, cloud account, Google Drive backup, RDP, SPICE, X2Go, screen recording, LAN discovery, or SSH tunnel in V1.
- Normal builds must be possible without Android Studio on the user's laptop.
- GitHub Actions produces debug APKs; tag `v*` produces a signed release when signing secrets are configured.
- Android-dependent Room, Keystore, and Compose instrumentation tests run on a Gradle-managed API 36 emulator in GitHub Actions, so the user's laptop does not need to run an Android emulator.

---

## Execution Order

1. [Foundation and Connection Management](2026-08-10-remotex-foundation.md)
2. [VNC Remote Desktop](2026-08-10-remotex-vnc.md)
3. [SSH Terminal and SFTP](2026-08-10-remotex-ssh-sftp.md)
4. [Integration, Device Acceptance, and GitHub Release](2026-08-10-remotex-integration-release.md)

Do not start Plan 2 before Plan 1 tests and debug APK build pass.  
Do not start Plan 3 before Plan 1 passes. Plan 2 and Plan 3 may be implemented independently after the shared foundation exists.  
Plan 4 begins only after both protocol plans pass their module tests.

## Dependency Baseline

Use stable versions unless a plan explicitly pins a source tag:

```toml
[versions]
agp = "9.3.0"
ksp = "2.3.9"
composeBom = "2026.06.00"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
navigation = "2.9.8"
room = "2.8.4"
datastore = "1.2.1"
work = "2.11.2"
biometric = "1.1.0"
coreKtx = "1.19.0"
minaSshd = "2.19.0"
bouncycastle = "1.84"
vernacular = "1.16"
```

Build baseline:

```text
AGP 9.3.0
Gradle 9.5.0
JDK 17
Android Build Tools 36.0.0
Android NDK 28.2.13676358
```

AGP 9.x built-in Kotlin is used. Do not apply `org.jetbrains.kotlin.android`.

## Reviewer Gates

At the end of every task:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Run module-specific tests shown in each task as well.

At the end of every plan:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

A plan is not complete until all commands exit with code 0.

## Commit Policy

- One independently reviewable commit per task.
- Do not mix unrelated refactors.
- Never commit a keystore, private key, credential database, local connection profile, or `.env`.
- Use commit prefixes: `build:`, `feat:`, `fix:`, `test:`, `docs:`, `security:`.

## Plan Completion Definition

RemoteX V1 is complete only after Plan 4 verifies:

```text
APK cloud build ✓
Install on POCO X7 Pro Android 16 ✓
Profile CRUD ✓
Encrypted credentials ✓
VNC render + trackpad + two-finger right-click ✓
SSH terminal + host-key verification ✓
SFTP list/upload/download ✓
Background transfer notification ✓
No secrets in logs ✓
Signed GitHub Release APK ✓
```
