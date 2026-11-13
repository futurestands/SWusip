cmake_minimum_required(VERSION 3.18...4.0)

project(baresip)

add_link_options("LINKER:--build-id=none" "-Wl,-z,max-page-size=16384")

add_library(baresip SHARED
        ${CMAKE_SOURCE_DIR}/baresip.c)

add_definitions(-DHAVE_PTHREAD)

target_link_libraries(
        baresip
        android
        aaudio
        z
        log)
