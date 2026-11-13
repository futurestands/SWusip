#!/usr/bin/env bash
set -euo pipefail
project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${SWUSIP_NATIVE_SOURCE:?Set an empty Linux build directory outside /mnt/c for performance}"
if [[ -e "$SWUSIP_NATIVE_SOURCE" ]]; then
    echo "Refusing to overwrite existing build directory: $SWUSIP_NATIVE_SOURCE" >&2
    exit 2
fi
mkdir -p "$SWUSIP_NATIVE_SOURCE"
tar -xf "$project/.native-build/native-builder.tar" -C "$SWUSIP_NATIVE_SOURCE"
for archive in "$project"/.native-build/sources/*.tar; do
    name=$(basename "$archive" .tar)
    mkdir "$SWUSIP_NATIVE_SOURCE/$name"
    tar -xf "$archive" -C "$SWUSIP_NATIVE_SOURCE/$name"
done
patch --batch --forward -d "$SWUSIP_NATIVE_SOURCE/g7221" -p1 < "$SWUSIP_NATIVE_SOURCE/g7221-patch"
patch --batch --forward -d "$SWUSIP_NATIVE_SOURCE/re" -p1 < "$SWUSIP_NATIVE_SOURCE/re-patch"
echo 'Staged locked upstream sources and patches.'
