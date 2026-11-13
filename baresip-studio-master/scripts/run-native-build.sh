#!/usr/bin/env bash
# Direct WSL entry point: avoid nested Windows/WSL shell expansion of exit status.
set -uo pipefail
project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
mkdir -p "$project/docs/evidence"
log="$project/docs/evidence/native-${1:-arm64-v8a}-build.log"
printf 'native_build_status=running\n' > "$log.exit"
bash "$project/scripts/build-native.sh" "${1:-arm64-v8a}" > "$log" 2>&1
status=$?
printf 'native_build_exit=%s\n' "$status" | tee "$log.exit"
tail -n 35 "$log"
exit "$status"
