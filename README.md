# RemoteX Android

RemoteX is a mobile-first Android remote administration client with VNC, SSH, and SFTP.

> Status: implementation in progress on `feature/remotex-v1`.

## Android build compatibility

RemoteX currently compiles with **compileSdk 37** while keeping **targetSdk 36** and **minSdk 26**. This is intentional: current AndroidX/Compose dependencies require API 37 at compile time, while the primary runtime target remains Android 16 / API 36.

## Android SDK compatibility

RemoteX V1 compiles against Android API 36 and targets API 36. To keep the cloud build on the released Android 16 SDK, the project pins `androidx.core` to `1.17.0` and AndroidX Lifecycle to `2.10.0`. Newer Core/Lifecycle releases used during early development require API 37 and are intentionally not used in V1.

