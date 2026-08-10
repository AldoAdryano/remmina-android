# RemoteX Integration and GitHub Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate all RemoteX V1 features, harden lifecycle/security behavior, build APKs entirely through GitHub Actions, and complete device acceptance on POCO X7 Pro Android 16.

**Architecture:** Protocol modules remain independent and are assembled by `AppContainer` and navigation. CI validates unit tests, lint, license/security checks, and debug builds. Release CI signs only from GitHub Secrets and attaches checksummed APKs to tag-based GitHub Releases.

**Tech Stack:** GitHub Actions, Gradle 9.5.0, JDK 17, Android SDK 36, NDK 28.2.13676358 for terminal JNI, shell scripts for verification, Android instrumentation/device testing.

## Global Constraints

- Public repository; no secrets committed.
- Debug APK on push, PR, manual dispatch.
- Release APK on `v*` tag.
- Release signing secrets only in GitHub repository secrets.
- No CI output may print keystore password, key password, private key, or server credentials.
- V1 acceptance is performed on POCO X7 Pro Android 16.
- GitHub Actions is the primary build path.

---

## File Map

```text
.github/workflows/
├── build-debug.yml
└── release.yml

scripts/
├── check-secrets.sh
├── check-third-party-licenses.sh
└── verify-release-apk.sh

docs/
├── testing/v1-device-checklist.md
└── security/credential-threat-model.md

README.md
SECURITY.md
THIRD_PARTY_LICENSES.md
```

### Task 1: Wire final navigation and shared lifecycle behavior

**Files:**
- Modify: `app/RemoteXApp.kt`
- Modify: `app/AppContainer.kt`
- Modify: all protocol navigation entry points.
- Test: app navigation tests.

**Interfaces:**
- Home navigation routes:
  - `connection/{id}/vnc`
  - `connection/{id}/ssh`
  - `connection/{id}/sftp`
  - `connection/new`
  - `connection/{id}/edit`
  - `settings`

- [ ] **Step 1: Write route-resolution tests**

A profile ID must be loaded from repository; no complete profile or credential is passed through a route string.

- [ ] **Step 2: Centralize credential prompts**

Create a reusable credential prompt coordinator with separate types for:
- VNC password
- SSH password
- private-key passphrase

Do not store entered text in SavedStateHandle.

- [ ] **Step 3: Centralize connection success tracking**

Only successful protocol authentication/session establishment updates recent timestamp.

- [ ] **Step 4: Verify back navigation**

After disconnect:
- VNC back returns to profile/home.
- SSH disconnect returns without leaving an active shell.
- SFTP back cancels only UI observation, not explicitly backgrounded transfers.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
git add app
git commit -m "feat: integrate RemoteX V1 navigation"
```

### Task 2: Add security and repository leak checks

**Files:**
- Create: `scripts/check-secrets.sh`
- Create: `docs/security/credential-threat-model.md`
- Update: `.gitignore`
- Update: `SECURITY.md`

**Interfaces:**
- Script exits non-zero when forbidden sensitive file patterns are tracked.

- [ ] **Step 1: Harden `.gitignore`**

Required patterns:

```gitignore
*.jks
*.keystore
*.pem
*.key
.env
.env.*
local.properties
secrets.properties
**/connections-export*.json
**/remotex-profiles*.json
```

- [ ] **Step 2: Add tracked-file leak script**

```bash
#!/usr/bin/env bash
set -euo pipefail

for pattern in '*.jks' '*.keystore' '*.pem' '*.key' '.env' 'secrets.properties' 'local.properties'; do
  if git ls-files "$pattern" | grep -q .; then
    echo "Forbidden tracked secret-like file: $pattern"
    exit 1
  fi
done

if git grep -nEi '(BEGIN (RSA |OPENSSH )?PRIVATE KEY|password[[:space:]]*=[[:space:]]*["'\''][^"'\'']+)' -- \
  ':!docs/**' ':!scripts/check-secrets.sh'; then
  echo "Potential hard-coded secret detected"
  exit 1
fi
```

- [ ] **Step 3: Document threat boundaries**

Threat model must explicitly cover:
- public source repository;
- lost/unlocked phone;
- Android backup;
- malicious local app clipboard access;
- hostile Wi-Fi/MITM;
- changed SSH host key;
- unencrypted VNC risk;
- logs/notifications;
- exported profiles.

State clearly: VNC without transport encryption is not safe over untrusted networks; RemoteX V1 is primarily for trusted LAN/VPN use until SSH tunnel/VNC TLS support is explicitly validated.

- [ ] **Step 4: Run script and commit**

```bash
chmod +x scripts/check-secrets.sh
./scripts/check-secrets.sh
git add .gitignore scripts SECURITY.md docs/security
git commit -m "security: add repository leak checks and threat model"
```

### Task 3: Create debug CI workflow

**Files:**
- Create: `.github/workflows/build-debug.yml`

- [ ] **Step 1: Add triggers**

```yaml
on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:
```

- [ ] **Step 2: Add build job**

Workflow job sequence:

```yaml
steps:
  - uses: actions/checkout@v5
  - uses: actions/setup-java@v5
    with:
      distribution: temurin
      java-version: "17"
      cache: gradle
  - uses: gradle/actions/setup-gradle@v4
  - run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;28.2.13676358"
  - run: chmod +x gradlew
  - run: ./scripts/check-secrets.sh
  - run: ./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
  - uses: actions/upload-artifact@v4
    with:
      name: remotex-debug
      path: app/build/outputs/apk/debug/app-debug.apk
      if-no-files-found: error
```

- [ ] **Step 3: Add cloud-managed instrumentation job**

On pushes to `main` and manual dispatches, run Android instrumentation through the Gradle-managed `pixel6api36` device. Do not require the user's laptop to run an emulator.

```yaml
  instrumented:
    if: github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"
          cache: gradle
      - uses: gradle/actions/setup-gradle@v4
      - run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;28.2.13676358"
      - run: chmod +x gradlew
      - run: ./gradlew pixel6api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect --stacktrace
```

This job covers Android Keystore, Room instrumentation, and Compose/device tests in the cloud.

- [ ] **Step 4: Add CI concurrency**

Cancel superseded branch/PR runs, but do not cancel tag releases.

- [ ] **Step 5: Push branch and inspect Actions**

Success requirements:
- unit tests pass;
- lint passes;
- `pixel6api36DebugAndroidTest` passes in the cloud-managed emulator;
- debug APK artifact exists;
- no secret values printed.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/build-debug.yml
git commit -m "build: add GitHub Actions debug APK workflow"
```

### Task 4: Configure release signing without committing keys

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `scripts/verify-release-apk.sh`
- Create documentation section in `README.md`.

**GitHub Secret names:**

```text
REMOTEX_KEYSTORE_BASE64
REMOTEX_KEYSTORE_PASSWORD
REMOTEX_KEY_ALIAS
REMOTEX_KEY_PASSWORD
```

- [ ] **Step 1: Add environment-backed signing config**

Gradle must create release signing config only when all four values exist. Decode the keystore to a temporary CI path before Gradle runs; do not store Base64 in repository files.

- [ ] **Step 2: Provide one-time key creation instructions**

Use a trusted JDK shell or GitHub Codespace:

```bash
keytool -genkeypair \
  -v \
  -keystore remotex-release.jks \
  -alias remotex \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Then locally encode without line wraps:

```bash
base64 -w 0 remotex-release.jks > remotex-release.jks.b64
```

On macOS:

```bash
base64 < remotex-release.jks | tr -d '\n' > remotex-release.jks.b64
```

Put the resulting string into `REMOTEX_KEYSTORE_BASE64`, then delete the `.b64` file from the working directory.

- [ ] **Step 3: Add APK verification script**

Verify:
- APK exists;
- `apksigner verify --verbose` succeeds;
- SHA-256 is printed;
- package ID is `com.remotex.android`.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts scripts/verify-release-apk.sh README.md
git commit -m "build: configure secret-backed release signing"
```

### Task 5: Create tag-based GitHub Release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Add release trigger**

```yaml
on:
  push:
    tags:
      - "v*"
```

- [ ] **Step 2: Decode keystore safely**

Use `printf '%s' "$REMOTEX_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/remotex-release.jks"`.

Mask secrets automatically through GitHub Secrets and never `set -x`.

- [ ] **Step 3: Build and verify**

Run:

```bash
./scripts/check-secrets.sh
./gradlew clean testDebugUnitTest lintDebug assembleRelease
./scripts/verify-release-apk.sh app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk > RemoteX-Android.sha256
```

- [ ] **Step 4: Publish GitHub Release**

Attach:
- `RemoteX-Android-<tag>.apk`
- `RemoteX-Android.sha256`

Release notes must state:
- V1 protocols: VNC, SSH, SFTP;
- Android 8+;
- primarily tested on Android 16;
- VNC over untrusted networks should use a trusted VPN/network until encrypted transport is configured.

- [ ] **Step 5: Remove temporary keystore**

Use an `if: always()` step:

```bash
rm -f "$RUNNER_TEMP/remotex-release.jks"
```

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "build: publish signed tag releases"
```

### Task 6: Execute full automated verification

**Files:**
- Create: `docs/testing/v1-device-checklist.md`

- [ ] **Step 1: Run local/cloud test suite**

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
./scripts/check-secrets.sh
```

Expected: all PASS.

- [ ] **Step 2: Check dependency tree for licensing surprises**

Run:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath > build/runtime-dependencies.txt
```

Manually confirm no GPL/AGPL component is packaged in the APK. Preserve Apache/MIT/Bouncy Castle notices in `THIRD_PARTY_LICENSES.md`.

- [ ] **Step 3: Build through GitHub Actions**

Trigger `workflow_dispatch`.

Download `remotex-debug` artifact and verify its SHA-256 locally or on Android-capable tooling.

- [ ] **Step 4: Install on POCO X7 Pro**

Enable installation from the selected browser/file manager only long enough to install the APK. Disable that permission afterward if desired.

- [ ] **Step 5: Run connection profile acceptance**

Verify:
- create/edit/delete;
- favorites;
- recent;
- no bundled personal server;
- Save Securely;
- Always Ask;
- app restart retains metadata and decrypts saved credentials correctly.

- [ ] **Step 6: Run VNC acceptance**

Checklist:

```text
connect
render
landscape
trackpad pointer
left click
two-finger right click
two-finger scroll
double click
drag
pinch zoom
keyboard
Ctrl Alt Tab Esc
clipboard
screenshot -> Pictures/RemoteX
disconnect
bounded reconnect
```

- [ ] **Step 7: Run SSH acceptance**

Checklist:

```text
unknown fingerprint prompt
trust
reconnect trusted host
changed-key block test using controlled test host
password auth
private-key auth
passphrase auth
terminal I/O
PTY resize
copy/paste
special keys
```

- [ ] **Step 8: Run SFTP acceptance**

Checklist:

```text
list
upload
download
mkdir
rename
copy
move
delete
overwrite confirmation
background progress
API 34+ UIDT execution
cancel
retry
Downloads/RemoteX output
```

- [ ] **Step 9: Inspect diagnostics**

Search exported/local diagnostic logs for:
- password used during testing;
- passphrase;
- private-key markers;
- clipboard sample.

Expected: zero matches.

- [ ] **Step 10: Commit device checklist results template**

```bash
git add docs/testing/v1-device-checklist.md
git commit -m "test: add RemoteX V1 device acceptance checklist"
```

### Task 7: Produce first signed release candidate

**Files:**
- Update `README.md`
- Update version fields in `app/build.gradle.kts`

- [ ] **Step 1: Set release candidate version**

```text
versionCode = 1
versionName = "1.0.0-rc1"
```

- [ ] **Step 2: Verify working tree**

```bash
git status --short
```

Expected: no uncommitted secret/key files and only intended source changes.

- [ ] **Step 3: Run final pre-tag gate**

```bash
./scripts/check-secrets.sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Expected: PASS.

- [ ] **Step 4: Tag**

```bash
git tag -a v1.0.0-rc1 -m "RemoteX Android v1.0.0-rc1"
git push origin main
git push origin v1.0.0-rc1
```

- [ ] **Step 5: Verify GitHub Release**

Confirm:
- release workflow green;
- signed APK attached;
- checksum attached;
- APK installs on POCO;
- installed app reports correct package and version;
- VNC/SSH/SFTP smoke tests still pass.

- [ ] **Step 6: Promote to 1.0.0 only after RC acceptance**

Change:

```text
versionCode = 2
versionName = "1.0.0"
```

Commit:

```bash
git add app/build.gradle.kts README.md
git commit -m "release: prepare RemoteX Android 1.0.0"
git tag -a v1.0.0 -m "RemoteX Android v1.0.0"
git push origin main
git push origin v1.0.0
```

## V1 Final Acceptance Gate

RemoteX V1 is complete only when all items are true:

```text
Public MIT repository ✓
No embedded personal Jetson data ✓
GitHub debug build ✓
Signed GitHub release ✓
POCO X7 Pro Android 16 install ✓
Connection profile CRUD ✓
Encrypted credential storage ✓
Always Ask policy ✓
VNC full device checklist including screenshot and scoped Wi-Fi lock release ✓
SSH full device checklist ✓
SFTP full device checklist including API 34+ UIDT background transfer ✓
Host-key change protection ✓
Seven-day log retention ✓
No secrets in diagnostics ✓
Third-party license notices ✓
No GPL/AGPL runtime dependency ✓
```
