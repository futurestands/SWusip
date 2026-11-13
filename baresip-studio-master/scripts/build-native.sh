#!/usr/bin/env bash
# Build the upstream engine. Never run upstream download-sources over local work.
set -euo pipefail
project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
: "${ANDROID_NDK_ROOT:?Set ANDROID_NDK_ROOT to an extracted Linux NDK matching app/build.gradle.kts}"
abi=${1:-arm64-v8a}
case "$abi" in arm64-v8a|armeabi-v7a|x86_64) ;; *) echo 'Unsupported ABI' >&2; exit 2;; esac
host=$(uname -s)
[[ "$host" == Linux ]] || { echo 'Run in Linux or WSL with a Linux NDK' >&2; exit 2; }
tools="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -x "$tools/clang" ]] || { echo "Missing Linux NDK clang: $tools/clang (a Windows NDK cannot be used in WSL)" >&2; exit 2; }
expected=$(sed -n 's/.*ndkVersion = "\([^"]*\)".*/\1/p' "$project/app/build.gradle.kts")
actual=$(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_NDK_ROOT/source.properties" | tr -d '\r')
[[ "$actual" == "$expected" ]] || { echo "NDK revision must be $expected, found $actual" >&2; exit 2; }
for command in make cmake autoconf automake libtoolize pkg-config patch python3; do
    command -v "$command" >/dev/null || { echo "Missing prerequisite: $command" >&2; exit 2; }
done
native=${SWUSIP_NATIVE_SOURCE:-$project/.native-build/libbaresip-android}
[[ -f "$native/Makefile" ]] || { echo "Missing prepared native source: $native. See docs/NATIVE_DEPENDENCIES.md" >&2; exit 2; }
for path in "$project" "$native" "$ANDROID_NDK_ROOT"; do
    [[ "$path" != *[[:space:]]* ]] || { echo "Upstream Makefile requires paths without whitespace: $path" >&2; exit 2; }
done
export PATH="$tools:$PATH"
"$tools/clang" --version
case "$abi" in
    arm64-v8a) compiler=aarch64-linux-android28-clang;;
    armeabi-v7a) compiler=armv7a-linux-androideabi28-clang;;
    x86_64) compiler=x86_64-linux-android28-clang;;
esac
mkdir -p "$project/.native-build"
printf 'int main(void) { return 0; }\n' | "$tools/$compiler" -x c - -o "$project/.native-build/toolchain-probe"
# Command line variables override upstream developer-specific defaults.
resume=()
# Diagnostic resume only. Final evidence must also include an unskipped full build.
for target in ${SWUSIP_SKIP_BUILT:-}; do
    case "$target" in amr|codec2|g729|g722|g7221|gzrtp|ilbc|openssl|opus|sndfile|libre.a) resume+=(-o "$target");;
        *) echo "Invalid resume target: $target" >&2; exit 2;; esac
done
make -C "$native" -f Makefile -f "$project/scripts/native-overrides.mk" "${resume[@]}" libbaresip ANDROID_TARGET_ARCH="$abi" \
    NDK_PATH="$ANDROID_NDK_ROOT" OUTPUT_DIR="$project/distribution" CPU_COUNT="${SWUSIP_BUILD_JOBS:-4}"
# Keep installed TLS headers aligned with the actual compiled OpenSSL snapshot.
mkdir -p "$project/distribution/openssl/include"
cp -a "$native/openssl/include/openssl" "$project/distribution/openssl/include/"
python3 "$project/scripts/verify_native.py" --abi "$abi" --tools "$tools"
