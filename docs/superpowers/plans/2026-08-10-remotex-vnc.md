# RemoteX VNC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a production-usable VNC remote desktop session to RemoteX with framebuffer rendering, Trackpad Mode, two-finger right-click, keyboard controls, scaling, clipboard, fullscreen, and bounded reconnect.

**Architecture:** `feature:vnc` exposes RemoteX-owned protocol-neutral interfaces. A Vernacular adapter is attempted behind a compatibility gate; no Vernacular type may leak into ViewModels or Compose. Rendering and gesture translation are separate from socket/session management.

**Tech Stack:** Kotlin, Compose Canvas/Android bitmap interop, coroutines/Flow, Vernacular `io.xpipe:vernacular:1.16` compatibility spike, Bouncy Castle 1.84 override where needed.

## Global Constraints

- Default VNC input mode: Trackpad.
- Two-finger tap: right click.
- One-finger tap: left click.
- One-finger move: pointer motion.
- Double tap: double click.
- Tap-hold-drag: drag.
- Two-finger move: scroll.
- Pinch: zoom.
- Three-finger tap: show keyboard.
- Default scaling: Fit Screen.
- Auto reconnect: maximum three retries.
- No infinite reconnect loop.
- No credential logging.
- VNC module must not import Room or Compose from its transport package.
- Do not integrate GPL VNC engines into the MIT APK.

---

## File Map

```text
feature/vnc/
└── src/
    ├── main/java/com/remotex/feature/vnc/
    │   ├── domain/
    │   │   ├── VncConnectionSpec.kt
    │   │   ├── VncSession.kt
    │   │   ├── VncSessionState.kt
    │   │   ├── VncFrame.kt
    │   │   └── VncInputEvent.kt
    │   ├── engine/
    │   │   ├── VncEngine.kt
    │   │   ├── VernacularVncEngine.kt
    │   │   └── VncEngineFactory.kt
    │   ├── input/
    │   │   ├── TrackpadGestureInterpreter.kt
    │   │   └── KeySymMapper.kt
    │   ├── presentation/
    │   │   ├── VncViewModel.kt
    │   │   ├── VncScreen.kt
    │   │   ├── VncCanvas.kt
    │   │   └── VncToolbar.kt
    │   ├── clipboard/
    │   │   └── VncClipboardBridge.kt
    │   └── screenshot/
    │       └── VncScreenshotSaver.kt
    └── test/
```

### Task 1: Establish VNC engine compatibility gate and interfaces

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/vnc/build.gradle.kts`
- Create domain and engine interface files above.
- Test: `feature/vnc/src/test/.../VncContractTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class VncConnectionSpec(
    val host: String,
    val port: Int,
    val password: CharArray?,
    val shared: Boolean = true,
)

sealed interface VncSessionState {
    data object Idle : VncSessionState
    data object Connecting : VncSessionState
    data class Connected(val width: Int, val height: Int) : VncSessionState
    data class Reconnecting(val attempt: Int) : VncSessionState
    data class Failed(val reason: String, val retryable: Boolean) : VncSessionState
    data object Closed : VncSessionState
}

data class VncFrame(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val dirtyLeft: Int,
    val dirtyTop: Int,
    val dirtyRight: Int,
    val dirtyBottom: Int,
)

sealed interface VncInputEvent {
    data class Pointer(val x: Int, val y: Int, val buttonsMask: Int) : VncInputEvent
    data class Scroll(val deltaX: Float, val deltaY: Float) : VncInputEvent
    data class Key(val keysym: Int, val down: Boolean) : VncInputEvent
    data class Clipboard(val text: String) : VncInputEvent
}

interface VncEngine {
    val state: StateFlow<VncSessionState>
    val frames: Flow<VncFrame>
    val remoteClipboard: Flow<String>
    suspend fun connect(spec: VncConnectionSpec)
    suspend fun send(event: VncInputEvent)
    suspend fun disconnect()
}
```

- [ ] **Step 1: Add VNC dependencies**

```toml
[versions]
vernacular = "1.16"
bouncycastle = "1.84"

[libraries]
vernacular = { module = "io.xpipe:vernacular", version.ref = "vernacular" }
bouncycastle = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }
```

Use dependency resolution so Bouncy Castle resolves to 1.84 rather than Vernacular's older transitive version.

- [ ] **Step 2: Write a contract compile test**

The test must instantiate only RemoteX-owned fake engine types, proving presentation code depends on interfaces rather than Vernacular.

```kotlin
@Test
fun vncEngine_contractAcceptsPointerAndFrameFlow() = runTest {
    val engine = FakeVncEngine()
    engine.connect(VncConnectionSpec("127.0.0.1", 5900, null))
    engine.send(VncInputEvent.Pointer(10, 20, buttonsMask = 1))
    assertEquals(1, engine.sentEvents.size)
}
```

- [ ] **Step 3: Compile the Android module with Vernacular present**

Run:

```bash
./gradlew :feature:vnc:dependencies --configuration debugRuntimeClasspath
./gradlew :feature:vnc:compileDebugKotlin
```

Acceptance gate:
- No `java.awt`, Swing, JavaFX, or unsupported JVM desktop class may be required by the code path used by `VernacularVncEngine`.
- No GPL dependency may appear in runtime classpath.
- `bcprov-jdk18on` must resolve to `1.84`.

If the compile gate fails specifically because the Vernacular core requires unsupported desktop APIs, stop VNC implementation at this task and replace the engine choice before continuing. Do not weaken licensing or Android compatibility rules to force the dependency through.

- [ ] **Step 4: Record runtime license inventory**

Add this section to `THIRD_PARTY_LICENSES.md` only after the dependency report confirms the runtime graph:

```text
Vernacular VNC — MIT
Bouncy Castle Java — MIT-style Bouncy Castle License
```

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :feature:vnc:testDebugUnitTest :feature:vnc:lintDebug
git add feature/vnc gradle/libs.versions.toml THIRD_PARTY_LICENSES.md
git commit -m "feat: define VNC engine boundary"
```

### Task 2: Implement session state, framebuffer updates, and bounded reconnect

**Files:**
- Create: `feature/vnc/.../engine/VernacularVncEngine.kt`
- Create: `feature/vnc/.../engine/ReconnectPolicy.kt`
- Test: `ReconnectPolicyTest.kt`
- Test: `VncEngineStateTest.kt`

**Interfaces:**
- Consumes `VncEngine`.
- Produces `ReconnectPolicy.nextDelay(attempt: Int): Duration?`.

- [ ] **Step 1: Write reconnect policy tests**

```kotlin
@Test fun retriesExactlyThreeTimes() {
    val policy = ReconnectPolicy()
    assertNotNull(policy.nextDelay(1))
    assertNotNull(policy.nextDelay(2))
    assertNotNull(policy.nextDelay(3))
    assertNull(policy.nextDelay(4))
}
```

Use delays:

```text
attempt 1 = 1 second
attempt 2 = 2 seconds
attempt 3 = 4 seconds
attempt 4 = stop
```

- [ ] **Step 2: Implement state transition tests**

Required legal path:

```text
Idle -> Connecting -> Connected -> Reconnecting(1..3) -> Connected
Idle -> Connecting -> Failed
Connected -> Closed
Reconnecting(3) -> Failed
```

Test that an explicit `disconnect()` always ends in `Closed` and suppresses reconnect.

- [ ] **Step 3: Adapt Vernacular callbacks**

Translate the library into:
- `VncFrame` dirty-region updates.
- `Connected(width,height)` after framebuffer size is known.
- `Failed(reason,retryable)` on protocol/socket failure.
- `remoteClipboard` text events.

Never expose a library framebuffer object beyond `engine/`.

- [ ] **Step 4: Protect passwords**

`VncConnectionSpec.password` is passed only during connection setup. Copy it to the minimum temporary representation required by the engine, then zero any RemoteX-owned temporary buffer. Never put it in state, exceptions, or logs.

- [ ] **Step 5: Run tests**

```bash
./gradlew :feature:vnc:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/vnc
git commit -m "feat: implement VNC session and reconnect state"
```

### Task 3: Implement Trackpad gesture interpreter

**Files:**
- Create: `feature/vnc/.../input/TrackpadGestureInterpreter.kt`
- Create: `feature/vnc/.../input/PointerTransform.kt`
- Test: `TrackpadGestureInterpreterTest.kt`

**Interfaces:**
- Produces pure functions that translate normalized gesture events into `VncInputEvent`.

- [ ] **Step 1: Write one-finger pointer test**

```kotlin
@Test
fun oneFingerMove_movesPointerWithoutClick() {
    val result = interpreter.onMove(
        fingers = 1,
        dx = 25f,
        dy = -10f,
        currentRemoteX = 100,
        currentRemoteY = 100,
    )
    assertEquals(VncInputEvent.Pointer(125, 90, 0), result)
}
```

- [ ] **Step 2: Write button mapping tests**

Required masks:

```text
left button  = 1
middle       = 2
right button = 4
wheel up     = 8
wheel down   = 16
```

Test:
- one-finger tap emits press mask `1`, then release `0`.
- two-finger tap emits press mask `4`, then release `0`.
- double tap emits two left-click cycles.
- hold + move keeps left button mask `1`.

- [ ] **Step 3: Write scroll tests**

Two-finger scroll must accumulate fractional deltas and emit discrete wheel events only after crossing threshold. This prevents hypersensitive scrolling.

- [ ] **Step 4: Implement pointer clamping**

Remote coordinates must always remain:

```text
x in 0 until framebufferWidth
y in 0 until framebufferHeight
```

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :feature:vnc:testDebugUnitTest
git add feature/vnc
git commit -m "feat: add VNC trackpad gesture mapping"
```

### Task 4: Render framebuffer, scaling, toolbar, keyboard, and clipboard

**Files:**
- Create: `VncViewModel.kt`
- Create: `VncScreen.kt`
- Create: `VncCanvas.kt`
- Create: `VncToolbar.kt`
- Create: `KeySymMapper.kt`
- Create: `VncClipboardBridge.kt`
- Test: ViewModel tests and Compose UI tests.

**Interfaces:**
- Consumes: `VncEngine`.
- Produces UI state:

```kotlin
data class VncUiState(
    val sessionState: VncSessionState,
    val bitmap: ImageBitmap?,
    val scaleMode: VncScaleMode,
    val inputMode: VncInputMode,
    val zoom: Float,
    val showToolbar: Boolean,
)
```

- [ ] **Step 1: Implement scale modes**

```kotlin
enum class VncScaleMode { FIT_SCREEN, ORIGINAL_SIZE, STRETCH }
enum class VncInputMode { TRACKPAD, DIRECT_TOUCH }
```

`FIT_SCREEN` preserves aspect ratio.  
`ORIGINAL_SIZE` uses 1 remote pixel = 1 display pixel before user zoom.  
`STRETCH` fills the viewport.

- [ ] **Step 2: Implement efficient dirty-region updates**

Keep a persistent bitmap/buffer sized to the remote framebuffer. Update only the dirty rectangle from each `VncFrame`; do not allocate a full new bitmap on every small frame.

- [ ] **Step 3: Add toolbar controls**

Toolbar must expose:

```text
Ctrl
Alt
Shift
Super
Tab
Esc
Keyboard
Trackpad/Direct Touch
Fit/Original/Stretch
Fullscreen
Clipboard
Screenshot
Disconnect
```

Modifier keys use latch behavior:
- first tap arms modifier;
- next non-modifier key sends modifier down, key down/up, modifier up;
- long press keeps modifier locked until tapped again.

- [ ] **Step 4: Map Android keys to X11 keysyms**

At minimum implement:
- A-Z/a-z
- digits
- Enter
- Backspace
- Tab
- Escape
- arrows
- Home/End
- PageUp/PageDown
- Insert/Delete
- F1-F12
- Ctrl/Alt/Shift/Super

Unknown keys must be ignored with a safe diagnostic event, not a crash.

- [ ] **Step 5: Add clipboard bridge**

Rules:
- remote clipboard text can be copied into Android clipboard after explicit session connection;
- sending Android clipboard to remote is user-triggered from toolbar by default;
- never write clipboard text to logs.

- [ ] **Step 6: Implement VNC screenshot saving**

Create `VncScreenshotSaver` that snapshots the current rendered framebuffer and writes a PNG through `MediaStore` into `Pictures/RemoteX/`. The saved image must represent only the remote framebuffer, not RemoteX toolbar/overlays. On API 29+ use scoped storage and do not request broad storage permission. Add a toolbar action labeled `Screenshot`/Indonesian UI equivalent and return a success/failure event to the screen.

- [ ] **Step 7: Add three-finger keyboard gesture**

Three-finger tap invokes the Android input method through a hidden/focusable text input target connected to key dispatch.

- [ ] **Step 8: Add Compose tests**

Verify toolbar content descriptions and default mode:

```kotlin
composeRule.onNodeWithText("Trackpad").assertExists()
composeRule.onNodeWithText("Pas Layar").assertExists()
composeRule.onNodeWithContentDescription("Putuskan").assertExists()
```

- [ ] **Step 9: Run tests and commit**

```bash
./gradlew :feature:vnc:testDebugUnitTest :feature:vnc:lintDebug
git add feature/vnc
git commit -m "feat: add VNC rendering and session controls"
```

### Task 5: Integrate VNC with profiles and Android lifecycle

**Files:**
- Modify: `app/RemoteXApp.kt`
- Modify: `AppContainer.kt`
- Modify: Home navigation
- Modify: `AndroidManifest.xml`
- Test: navigation and lifecycle tests.

- [ ] **Step 1: Add `INTERNET` permission**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Do not request location permission for ordinary VNC connectivity.

- [ ] **Step 2: Resolve credential only at connect time**

Flow:

```text
profile selected
-> if Always Ask, show password dialog
-> if Save Securely, decrypt from CredentialStore
-> create VncConnectionSpec
-> connect
```

No ViewModel state object may retain the password after connection setup finishes.

- [ ] **Step 3: Enforce landscape-first VNC UI**

On entering VNC, prefer landscape for the remote desktop activity/screen behavior while still tolerating device rotation. SSH/SFTP are not globally forced into landscape.

- [ ] **Step 4: Manage display and Wi-Fi wake state**

During an active VNC screen, keep the display awake using the Activity window flag or Compose side effect. When the active transport is Wi-Fi, acquire a non-reference-counted `WifiManager.WifiLock` only for the active session. Always release both display/wake state and Wi-Fi lock on disconnect, failure, or disposal. `WAKE_LOCK` is sufficient for `WifiLock`; do not request location merely for this behavior.

- [ ] **Step 5: Pause rendering in background**

When lifecycle is below `STARTED`:
- stop collecting/rendering frame updates;
- preserve engine session only if socket remains healthy;
- resume frame collection on foreground.

- [ ] **Step 6: Run module and app verification**

```bash
./gradlew :feature:vnc:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- [ ] **Step 7: Commit**

```bash
git add app feature/vnc
git commit -m "feat: integrate VNC remote desktop"
```

## VNC Device Acceptance Gate

On the POCO X7 Pro, verify against a VNC server you control:

```text
Connect/disconnect ✓
Framebuffer renders ✓
Fit Screen default ✓
Landscape usable ✓
One-finger pointer ✓
One-finger left click ✓
Two-finger right click ✓
Two-finger scroll ✓
Double-click ✓
Drag ✓
Pinch zoom ✓
Three-finger keyboard ✓
Ctrl/Alt/Tab/Esc ✓
Clipboard send/receive ✓
Screenshot saved to Pictures/RemoteX ✓
3 retry maximum ✓
No password in logs ✓
```
