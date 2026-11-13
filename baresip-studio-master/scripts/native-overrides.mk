# Compatibility with CMake 4.x for inherited ZRTPCPP's 3.4 minimum.
# This enables current policies without changing upstream source or library versions.
# Let the NDK toolchain select absolute compiler paths. Upstream's relative
# wrapper names trigger cache invalidation on the second ZRTP configure pass.
CMAKE_ANDROID_FLAGS := $(filter-out -DCMAKE_C_COMPILER=% -DCMAKE_CXX_COMPILER=%,$(CMAKE_ANDROID_FLAGS)) -DCMAKE_POLICY_VERSION_MINIMUM=3.5

# Parallelise only each codec/OpenSSL sub-build, keeping top-level dependencies
# sequential so Libre cannot configure against an unfinished OpenSSL build.
amr g7221 ilbc openssl opus: export MAKEFLAGS := -j$(CPU_COUNT)
