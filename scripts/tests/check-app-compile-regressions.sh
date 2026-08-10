#!/usr/bin/env bash
set -euo pipefail

fail=0

if grep -q 'implementation(libs.androidx.room.runtime)' core/database/build.gradle.kts; then
  echo "Room runtime must be exported with api() because RemoteXDatabase publicly extends RoomDatabase."
  fail=1
fi

if grep -q 'profile?.let(content)' app/src/main/kotlin/com/remotex/android/RemoteXApp.kt; then
  echo "Composable content must not be passed to kotlin.let as a regular function."
  fail=1
fi

if grep -nE 'reason"[[:space:]]+to[[:space:]]+state\.reason' app/src/main/kotlin/com/remotex/android/RemoteXApp.kt; then
  echo "Delegated Compose state cannot be smart-cast; bind state to a local value first."
  fail=1
fi

if grep -nE '^[[:space:]]*setProgress\(' app/src/main/kotlin/com/remotex/android/transfer/TransferRuntime.kt; then
  echo "CoroutineWorker.setProgress() is suspend and cannot be called from the non-suspend transfer callback."
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

echo "App compile regression source check: OK"
