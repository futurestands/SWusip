cmake_minimum_required(VERSION 3.18...4.0)

project(baresip)

add_link_options("LINKER:--build-id=none" "-Wl,-z,max-page-size=16384")

set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../distribution)

# Reject missing/empty inputs at configuration time. Symbol and ABI validation
# is performed by scripts/verify_native.py before packaging.
file(GLOB_RECURSE native_archives "${distribution_DIR}/*/lib/${ANDROID_ABI}/*.a")
foreach(archive IN LISTS native_archives)
    file(SIZE "${archive}" archive_size)
    if(archive_size LESS_EQUAL 8)
        message(FATAL_ERROR "Empty native archive: ${archive}. Build the genuine engine using scripts/build-native.sh")
    endif()
endforeach()

add_library(lib_crypto STATIC IMPORTED)
set_target_properties(lib_crypto PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/openssl/lib/${ANDROID_ABI}/libcrypto.a)

add_library(lib_ssl STATIC IMPORTED)
set_target_properties(lib_ssl PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/openssl/lib/${ANDROID_ABI}/libssl.a)

add_library(lib_re STATIC IMPORTED)
set_target_properties(lib_re PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/re/lib/${ANDROID_ABI}/libre.a)

add_library(lib_opus STATIC IMPORTED)
set_target_properties(lib_opus PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/opus/lib/${ANDROID_ABI}/libopus.a)

add_library(lib_g722 STATIC IMPORTED)
set_target_properties(lib_g722 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g722/lib/${ANDROID_ABI}/libg722.a)

add_library(lib_g722_1 STATIC IMPORTED)
set_target_properties(lib_g722_1 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g7221/lib/${ANDROID_ABI}/libg722_1.a)

add_library(lib_g729 STATIC IMPORTED)
set_target_properties(lib_g729 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/g729/lib/${ANDROID_ABI}/libbcg729.a)

add_library(lib_ilbc STATIC IMPORTED)
set_target_properties(lib_ilbc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/ilbc/lib/${ANDROID_ABI}/libilbc.a)

add_library(lib_codec2 STATIC IMPORTED)
set_target_properties(lib_codec2 PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/codec2/lib/${ANDROID_ABI}/libcodec2.a)

add_library(lib_amrnb STATIC IMPORTED)
set_target_properties(lib_amrnb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrnb.a)

add_library(lib_amrwb STATIC IMPORTED)
set_target_properties(lib_amrwb PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwb.a)

add_library(lib_amrwbenc STATIC IMPORTED)
set_target_properties(lib_amrwbenc PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/amr/lib/${ANDROID_ABI}/libamrwbenc.a)

add_library(lib_zrtpcppcore STATIC IMPORTED)
set_target_properties(lib_zrtpcppcore PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/gzrtp/lib/${ANDROID_ABI}/libzrtpcppcore.a)

add_library(lib_sndfile STATIC IMPORTED)
set_target_properties(lib_sndfile PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/sndfile/lib/${ANDROID_ABI}/libsndfile.a)

add_library(lib_baresip STATIC IMPORTED)
set_target_properties(lib_baresip PROPERTIES IMPORTED_LOCATION
        ${distribution_DIR}/baresip/lib/${ANDROID_ABI}/libbaresip.a)

foreach(dependency IN ITEMS crypto ssl re opus g722 g722_1 g729 ilbc codec2 amrnb amrwb amrwbenc zrtpcppcore sndfile baresip)
    get_target_property(archive lib_${dependency} IMPORTED_LOCATION)
    if(NOT EXISTS "${archive}")
        message(FATAL_ERROR "Missing native archive: ${archive}. Run scripts/build-native.sh ${ANDROID_ABI}")
    endif()
    execute_process(COMMAND "${CMAKE_AR}" t "${archive}"
        RESULT_VARIABLE archive_status OUTPUT_VARIABLE archive_members ERROR_VARIABLE archive_error)
    if(NOT archive_status EQUAL 0 OR archive_members STREQUAL "")
        message(FATAL_ERROR "Invalid/empty native archive: ${archive}: ${archive_error}")
    endif()
endforeach()

foreach(dependency IN ITEMS re baresip)
    get_target_property(archive lib_${dependency} IMPORTED_LOCATION)
    execute_process(COMMAND "${CMAKE_NM}" --defined-only "${archive}"
        RESULT_VARIABLE symbol_status OUTPUT_VARIABLE symbols ERROR_VARIABLE symbol_error)
    if(NOT symbol_status EQUAL 0)
        message(FATAL_ERROR "Cannot verify native symbols: ${archive}: ${symbol_error}")
    endif()
    if(dependency STREQUAL "re")
        set(required_symbols libre_init)
    else()
        set(required_symbols baresip_init ua_init ua_register call_connect call_transfer)
    endif()
    foreach(symbol IN LISTS required_symbols)
        if(NOT symbols MATCHES "[ \t][Tt][ \t]+${symbol}[\r\n]")
            message(FATAL_ERROR "Missing genuine implementation ${symbol} in ${archive}")
        endif()
    endforeach()
endforeach()

add_library(baresip SHARED ${CMAKE_SOURCE_DIR}/baresip.c)
# JNI is C, but inherited ZRTP/AMR archives contain C++. Let the NDK supply
# the selected c++_shared runtime and have AGP package it with the APK.
set_target_properties(baresip PROPERTIES LINKER_LANGUAGE CXX)

target_include_directories(baresip PRIVATE
        ${distribution_DIR}/openssl/include
        ${distribution_DIR}/re/include
        ${distribution_DIR}/baresip/include)

add_definitions(-DHAVE_PTHREAD)

target_link_libraries(
        baresip
        android
        aaudio
        lib_baresip
        lib_re
        lib_ssl
        lib_crypto
        lib_opus
        lib_g722
        lib_g722_1
        lib_g729
        lib_ilbc
        lib_codec2
        lib_amrnb
        lib_amrwb
        lib_amrwbenc
        lib_zrtpcppcore
        lib_sndfile
        z
        log)
