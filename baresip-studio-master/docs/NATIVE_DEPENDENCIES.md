# Native source inventory

Native builder: upstream `juha-h/libbaresip-android` at
`bf8448d8db4c3ff0ad3dabdd2d957954fed8ce22`, preserving the project gitlink.
Exact source URLs, refs and immutable commits are in
[`scripts/native-sources.lock.json`](../scripts/native-sources.lock.json).
Initial lock resolution and fetch evidence is under `docs/evidence/`.

The existing local source copies were not trusted: they have no independent Git
metadata. Recovery fetches each locked commit into `.native-build/`, then exports
it with `core.autocrlf=false` to preserve LF and executable bits. Linux staging
applies only the two patches supplied by the pinned upstream builder. Original
local copies remain intact. Do not run upstream `make download-sources`: it deletes
source trees and fetches moving branches.

## Versions and attribution

- Baresip 4.13.0 and Libre 4.13.0: BSD-3-Clause; retain project/source notices.
- OpenSSL: inherited `openssl-4.0` branch, frozen to the lock commit. Its VERSION.dat
  currently says **4.0.4-dev**. Apache-2.0. A development snapshot is a release risk;
  no claim of production approval or vulnerability clearance is made.
- Opus v1.4: retain COPYING and accompanying patent/licence notices.
- bcg729 release/1.1.1: **GPLv3** (LICENSE.txt); inherited dependency, not newly
  introduced. Distribution obligations must be reviewed before institutional release.
- Codec2 1.2.0: LGPL-2.1; retain COPYING and provide required source/relink materials.
- libg722 v1.2.2: retain its Carnegie Mellon and per-file notices.
- libg7221: COPYING contains Polycom licensing conditions. Do not infer current
  patent status or institutional distribution rights from successful compilation.
- libilbc: README declares Apache-2.0; retain source notices.
- opencore-amr v0.1.6 and vo-amrwbenc: Apache-2.0 notices; codec patent questions
  are separate from source copyright licences.
- libsndfile: LGPL-2.1; preserve source and relinking obligations.
- ZRTPCPP: COPYING declares Apache-2.0; preserve all per-file notices as well.

This lock pins existing upstream choices; it does not approve licences or certify
vulnerability status. No codec substitution or significant licence upgrade was made.
Retain complete upstream licence files with release source and attribution bundle.

## Reproduction

1. Initialise the project submodule from the actual Git root once its root mapping
   is present. Recovery uses a separate upstream clone at the same gitlink revision.
2. In the Android project run `python scripts/prepare_native.py`. It prepares the
   builder at its pinned revision and exports all locked sources. Normal operation
   does **not** resolve branch heads. No manual Makefile editing is required.
3. Source archive SHA-256 values are recorded in
   `docs/evidence/native-source-archive-sha256.json`; source Git commits remain
   the primary provenance. The optional `export-native-builder.ps1` exports only
   the builder if that individual export needs repeating.
4. Supply a matching **Linux** NDK to WSL; the Windows NDK cannot be reused as the host
   toolchain. Set `ANDROID_NDK_ROOT` and an initially absent `SWUSIP_NATIVE_SOURCE`
   on the Linux filesystem. Run `bash scripts/stage-native-linux.sh`, then
   `bash scripts/build-native.sh arm64-v8a`.
5. Verify real object members, ABI and six required implementations with
   `scripts/verify_native.py`. Checksums identify build outputs; bit-for-bit
   reproducibility across machines has **not** been established.

Two same-source native runs matched for 14/15 archives; libcrypto differed.
OpenSSL's generated `crypto/buildinf.h` embeds a wall-clock build timestamp
(`built on: Fri Nov 13 10:59:41 2026 UTC` in the final run), which explains a
source of nondeterminism. A reproducible-release profile should pin
SOURCE_DATE_EPOCH and normalise source/build paths, then prove equality with
independent builds. That release qualification has not been performed here.

Recovery NDK archive: official `android-ndk-r30-linux.zip`, revision 30.0.16248370,
738633529 bytes; SHA-256
`753611f410d002cfcd3f3dc2ef49aad532089d3180b436c060a90bf0fcb64df2`.
This is a locally measured digest, not an independently verified publisher checksum.
