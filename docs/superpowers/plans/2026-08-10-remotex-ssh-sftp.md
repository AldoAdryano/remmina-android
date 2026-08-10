# RemoteX SSH and SFTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure SSH terminal and SFTP file management to RemoteX, sharing one SSH transport layer while keeping terminal and file-transfer presentation independent.

**Architecture:** `feature:ssh` owns the Apache MINA SSHD client adapter, host-key verification, authentication, PTY shell, and session lifecycle. `feature:sftp` consumes an authenticated SSH session through a narrow interface. ConnectBot termlib 0.1.0 is source-vendored at its exact tag for terminal rendering; Apache MINA remains the SSH transport selected in the approved design.

**Tech Stack:** Apache MINA SSHD 2.19.0, ConnectBot termlib 0.1.0 (Apache-2.0, libvterm MIT), NDK 28.2.13676358 for JNI/libvterm, Compose, Android Storage Access Framework, JobScheduler User-Initiated Data Transfer jobs on API 34+, WorkManager/foreground-compatible fallback on API 26–33.

## Global Constraints

- Password, private key, and passphrase auth supported.
- Unknown host key requires Reject/Trust.
- Changed trusted host key must block connection by default.
- No host-key auto-accept.
- No secret logging.
- SSH and SFTP default port 22.
- Portrait and landscape supported.
- SFTP landscape is dual-pane; portrait switches Local/Remote.
- Default download directory: `Downloads/RemoteX/`.
- Background transfer must show Android notification.
- No SSH tunneling in V1.

---

## File Map

```text
feature/ssh/
├── domain/
│   ├── SshConnectionSpec.kt
│   ├── SshAuth.kt
│   ├── HostKeyDecision.kt
│   ├── SshSession.kt
│   ├── SshSessionState.kt
│   ├── SftpTransport.kt
│   └── SftpTransportEntry.kt
├── engine/
│   ├── SshEngine.kt
│   ├── MinaSshEngine.kt
│   ├── MinaHostKeyVerifier.kt
│   └── PrivateKeyLoader.kt
├── terminal/
│   ├── TerminalSessionController.kt
│   ├── TerminalScreen.kt
│   ├── TerminalToolbar.kt
│   └── SpecialKeyEncoder.kt
└── knownhosts/
    ├── KnownHostRepository.kt
    └── RoomKnownHostRepository.kt

feature/sftp/
├── domain/
│   ├── RemoteFile.kt
│   ├── TransferJob.kt
│   └── TransferState.kt
├── engine/
│   ├── SftpClient.kt
│   └── MinaSftpClient.kt
├── presentation/
│   ├── SftpViewModel.kt
│   ├── SftpScreen.kt
│   ├── LocalFilePane.kt
│   └── RemoteFilePane.kt
└── transfer/
    ├── TransferCoordinator.kt
    ├── TransferWorker.kt
    └── TransferNotification.kt

third_party/termlib/
└── source imported from tag 0.1.0 / commit e3f4bdc

Native ABI target for terminal JNI:
- arm64-v8a (required for POCO X7 Pro)
- x86_64 (required for CI/emulator testing)
- armeabi-v7a when upstream libvterm build remains compatible; release APK remains a single multi-ABI APK
```

### Task 1: Add SSH transport contracts and Apache MINA dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/ssh/build.gradle.kts`
- Create SSH domain and engine interface files.
- Test: `SshEngineContractTest.kt`

**Interfaces:**
- Produces:

```kotlin
sealed interface SshAuth {
    data class Password(val password: CharArray) : SshAuth
    data class PrivateKey(
        val keyBytes: ByteArray,
        val passphrase: CharArray?,
    ) : SshAuth
}

data class SshConnectionSpec(
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
)

sealed interface SshSessionState {
    data object Idle : SshSessionState
    data object Connecting : SshSessionState
    data object VerifyingHost : SshSessionState
    data object Authenticating : SshSessionState
    data object Connected : SshSessionState
    data class Failed(val reason: String) : SshSessionState
    data object Closed : SshSessionState
}

interface ShellChannel {
    val stdout: Flow<ByteArray>
    val stderr: Flow<ByteArray>
    suspend fun write(bytes: ByteArray)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun close()
}

interface SshEngine {
    val state: StateFlow<SshSessionState>
    suspend fun connect(spec: SshConnectionSpec): SshSessionHandle
    suspend fun disconnect()
}

data class SftpTransportEntry(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAtEpochMillis: Long?,
)

interface SftpTransport {
    suspend fun list(path: String): List<SftpTransportEntry>
    suspend fun exists(path: String): Boolean
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun removeFile(path: String)
    suspend fun removeDirectory(path: String)
    suspend fun openRead(path: String): InputStream
    suspend fun openWrite(path: String, truncate: Boolean): OutputStream
    suspend fun serverSideCopy(from: String, to: String): Boolean
    suspend fun close()
}

interface SshSessionHandle {
    suspend fun openShell(term: String, columns: Int, rows: Int): ShellChannel
    suspend fun openSftpTransport(): SftpTransport
}
```

- [ ] **Step 1: Add Apache MINA artifacts**

```toml
[versions]
mina-sshd = "2.19.0"

[libraries]
mina-sshd-core = { module = "org.apache.sshd:sshd-core", version.ref = "mina-sshd" }
mina-sshd-sftp = { module = "org.apache.sshd:sshd-sftp", version.ref = "mina-sshd" }
```

- [ ] **Step 2: Write fake transport contract tests**

Verify a presentation layer can open a shell and write bytes using only `SshEngine`, `SshSessionHandle`, and `ShellChannel`.

- [ ] **Step 3: Inspect runtime dependencies**

Run:

```bash
./gradlew :feature:ssh:dependencies --configuration debugRuntimeClasspath
```

Confirm no GPL runtime component is introduced.

- [ ] **Step 4: Run tests and commit**

```bash
./gradlew :feature:ssh:testDebugUnitTest :feature:ssh:lintDebug
git add feature/ssh gradle/libs.versions.toml
git commit -m "feat: define SSH transport boundary"
```

### Task 2: Implement known-host verification and authentication

**Files:**
- Add `KnownHostEntity`, `KnownHostDao` to `core:database`.
- Create `KnownHostRepository.kt`.
- Create `MinaHostKeyVerifier.kt`.
- Create `PrivateKeyLoader.kt`.
- Test: host-key decision tests and private-key parsing tests.

**Interfaces:**
- Produces:

```kotlin
data class KnownHost(
    val host: String,
    val port: Int,
    val algorithm: String,
    val sha256Fingerprint: String,
)

sealed interface HostKeyDecision {
    data class Unknown(val candidate: KnownHost) : HostKeyDecision
    data object Trusted : HostKeyDecision
    data class Changed(val old: KnownHost, val candidate: KnownHost) : HostKeyDecision
}
```

- [ ] **Step 1: Write host-key tests**

```kotlin
@Test
fun unknownHost_requiresUserDecision() = runTest {
    val result = verifier.check("server.local", 22, "ssh-ed25519", candidateKey)
    assertTrue(result is HostKeyDecision.Unknown)
}

@Test
fun changedKey_isBlocked() = runTest {
    repository.save(oldHost)
    val result = verifier.check(oldHost.host, oldHost.port, "ssh-ed25519", differentKey)
    assertTrue(result is HostKeyDecision.Changed)
}
```

- [ ] **Step 2: Normalize fingerprints**

Display OpenSSH-style SHA-256 fingerprint:

```text
SHA256:<base64-without-padding>
```

Use SHA-256 over the raw host public key blob.

- [ ] **Step 3: Connect Apache MINA host-key verification to UI decision**

Unknown:
```text
engine state -> VerifyingHost
UI -> Reject or Trust
Trust -> store KnownHost -> continue
Reject -> abort connection
```

Changed:
```text
show old + new fingerprint
default action = Cancel
replacement requires explicit "Replace trusted key"
```

- [ ] **Step 4: Implement password auth**

Do not store the password in `SshSessionState`. Feed it directly to the MINA session authentication request and zero RemoteX-owned temporary arrays afterward.

- [ ] **Step 5: Implement private-key auth**

Import keys through Android document picker. Store encrypted key bytes through `CredentialStore`. Support passphrase policy:
- Save Securely
- Always Ask

Reject unreadable/unsupported keys with a user-readable error without logging key content.

- [ ] **Step 6: Run tests and commit**

```bash
./gradlew :core:database:testDebugUnitTest :feature:ssh:testDebugUnitTest
git add core/database feature/ssh
git commit -m "security: verify SSH hosts and authenticate securely"
```

### Task 3: Implement interactive PTY shell controller

**Files:**
- Create: `MinaSshEngine.kt`
- Create: `TerminalSessionController.kt`
- Create: `SpecialKeyEncoder.kt`
- Test: controller and special-key tests.

**Interfaces:**
- Consumes `ShellChannel`.
- Produces:
  - `connect()`
  - `sendText(String)`
  - `sendSpecialKey(SpecialKey)`
  - `resize(columns, rows)`
  - `disconnect()`

- [ ] **Step 1: Write special-key tests**

Required bytes:

```text
Enter      \r
Tab        \t
Esc        0x1B
Ctrl+C     0x03
Ctrl+D     0x04
Ctrl+Z     0x1A
Arrow Up   ESC [ A
Arrow Down ESC [ B
Arrow Right ESC [ C
Arrow Left  ESC [ D
```

- [ ] **Step 2: Open PTY**

Request:

```text
TERM=xterm-256color
initial columns=80
initial rows=24
```

Resize PTY whenever terminal measured cell dimensions change.

- [ ] **Step 3: Stream I/O with structured concurrency**

Use one job for stdout and one for stderr. Both write into the terminal emulator input sink. Cancellation of the terminal screen closes both jobs and the shell channel.

- [ ] **Step 4: Keep-alive**

Use MINA's client/session keep-alive support with a conservative interval of 30 seconds while the session is connected. Stop keep-alive immediately on disconnect.

- [ ] **Step 5: Command history**

Store only commands the user submits through the optional input/history layer, maximum 100 items. Do not infer or record password prompts. Provide a setting to clear history. Never include history in diagnostics export.

- [ ] **Step 6: Run tests and commit**

```bash
./gradlew :feature:ssh:testDebugUnitTest
git add feature/ssh
git commit -m "feat: implement interactive SSH PTY session"
```

### Task 4: Vendor and integrate ConnectBot termlib 0.1.0

**Files:**
- Create: `third_party/termlib/` from tag `0.1.0` (`e3f4bdc`).
- Modify: `settings.gradle.kts`
- Modify: `THIRD_PARTY_LICENSES.md`
- Create: `TerminalScreen.kt`
- Create: `TerminalToolbar.kt`
- Test: terminal Compose smoke test.

**Interfaces:**
- Consumes terminal byte stream from `TerminalSessionController`.
- Produces terminal resize callback and key/text callbacks.

- [ ] **Step 1: Import the exact upstream source tag**

Use:

```bash
git subtree add \
  --prefix=third_party/termlib \
  https://github.com/connectbot/termlib.git \
  0.1.0 \
  --squash
```

Immediately verify:

```bash
git -C third_party/termlib rev-parse --is-inside-work-tree || true
grep -n "Apache License" third_party/termlib/LICENSE
```

The subtree import becomes pinned by the RemoteX commit.

- [ ] **Step 2: Preserve license notices**

Add to `THIRD_PARTY_LICENSES.md`:

```text
ConnectBot Terminal (termlib) 0.1.0
Copyright its respective authors
License: Apache License 2.0
Uses libvterm under the MIT license
Upstream: https://github.com/connectbot/termlib
```

Do not relabel third-party source as MIT.

- [ ] **Step 3: Integrate only the terminal library module and pin the Android NDK**

Do not import ConnectBot's SSH implementation. Apache MINA remains the transport.

Pin `ndkVersion = "28.2.13676358"` in the Android module that builds the termlib JNI layer. Configure native ABI packaging so `arm64-v8a` is always present and `x86_64` is available for emulator/CI testing. Verify the resulting APK contains the expected `lib/<abi>/` native library entries.

- [ ] **Step 4: Wire terminal bytes**

```text
SSH stdout/stderr -> termlib terminal input
termlib text/key callback -> TerminalSessionController -> SSH stdin
terminal geometry -> PTY resize
```

Toolbar:

```text
Ctrl Alt Esc Tab
Arrows
Keyboard
Copy
Paste
Font -
Font +
Reconnect
Disconnect
```

Because upstream termlib documents paste as planned rather than current, implement RemoteX Paste as:
`Android Clipboard -> controller.sendText(text)` rather than depending on a termlib paste API.

- [ ] **Step 5: Add terminal UI test**

Verify:
- terminal container exists;
- toolbar shows Ctrl/Alt/Esc/Tab;
- reconnect and disconnect actions exist;
- portrait and landscape layouts do not overlap.

- [ ] **Step 6: Run and commit**

```bash
./gradlew :feature:ssh:testDebugUnitTest :feature:ssh:lintDebug :app:assembleDebug
git add third_party/termlib feature/ssh settings.gradle.kts THIRD_PARTY_LICENSES.md
git commit -m "feat: integrate terminal emulator UI"
```

### Task 5: Implement SFTP client and file operations

**Files:**
- Create `feature/sftp/domain/*`
- Create `MinaSftpClient.kt`
- Create tests using a fake SFTP backend.

**Interfaces:**
- Consumes: `SshSessionHandle.openSftpTransport(): SftpTransport` from `feature:ssh`; `feature:ssh` must never depend on `feature:sftp`, preventing a circular Gradle dependency.
- Produces:

```kotlin
data class RemoteFile(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAtEpochMillis: Long?,
)

interface SftpClient {
    suspend fun list(path: String): List<RemoteFile>
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun move(from: String, to: String)
    suspend fun copy(from: String, to: String, onProgress: (Long, Long?) -> Unit)
    suspend fun delete(path: String, recursive: Boolean = false)
    suspend fun download(remotePath: String, output: OutputStream, onProgress: (Long, Long?) -> Unit)
    suspend fun upload(input: InputStream, remotePath: String, size: Long?, onProgress: (Long, Long?) -> Unit)
}
```

- [ ] **Step 1: Write directory listing test**

Ensure `.` and `..` are filtered from UI results and directories sort before files, each group alphabetically.

- [ ] **Step 2: Write overwrite safety test**

If destination exists, engine must return a conflict result rather than overwrite immediately:

```kotlin
sealed interface TransferPreparation {
    data object Ready : TransferPreparation
    data class Conflict(val destination: String) : TransferPreparation
}
```

UI choices:
- Replace
- Rename destination
- Cancel

- [ ] **Step 3: Implement remote copy and move semantics**

`move(from,to)` uses `SftpTransport.rename` when source and destination are on the same server/filesystem. `copy(from,to)` must not load the whole file into memory: first call `SftpTransport.serverSideCopy`; if it returns `false`, stream `openRead(from)` into `openWrite(to, truncate = true)` through fixed-size buffers. For directory copy, walk entries recursively with explicit cancellation checks and progress aggregation.

- [ ] **Step 4: Implement delete rules**

Single file delete: confirmation required.  
Directory delete:
- empty directory can be deleted;
- non-empty recursive delete requires a second explicit confirmation containing directory name.

- [ ] **Step 5: Implement upload/download stream transfer**

Do not read whole files into memory. Use fixed-size buffers and progress callbacks.

- [ ] **Step 6: Run tests and commit**

```bash
./gradlew :feature:sftp:testDebugUnitTest
git add feature/sftp
git commit -m "feat: add SFTP file operations"
```

### Task 6: Build mobile SFTP UI and Android-16-safe background transfer coordinator

**Files:**
- Create: `SftpViewModel.kt`
- Create: `SftpScreen.kt`
- Create: `LocalFilePane.kt`
- Create: `RemoteFilePane.kt`
- Create: `TransferCoordinator.kt`
- Create: `TransferWorker.kt` for API 26–33 fallback
- Create: `UserInitiatedTransferJobService.kt` for API 34+
- Create: `TransferNotification.kt`
- Modify: Android manifest notification/foreground service declarations only where required by selected Android execution API.
- Test: transfer state tests.

**Interfaces:**
- Produces:

```kotlin
sealed interface TransferState {
    data object Queued : TransferState
    data class Running(val bytes: Long, val total: Long?, val bytesPerSecond: Long) : TransferState
    data object Completed : TransferState
    data class Failed(val message: String) : TransferState
    data object Cancelled : TransferState
}
```

- [ ] **Step 1: Implement portrait/landscape layout**

Portrait:
```text
[ Local | Remote ]
single pane
```

Landscape:
```text
Local Android | Remote Server
```

- [ ] **Step 2: Use Android storage APIs**

Default download target is `Downloads/RemoteX/`.

On modern Android:
- use MediaStore or Storage Access Framework for shared Downloads;
- use `ContentResolver` streams;
- do not rely on unrestricted raw `/sdcard` paths;
- do not request broad storage permission merely to implement SFTP.

- [ ] **Step 3: Implement Android-version-aware transfer execution**

For transfers explicitly started by the user:

```text
API 34+  -> JobScheduler JobInfo.setUserInitiated(true) + RUN_USER_INITIATED_JOBS
API 26-33 -> WorkManager long-running/foreground-compatible worker
```

UIDT jobs must be scheduled while the app is visible, provide an ongoing notification, execute I/O off the main thread, and call `jobFinished()` on completion/failure. This avoids relying exclusively on long-running WorkManager jobs on Android 16, where those jobs can consume JobScheduler quota.

- [ ] **Step 4: Implement transfer queue**

One connection may run at most two concurrent transfers. Additional jobs remain `Queued`. Cancellation closes the active stream promptly.

- [ ] **Step 5: Implement notifications and required permissions**

When a transfer continues in background, notification displays:
- filename;
- upload/download direction;
- progress if total known;
- cancel action.

Never put remote password, username, private path notes, or clipboard text in notification content.

Declare `android.permission.RUN_USER_INITIATED_JOBS` for API 34+ UIDT support and `android.permission.POST_NOTIFICATIONS` for API 33+. Request notification permission at runtime before enabling background transfer notifications; if denied, explain the limitation and keep the transfer in a foreground-only UI path rather than silently claiming background notification support.

- [ ] **Step 6: Add retry behavior**

Failed transfers may be retried manually. Retry restarts the individual transfer from the beginning in V1; resumable transfer is not claimed.

- [ ] **Step 7: Run tests and commit**

```bash
./gradlew :feature:sftp:testDebugUnitTest :feature:sftp:lintDebug :app:assembleDebug
git add feature/sftp app
git commit -m "feat: add mobile SFTP browser and transfer manager"
```

### Task 7: Integrate SSH/SFTP with profiles and credentials

**Files:**
- Modify Home navigation.
- Modify `AppContainer.kt`.
- Modify Connection Editor private-key picker UI.
- Test credential-resolution flows.

- [ ] **Step 1: Implement password resolution**

```text
ALWAYS_ASK -> prompt -> connect -> clear transient password
SAVE_SECURELY -> CredentialStore.read -> connect -> clear returned secret buffer
```

- [ ] **Step 2: Implement key resolution**

```text
private key credential -> decrypt bytes
passphrase policy -> decrypt or prompt
authenticate
zero RemoteX-owned key/passphrase buffers after session setup where library semantics allow
```

- [ ] **Step 3: Update profile actions**

`Terminal` and `Files` appear only when `sshEnabled == true`.

- [ ] **Step 4: Mark successful connections recent**

After SSH authentication succeeds, update `lastConnectedAtEpochMillis`.

Do not mark a failed auth attempt as a successful recent connection.

- [ ] **Step 5: Run full SSH/SFTP verification**

```bash
./gradlew :feature:ssh:testDebugUnitTest
./gradlew :feature:sftp:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add app feature/ssh feature/sftp feature/connections
git commit -m "feat: integrate SSH and SFTP profile flows"
```

## SSH/SFTP Device Acceptance Gate

Against an SSH/SFTP server you control:

```text
Unknown fingerprint prompt ✓
Trust persists ✓
Changed key blocks ✓
Password auth ✓
Private-key auth ✓
Passphrase auth ✓
Interactive shell ✓
Ctrl/Alt/Esc/Tab ✓
Arrow keys ✓
Copy/paste ✓
PTY resize ✓
Portrait/landscape ✓
SFTP directory list ✓
Upload ✓
Download ✓
Rename/move/delete/new folder ✓
Overwrite confirmation ✓
Background notification ✓
Cancel transfer ✓
Retry transfer ✓
No secrets in logs ✓
```
