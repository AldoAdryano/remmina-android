#!/usr/bin/env bash
set -euo pipefail

APP='app/src/main/kotlin/com/remotex/android/RemoteXApplication.kt'
RUNTIME='feature/ssh/src/main/kotlin/com/remotex/feature/ssh/engine/SshdAndroidRuntime.kt'

[[ -f "$RUNTIME" ]] || { echo "FAIL: missing SshdAndroidRuntime.kt"; exit 1; }
grep -q 'PathUtils.setUserHomeFolderResolver' "$RUNTIME" || { echo 'FAIL: MINA user-home resolver is not configured'; exit 1; }
grep -q 'OsUtils.setCurrentWorkingDirectoryResolver' "$RUNTIME" || { echo 'FAIL: MINA CWD resolver is not configured'; exit 1; }
grep -q 'OsUtils.setAndroid' "$RUNTIME" || { echo 'FAIL: MINA Android mode is not configured'; exit 1; }
grep -q 'SshdAndroidRuntime.configure' "$APP" || { echo 'FAIL: RemoteXApplication does not initialize MINA Android runtime'; exit 1; }

echo 'MINA Android runtime source check: OK'
