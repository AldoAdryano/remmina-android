# RemoteX Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a buildable RemoteX Android application with modular architecture, connection profile CRUD, encrypted credential storage, home UI, settings, logs, import/export, and custom icon.

**Architecture:** A multi-module Android project separates domain models, persistence, security, shared UI, and features. Manual dependency composition in `AppContainer` avoids a DI framework. Room stores non-secret profile metadata and encrypted credential blobs; Android Keystore owns the AES key.

**Tech Stack:** Kotlin, Jetpack Compose, AGP 9.3.0 built-in Kotlin, Gradle 9.5.0, JDK 17, Room 2.8.4, KSP 2.3.9, DataStore 1.2.1, Biometric 1.1.0.

## Global Constraints

- Application ID: `com.remotex.android`.
- App name: `RemoteX`.
- `minSdk = 26`, `compileSdk = 37`, `targetSdk = 36`.
- Public MIT repository.
- No hard-coded personal server data.
- No plaintext credentials.
- No Hilt/Dagger; use explicit interfaces and `AppContainer`.
- Do not annotate Room entities with `@Parcelize`.
- Theme follows Android system by default.
- Default application UI strings are Indonesian; README remains English.
- Logs expire after seven days.
- Exported profiles never contain secrets.

---

## File Map

```text
RemoteX-Android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/remotex/android/
│       │   ├── RemoteXApplication.kt
│       │   ├── AppContainer.kt
│       │   ├── MainActivity.kt
│       │   └── RemoteXApp.kt
│       └── res/
├── core/model/
├── core/database/
├── core/security/
├── core/logging/
├── core/ui/
├── feature/home/
├── feature/connections/
├── feature/settings/
├── LICENSE
├── SECURITY.md
├── THIRD_PARTY_LICENSES.md
└── .gitignore
```

### Task 1: Bootstrap the Android project and module graph

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/remotex/android/MainActivity.kt`
- Create build files for `core:model`, `core:database`, `core:security`, `core:logging`, `core:ui`, `feature:home`, `feature:connections`, `feature:settings`
- Create: `.gitignore`
- Create: `LICENSE`
- Create: `SECURITY.md`
- Create: `THIRD_PARTY_LICENSES.md`

**Interfaces:**
- Produces: a buildable multi-module Android application.
- Produces: package root `com.remotex.android`.

- [ ] **Step 1: Create the module list**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RemoteX-Android"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:security",
    ":core:logging",
    ":core:ui",
    ":feature:home",
    ":feature:connections",
    ":feature:settings",
    ":feature:vnc",
    ":feature:ssh",
    ":feature:sftp",
)
```

- [ ] **Step 2: Pin the build toolchain and shared dependency versions**

Use this baseline in `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.0"
ksp = "2.3.9"
compose-bom = "2026.06.00"
activity = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.9.8"
room = "2.8.4"
datastore = "1.2.1"
work = "2.11.2"
biometric = "1.1.0"
core = "1.19.0"
junit = "4.13.2"
androidx-test-core = "1.7.0"
androidx-test-runner = "1.7.0"
androidx-test-junit = "1.3.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-junit" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Do not add `org.jetbrains.kotlin.android`; AGP 9.x supplies built-in Kotlin.

In `app` dependencies, add `androidTestImplementation` for AndroidX Test Core 1.7.0, Runner 1.7.0, JUnit extension 1.3.0, and Compose `ui-test-junit4` under the Compose BOM; add `debugImplementation` for Compose `ui-test-manifest`. This is required for Room/Keystore/Compose instrumentation on the cloud-managed device.

- [ ] **Step 3: Configure Gradle wrapper**

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 4: Configure the app module**

Core values:

```kotlin
android {
    namespace = "com.remotex.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.remotex.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6api36") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

- [ ] **Step 5: Add Indonesian default string resources and a minimal launch test**

Create `app/src/main/res/values/strings.xml` with Indonesian labels for Home, connection editing, settings, protocol actions, errors, and accessibility descriptions. Do not hard-code user-facing text in Compose. Keep `README.md` in English.

Create `app/src/test/java/com/remotex/android/BuildConstantsTest.kt`:

```kotlin
package com.remotex.android

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConstantsTest {
    @Test
    fun packageName_isRemoteXPackage() {
        assertEquals("com.remotex.android", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 6: Run the first build**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit**

```bash
git add .
git commit -m "build: bootstrap RemoteX Android modules"
```

### Task 2: Define connection domain models and validation

**Files:**
- Create: `core/model/src/main/java/com/remotex/core/model/ConnectionProfile.kt`
- Create: `core/model/src/main/java/com/remotex/core/model/AuthenticationMode.kt`
- Create: `core/model/src/main/java/com/remotex/core/model/CredentialPolicy.kt`
- Create: `core/model/src/main/java/com/remotex/core/model/ConnectionValidator.kt`
- Test: `core/model/src/test/java/com/remotex/core/model/ConnectionValidatorTest.kt`

**Interfaces:**
- Produces:
  - `data class ConnectionProfile`
  - `enum class AuthenticationMode`
  - `enum class CredentialPolicy`
  - `fun ConnectionValidator.validate(profile: ConnectionProfile): List<ValidationError>`

- [ ] **Step 1: Write failing validation tests**

```kotlin
class ConnectionValidatorTest {
    private val validator = ConnectionValidator()

    @Test
    fun blankHost_isRejected() {
        val errors = validator.validate(
            ConnectionProfile.new(name = "Jetson", host = "", username = "user")
        )
        assertTrue(errors.any { it.field == "host" })
    }

    @Test
    fun invalidPorts_areRejected() {
        val errors = validator.validate(
            ConnectionProfile.new(
                name = "Jetson",
                host = "192.168.1.10",
                username = "user",
                vncPort = 70000,
                sshPort = 0,
            )
        )
        assertEquals(setOf("vncPort", "sshPort"), errors.map { it.field }.toSet())
    }

    @Test
    fun validProfile_hasNoErrors() {
        val errors = validator.validate(
            ConnectionProfile.new(
                name = "Jetson",
                host = "192.168.1.10",
                username = "user",
                vncEnabled = true,
                sshEnabled = true,
            )
        )
        assertTrue(errors.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew :core:model:testDebugUnitTest
```

Expected: compile failure because model classes do not exist.

- [ ] **Step 3: Implement exact domain types**

```kotlin
data class ConnectionProfile(
    val id: Long,
    val name: String,
    val host: String,
    val username: String,
    val notes: String,
    val favorite: Boolean,
    val vncEnabled: Boolean,
    val vncPort: Int,
    val sshEnabled: Boolean,
    val sshPort: Int,
    val authenticationMode: AuthenticationMode,
    val credentialPolicy: CredentialPolicy,
    val lastConnectedAtEpochMillis: Long?,
) {
    companion object {
        fun new(
            name: String,
            host: String,
            username: String,
            notes: String = "",
            favorite: Boolean = false,
            vncEnabled: Boolean = true,
            vncPort: Int = 5900,
            sshEnabled: Boolean = true,
            sshPort: Int = 22,
            authenticationMode: AuthenticationMode = AuthenticationMode.PASSWORD,
            credentialPolicy: CredentialPolicy = CredentialPolicy.ALWAYS_ASK,
        ) = ConnectionProfile(
            id = 0,
            name = name,
            host = host.trim(),
            username = username.trim(),
            notes = notes,
            favorite = favorite,
            vncEnabled = vncEnabled,
            vncPort = vncPort,
            sshEnabled = sshEnabled,
            sshPort = sshPort,
            authenticationMode = authenticationMode,
            credentialPolicy = credentialPolicy,
            lastConnectedAtEpochMillis = null,
        )
    }
}

enum class AuthenticationMode { PASSWORD, PRIVATE_KEY, PRIVATE_KEY_WITH_PASSPHRASE }
enum class CredentialPolicy { SAVE_SECURELY, ALWAYS_ASK }
data class ValidationError(val field: String, val message: String)
```

Validation rules:

```kotlin
class ConnectionValidator {
    fun validate(profile: ConnectionProfile): List<ValidationError> = buildList {
        if (profile.name.isBlank()) add(ValidationError("name", "Name is required"))
        if (profile.host.isBlank()) add(ValidationError("host", "Host is required"))
        if (profile.vncEnabled && profile.vncPort !in 1..65535) {
            add(ValidationError("vncPort", "VNC port must be between 1 and 65535"))
        }
        if (profile.sshEnabled && profile.sshPort !in 1..65535) {
            add(ValidationError("sshPort", "SSH port must be between 1 and 65535"))
        }
        if (!profile.vncEnabled && !profile.sshEnabled) {
            add(ValidationError("protocol", "Enable VNC or SSH/SFTP"))
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :core:model:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/model
git commit -m "feat: define connection profile domain model"
```

### Task 3: Add Room persistence and repositories

**Files:**
- Create: `core/database/src/main/java/com/remotex/core/database/RemoteXDatabase.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/ProfileEntity.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/ProfileDao.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/CredentialEntity.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/CredentialDao.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/ProfileRepository.kt`
- Create: `core/database/src/main/java/com/remotex/core/database/RoomProfileRepository.kt`
- Test: `app/src/androidTest/java/com/remotex/android/database/ProfileDaoTest.kt`

**Interfaces:**
- Produces:
  - `interface ProfileRepository`
  - `suspend fun save(profile: ConnectionProfile): Long`
  - `fun observeAll(): Flow<List<ConnectionProfile>>`
  - `fun observeFavorites(): Flow<List<ConnectionProfile>>`
  - `fun observeRecent(limit: Int = 20): Flow<List<ConnectionProfile>>`
  - `suspend fun findById(id: Long): ConnectionProfile?`
  - `suspend fun delete(id: Long)`

- [ ] **Step 1: Write the DAO instrumentation test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {
    private lateinit var db: RemoteXDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemoteXDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test
    fun insertAndLoadProfile() = runTest {
        val id = db.profileDao().upsert(ProfileEntity.sample())
        val stored = db.profileDao().findById(id)
        assertEquals("Jetson", stored?.name)
        assertEquals("192.168.1.10", stored?.host)
    }
}
```

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew :app:pixel6api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Expected: compile failure because Room types are not implemented.

- [ ] **Step 3: Implement the schema**

Use `profiles` and `credentials` tables. `profiles` contains only connection metadata and optional credential reference IDs. `credentials` contains only encrypted payload and IV.

Required `ProfileEntity` columns:

```text
id INTEGER PRIMARY KEY AUTOINCREMENT
name TEXT NOT NULL
host TEXT NOT NULL
username TEXT NOT NULL
notes TEXT NOT NULL
favorite INTEGER NOT NULL
vnc_enabled INTEGER NOT NULL
vnc_port INTEGER NOT NULL
ssh_enabled INTEGER NOT NULL
ssh_port INTEGER NOT NULL
authentication_mode TEXT NOT NULL
credential_policy TEXT NOT NULL
password_credential_id INTEGER NULL
private_key_credential_id INTEGER NULL
passphrase_credential_id INTEGER NULL
last_connected_at INTEGER NULL
```

Required `CredentialEntity` columns:

```text
id INTEGER PRIMARY KEY AUTOINCREMENT
kind TEXT NOT NULL
ciphertext BLOB NOT NULL
iv BLOB NOT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

- [ ] **Step 4: Implement DAO ordering**

Favorites:

```sql
SELECT * FROM profiles
WHERE favorite = 1
ORDER BY name COLLATE NOCASE ASC
```

Recent:

```sql
SELECT * FROM profiles
WHERE last_connected_at IS NOT NULL
ORDER BY last_connected_at DESC
LIMIT :limit
```

- [ ] **Step 5: Run Room schema and tests**

```bash
./gradlew :core:database:kspDebugKotlin
./gradlew :app:pixel6api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/database
git commit -m "feat: persist connection profiles with Room"
```

### Task 4: Implement Android Keystore encrypted credentials

**Files:**
- Create: `core/security/src/main/java/com/remotex/core/security/CredentialCipher.kt`
- Create: `core/security/src/main/java/com/remotex/core/security/AndroidKeystoreCredentialCipher.kt`
- Create: `core/security/src/main/java/com/remotex/core/security/CredentialStore.kt`
- Create: `core/security/src/main/java/com/remotex/core/security/DatabaseCredentialStore.kt`
- Test: `app/src/androidTest/java/com/remotex/android/security/AndroidKeystoreCredentialCipherTest.kt`

**Interfaces:**
- Consumes: `CredentialDao`.
- Produces:

```kotlin
data class EncryptedPayload(val ciphertext: ByteArray, val iv: ByteArray)

interface CredentialCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): ByteArray
}

interface CredentialStore {
    suspend fun put(kind: CredentialKind, secret: CharArray): Long
    suspend fun read(id: Long): CharArray?
    suspend fun delete(id: Long)
}
```

- [ ] **Step 1: Write round-trip instrumentation test**

```kotlin
@Test
fun encryptDecrypt_roundTripsWithoutPlaintextStorage() {
    val cipher = AndroidKeystoreCredentialCipher(alias = "remotex-test-aes")
    val source = "secret-value".encodeToByteArray()
    val encrypted = cipher.encrypt(source)

    assertFalse(encrypted.ciphertext.contentEquals(source))
    assertFalse(encrypted.iv.isEmpty())
    assertArrayEquals(source, cipher.decrypt(encrypted))
}
```

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew :app:pixel6api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Expected: compile failure.

- [ ] **Step 3: Implement AES/GCM using AndroidKeyStore**

Required cryptographic settings:

```text
Key algorithm: AES
Key size: 256 bits
Block mode: GCM
Padding: NoPadding
Keystore alias: remotex.credentials.aes.v1
IV: generated by Cipher.init(ENCRYPT_MODE, key)
Tag: platform AES/GCM default 128-bit tag
```

Key creation must use:

```kotlin
KeyGenParameterSpec.Builder(
    alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
```

Do not use deprecated `EncryptedSharedPreferences`, `MasterKey`, or `EncryptedFile`.

- [ ] **Step 4: Zero transient secret buffers**

After converting `CharArray` to bytes, overwrite temporary arrays:

```kotlin
try {
    // encrypt or decrypt
} finally {
    bytes.fill(0)
    secret.fill('\u0000')
}
```

Do not overwrite a caller-owned `CharArray` unless ownership is explicitly transferred; document ownership in `CredentialStore.put`.

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:pixel6api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/security core/database
git commit -m "security: protect credentials with Android Keystore"
```

### Task 5: Build profile CRUD, Home, and navigation

**Files:**
- Create: `feature/connections/.../ConnectionEditorViewModel.kt`
- Create: `feature/connections/.../ConnectionEditorScreen.kt`
- Create: `feature/home/.../HomeViewModel.kt`
- Create: `feature/home/.../HomeScreen.kt`
- Create: `core/ui/.../RemoteXTheme.kt`
- Create: `app/.../RemoteXApp.kt`
- Create: `app/.../AppContainer.kt`
- Test: ViewModel unit tests and Compose UI tests.

**Interfaces:**
- Consumes: `ProfileRepository`, `CredentialStore`, `ConnectionValidator`.
- Produces navigation callbacks:
  - `onOpenVnc(profileId: Long)`
  - `onOpenSsh(profileId: Long)`
  - `onOpenSftp(profileId: Long)`
  - `onEdit(profileId: Long)`

- [ ] **Step 1: Write ViewModel save test**

```kotlin
@Test
fun save_validProfile_persistsAndFinishes() = runTest {
    val repo = FakeProfileRepository()
    val vm = ConnectionEditorViewModel(repo, ConnectionValidator(), FakeCredentialStore())

    vm.updateName("Jetson")
    vm.updateHost("192.168.1.10")
    vm.updateUsername("user")
    vm.save()

    assertEquals(1, repo.saved.size)
    assertEquals("Jetson", repo.saved.single().name)
    assertTrue(vm.events.first() is EditorEvent.Saved)
}
```

- [ ] **Step 2: Implement `AppContainer`**

```kotlin
class AppContainer(context: Context) {
    private val database = RemoteXDatabase.create(context)
    val credentialCipher: CredentialCipher = AndroidKeystoreCredentialCipher()
    val credentialStore: CredentialStore =
        DatabaseCredentialStore(database.credentialDao(), credentialCipher)
    val profileRepository: ProfileRepository =
        RoomProfileRepository(database.profileDao())
}
```

Do not expose DAOs directly to UI modules.

- [ ] **Step 3: Implement Home card behavior**

Each card displays:

```text
Name
Host
Favorite marker
Desktop button only when VNC enabled
Terminal button only when SSH enabled
Files button only when SSH enabled
Overflow: Edit / Duplicate / Delete
```

Home sections:

```text
Favorites
Recent
All Connections
```

Maximum recent list: 20.

- [ ] **Step 4: Implement Quick Connect**

Quick Connect requires:

```text
Host
Protocol: VNC | SSH | SFTP
Port
Username when SSH/SFTP
```

Quick Connect does not persist unless the user selects `Save as profile`.

- [ ] **Step 5: Add Compose UI tests**

At minimum verify:

```kotlin
composeRule.onNodeWithText("RemoteX").assertExists()
composeRule.onNodeWithContentDescription("Tambah koneksi").assertExists()
composeRule.onNodeWithText("Desktop").assertExists()
composeRule.onNodeWithText("Terminal").assertExists()
composeRule.onNodeWithText("File").assertExists()
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:connections:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app core/ui feature/home feature/connections
git commit -m "feat: add connection management and mobile home"
```

### Task 6: Add settings, safe logs, import/export, app lock, and custom icon

**Files:**
- Create: `feature/settings/.../SettingsRepository.kt`
- Create: `feature/settings/.../SettingsScreen.kt`
- Create: `core/logging/.../SafeLogger.kt`
- Create: `core/logging/.../LogRetention.kt`
- Create: `feature/connections/.../ProfileExportService.kt`
- Create adaptive icon XML/vector assets in `app/src/main/res/`
- Test: logger redaction, export omission, retention cutoff.

**Interfaces:**
- Produces:
  - `interface SafeLogger`
  - `fun ProfileExportService.export(profiles): String`
  - `fun ProfileExportService.import(json): List<ConnectionProfile>`

- [ ] **Step 1: Write secret-redaction test**

```kotlin
@Test
fun logger_neverEmitsKnownSensitiveFields() {
    val sink = RecordingLogSink()
    val logger = RedactingSafeLogger(sink)
    logger.event(
        "ssh_connect_failed",
        mapOf("host" to "server.local", "password" to "hunter2", "clipboard" to "secret")
    )

    val output = sink.lines.joinToString("\n")
    assertFalse(output.contains("hunter2"))
    assertFalse(output.contains("secret"))
    assertTrue(output.contains("[REDACTED]"))
}
```

Sensitive key names:

```text
password
passphrase
privateKey
private_key
credential
ciphertext
clipboard
```

- [ ] **Step 2: Write export omission test**

```kotlin
@Test
fun export_containsProfileMetadataButNoCredentialFields() {
    val json = service.export(listOf(sampleProfile()))
    assertTrue(json.contains("\"host\""))
    assertFalse(json.contains("password", ignoreCase = true))
    assertFalse(json.contains("privateKey", ignoreCase = true))
    assertFalse(json.contains("credentialId", ignoreCase = true))
}
```

- [ ] **Step 3: Implement seven-day retention**

```kotlin
private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

fun cutoff(now: Long): Long = now - RETENTION_MS
```

Delete older entries on application start and when user opens Diagnostics.

- [ ] **Step 4: Implement non-sensitive DataStore settings**

Store only:

```text
theme = SYSTEM | LIGHT | DARK
defaultVncInput = TRACKPAD
defaultVncScale = FIT_SCREEN
keepScreenAwake = true
backgroundTransferNotifications = true
appLockEnabled = false
```

Do not put credentials in DataStore.

- [ ] **Step 5: Implement optional biometric app lock**

App lock default is disabled. When enabled, request biometric/device credential before revealing the main navigation graph. Credential encryption remains independent from app lock.

- [ ] **Step 6: Implement the custom RemoteX adaptive icon**

Create:
- `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi-v26/ic_launcher_round.xml`
- `drawable/ic_launcher_foreground.xml`
- `drawable/ic_launcher_monochrome.xml`
- `values/ic_launcher_background.xml`

Visual rule:

```text
dark navy rounded screen
cyan four-direction connection strokes
negative-space X at center
no "RX" text
no Remmina logo shape
```

Use one vector source for normal and monochrome variants to keep the silhouette recognizable.

- [ ] **Step 7: Run full foundation verification**

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add .
git commit -m "feat: complete RemoteX foundation services"
```

## Foundation Acceptance Gate

Do not proceed until:

```text
Debug APK builds ✓
Home launches ✓
Profile CRUD ✓
Favorites/recent ✓
Quick Connect UI ✓
Save Securely/Always Ask modeled ✓
AES/GCM Keystore round-trip ✓
No plaintext secrets ✓
Import/export excludes secrets ✓
System theme ✓
Custom icon ✓
Seven-day log retention ✓
```
