# RemoteX Android

RemoteX is a mobile-first Android remote administration client with VNC, SSH, and SFTP.

> Status: implementation in progress on `feature/remotex-v1`.

## Android build compatibility

RemoteX currently compiles with **compileSdk 37** while keeping **targetSdk 36** and **minSdk 26**. This is intentional: current AndroidX/Compose dependencies require API 37 at compile time, while the primary runtime target remains Android 16 / API 36.
