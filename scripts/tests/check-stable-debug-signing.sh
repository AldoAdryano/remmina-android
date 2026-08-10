#!/usr/bin/env bash
set -euo pipefail

workflow=.github/workflows/build-debug.yml
gradle_file=app/build.gradle.kts

fail() { echo "Stable debug signing check FAILED: $*" >&2; exit 1; }

grep -q 'REMOTEX_KEYSTORE_BASE64' "$workflow" || fail 'debug workflow does not consume persistent keystore secret'
grep -q 'REMOTEX_KEYSTORE_PATH' "$workflow" || fail 'debug workflow does not expose decoded keystore path'
grep -q 'REMOTEX_VERSION_CODE:.*github.run_number' "$workflow" || fail 'debug workflow does not increment versionCode from github.run_number'
grep -q 'signingConfig = signingConfigs.getByName("remotex")' "$gradle_file" || fail 'debug build does not use the persistent RemoteX signing config'
grep -q 'getByName("debug")' "$gradle_file" || fail 'debug build type missing'

echo 'Stable debug signing source check: OK'
