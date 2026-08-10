# RemoteX Android V1 — Design Specification

**Date:** 2026-08-10  
**Status:** Approved design, pending written-spec review  
**License:** MIT  
**Repository visibility:** Public  
**Primary target device:** POCO X7 Pro, Android 16  
**Build strategy:** GitHub Actions (cloud build)

## 1. Product Goal

RemoteX Android is a mobile-first remote administration client inspired by the workflow of Remmina, but implemented as an independent Android application.

V1 must provide three core capabilities in a single Android application:

1. VNC remote desktop
2. SSH terminal
3. SFTP file manager

The application must be usable as a personal APK without requiring local Android Studio builds. GitHub Actions is the primary build environment.

The application must not contain hard-coded personal Jetson connection data, passwords, private keys, VNC passwords, release keystores, or other secrets.

## 2. Technical Baseline

- Language: Kotlin
- UI: Jetpack Compose
- Architecture: feature-modular
- minSdk: 26
- compileSdk: 37
- targetSdk: 36
- Primary architecture target: ARM64
- Additional deliverable: universal APK where practical
- License: MIT
- Repository: Public
- Dependency policy: use mature open-source protocol libraries with licenses compatible with the MIT-licensed application
- Third-party notices: `THIRD_PARTY_LICENSES.md`

## 3. Repository Structure

```text
RemoteX-Android/
├── app/
├── core/
│   ├── common/
│   ├── database/
│   ├── network/
│   ├── security/
│   ├── logging/
│   └── ui/
├── feature/
│   ├── home/
│   ├── connections/
│   ├── vnc/
│   ├── ssh/
│   ├── sftp/
│   └── settings/
├── .github/
│   └── workflows/
│       ├── build-debug.yml
│       └── release.yml
├── docs/
├── README.md
├── LICENSE
├── SECURITY.md
├── THIRD_PARTY_LICENSES.md
└── .gitignore
```

The protocol engines must be isolated from presentation logic so that a protocol engine can be replaced without rewriting the UI.

## 4. Application Navigation

Primary navigation:

```text
Home
├── Favorites
├── Recent
├── All Connections
├── Quick Connect
└── Settings
```

A single saved host can expose multiple actions:

```text
Host Profile
├── Desktop → VNC
├── Terminal → SSH
└── Files → SFTP
```

The user does not need three separate profiles when VNC, SSH, and SFTP point to the same machine.

## 5. Connection Profile

Each connection profile contains:

- Connection name
- Hostname or IP address
- Username
- Notes
- Favorite flag
- VNC enabled flag
- VNC port
- SSH/SFTP enabled flag
- SSH/SFTP port
- Authentication mode
- Credential behavior
- Protocol-specific advanced settings
- Last-connected timestamp

Credential behavior is selectable per profile:

1. Save securely
2. Always ask

No personal Jetson host, username, or password is bundled with the application.

## 6. Credential Security

Plaintext passwords must never be stored in Room, SharedPreferences, logs, source code, exported profiles, or GitHub.

Credential flow:

```text
User input
→ Credential Manager
→ Android Keystore-backed encryption
→ Encrypted local credential storage
```

Supported credential types:

- VNC password
- SSH password
- SSH private key reference/data
- SSH private-key passphrase

For SSH key passphrases, the user can choose:

- Remember securely
- Always ask

Exported profiles must exclude passwords, private keys, and credential material by default.

## 7. VNC Remote Desktop

### 7.1 Default Session Behavior

- Default orientation: landscape
- Default input mode: Trackpad Mode
- Default scaling: Fit Screen
- Fullscreen available
- Direct Touch Mode available as an alternative
- Clipboard text synchronization supported
- Auto reconnect: maximum 3 automatic retries

### 7.2 Trackpad Gesture Mapping

- One-finger move → move remote pointer
- One-finger tap → left click
- Double tap → double click
- Tap-hold and drag → drag and drop
- Two-finger vertical/horizontal movement → scroll
- Two-finger tap → right click
- Pinch → zoom
- Three-finger tap → open Android keyboard

### 7.3 VNC Toolbar

Toolbar controls:

- Ctrl
- Alt
- Shift
- Super
- Tab
- Esc
- Keyboard
- Input mode
- Fullscreen
- Scaling
- Clipboard
- Session settings
- Disconnect

### 7.4 Scaling Modes

- Fit Screen — default
- Original Size
- Stretch

Pinch zoom remains available where compatible with the selected scaling mode.

### 7.5 Reconnect Behavior

On connection loss:

```text
Retry 1
→ Retry 2
→ Retry 3
→ Show Reconnect / Disconnect actions
```

The application must not reconnect indefinitely.

## 8. SSH Terminal

Supported authentication:

- Password
- Private key
- Private key + passphrase

Terminal features:

- Interactive shell
- Portrait and landscape
- Copy
- Paste
- Adjustable font size
- Color scheme setting
- Ctrl
- Alt
- Esc
- Tab
- Arrow keys
- Local command history
- Keep-alive
- Reconnect

The terminal must remain logically separate from the SSH transport layer.

## 9. SSH Host Verification

Unknown SSH hosts must require explicit user approval.

First connection flow:

```text
Unknown host
→ Display algorithm + fingerprint
→ Reject / Trust
```

Known-host fingerprints are stored locally.

If a previously trusted host presents a different host key:

```text
HOST KEY CHANGED
```

The application must not silently accept the new key.

## 10. SFTP File Manager

### 10.1 Layout

Portrait:

```text
Local | Remote
```

Landscape:

```text
Local Android pane | Remote server pane
```

### 10.2 Operations

- Upload
- Download
- Copy
- Move
- Rename
- Delete
- Create folder
- Overwrite confirmation
- Cancel transfer
- Retry failed transfer
- Transfer progress

Default download directory:

```text
Downloads/RemoteX/
```

Android storage access must use supported Android storage APIs rather than unsafe raw-storage assumptions.

## 11. Transfer Manager

Long-running SFTP transfers must continue when the user leaves the SFTP screen.

The transfer manager must expose:

- Filename
- Direction
- Progress
- Transfer speed
- Waiting/running/failed/completed state
- Cancel
- Retry

Transfer progress must also be visible through an Android notification when the transfer runs in the background.

## 12. Background Behavior

SSH:
- May remain active in the background subject to Android lifecycle constraints.

SFTP:
- Active transfers should continue using an Android-appropriate foreground/background execution mechanism.

VNC:
- Rendering pauses when backgrounded.
- The session may be retained temporarily where practical.
- Rendering resumes when the user returns.

No design may rely on unrestricted background execution that modern Android does not permit.

## 13. Power and Connectivity

During an active remote session:

- Keep screen awake: enabled by default
- Keep Wi-Fi connectivity active where supported
- Release wake locks when the session terminates

The implementation must avoid persistent unnecessary wake locks.

## 14. Local Logging

Local logs are intended only for troubleshooting.

Allowed examples:

- Session start
- Protocol handshake status
- Connection failure category
- Transfer state
- Rendering initialization

Never log:

- Passwords
- Private keys
- Passphrases
- Credential ciphertext
- Clipboard content

Logs:

- Can be cleared manually
- Auto-delete after 7 days

## 15. App Lock

Application lock is optional and disabled by default.

Supported lock:

- Android biometric authentication where available

The application must still protect saved credentials even if app lock is disabled.

## 16. Import / Export Profiles

Export format:

```text
remotex-profiles.json
```

Export includes:

- Host
- Username
- Ports
- Favorite state
- Notes
- Protocol settings

Export excludes by default:

- Password
- Private key
- Passphrase
- Credential material

V1 uses manual import/export only. Cloud backup is out of scope.

## 17. Favorites and Recent Connections

Home sections:

- Favorites
- Recent
- All Connections

Recent connections retain at most 20 entries.

Quick Connect allows a temporary connection without requiring profile creation.

## 18. Protocol Engine Direction

Initial intended stack:

- VNC: permissively licensed Java-compatible RFB/VNC implementation, currently planned around Vernacular after Android compatibility validation
- SSH: Apache MINA SSHD
- SFTP: Apache MINA SSHD SFTP support
- Terminal renderer: Android-compatible terminal component with permissive licensing after license/API validation
- Future RDP: FreeRDP, Phase 2

All dependency versions and licenses must be verified during implementation planning before code is committed.

No GPL protocol engine may be directly integrated into the MIT APK unless the project licensing decision is deliberately revisited.

## 19. Extensibility

V1 must define clear abstractions for sessions so future protocols do not require a rewrite.

Conceptual direction:

```text
RemoteSession
├── VncSession
├── SshSession
└── FutureRdpSession
```

Roadmap:

- V1: VNC + SSH + SFTP
- V1.1: SSH Tunnel
- V2: RDP
- V3: SPICE / X2Go evaluation

RDP, SPICE, and X2Go are not V1 acceptance requirements.

## 20. UI Direction

UI style:

- Mobile-native
- Modern
- Minimal
- Dark/light theme follows System by default
- Dark navy + cyan accent direction
- Accessible touch targets
- Avoid desktop UI copied directly onto mobile

Primary connection card concept:

```text
My Server
192.168.x.x

[ Desktop ]
[ Terminal ] [ Files ]
```

## 21. Icon Direction

RemoteX must use a custom icon, not a temporary `RX` text icon.

Concept:

- Remote display / screen
- Cross-device connection
- Four-direction connection motif
- Implicit `X`
- Minimal geometric form
- Distinct from Remmina branding

Required Android icon assets later:

- Adaptive icon foreground
- Adaptive icon background
- Monochrome icon

## 22. GitHub Actions

The local laptop is not expected to perform normal Android builds.

### Debug workflow

Triggers:

- Push to `main`
- Pull request
- Manual `workflow_dispatch`

Output:

- Debug APK as GitHub Actions artifact

### Release workflow

Trigger:

```text
v*
```

Example:

```text
v1.0.0
```

Release workflow:

```text
Checkout
→ Configure JDK
→ Configure Android/Gradle environment
→ Build release APK
→ Sign using GitHub Secrets
→ Generate checksum
→ Create GitHub Release
→ Attach APK
```

No release signing material is committed to the public repository.

## 23. Repository Security

`.gitignore` must cover at minimum:

```text
*.jks
*.keystore
local.properties
secrets.properties
.env
*.pem
*.key
```

Repository must never contain:

- Personal connection profiles
- Jetson passwords
- VNC passwords
- SSH private keys
- Release keystore
- Signing passwords
- GitHub secrets

## 24. Documentation

Public repository documentation:

- `README.md` in English
- `LICENSE` using MIT
- `SECURITY.md`
- `THIRD_PARTY_LICENSES.md`

Implementation documentation may include additional architecture and protocol notes.

## 25. V1 Acceptance Criteria

V1 is considered complete only when all applicable checks pass on the POCO X7 Pro running Android 16:

- GitHub Actions successfully builds the APK
- APK installs successfully
- User can create, edit, and delete a connection profile
- User can choose Save Securely or Always Ask credentials
- VNC connects successfully to a compatible server
- Remote desktop renders correctly
- Trackpad pointer control works
- One-finger left click works
- Two-finger right click works
- Two-finger scroll works
- Drag and drop works
- Android keyboard input works
- Ctrl/Alt/Tab/Esc controls work
- Clipboard text transfer works
- Reconnect logic works
- SSH terminal connects successfully
- SSH password authentication works
- SSH private-key authentication works
- SSH host-fingerprint verification works
- Changed SSH host key is not silently accepted
- SFTP remote directory listing works
- SFTP upload works
- SFTP download works
- Transfer progress works
- Background transfer notification works
- Saved credentials are encrypted
- No secrets appear in application logs
- Portrait/landscape behavior works as designed
- GitHub tag build can produce a signed release APK
- GitHub Release attachment workflow works

## 26. Explicitly Out of Scope for V1

- Google Play Store publication
- RDP
- SPICE
- X2Go
- SSH tunneling
- LAN discovery
- Screen recording
- Google Drive/cloud backup
- Hard-coded personal server templates
- Analytics or telemetry
- Ads
- User accounts/cloud sync

## 27. Design Review Notes

Self-review completed against the approved conversation decisions:

- No unresolved placeholders remain in V1 scope.
- VNC default input is Trackpad Mode.
- Right click is explicitly two-finger tap.
- Credential behavior is selectable per profile.
- Repository is public and MIT-licensed.
- Personal Jetson data is explicitly excluded from source.
- Custom icon design is required.
- VNC, SSH, and SFTP are all included in V1.
- RDP and later protocols remain outside V1 acceptance criteria.
- Background execution is intentionally constrained by modern Android lifecycle rules rather than assuming unrestricted background access.
- Third-party protocol dependencies remain subject to implementation-time license/API verification.
