cmake_minimum_required(VERSION 3.22)
project(magnusorgue CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe comes prebuilt through Gradle's prefab support (see app/build.gradle.kts).
find_package(oboe REQUIRED CONFIG)

add_library(magnusorgue SHARED
        jni_bridge.cpp
        AudioEngine.cpp
        Voice.cpp
        VoiceManager.cpp)

target_compile_options(magnusorgue PRIVATE -Wall -Wextra -O2)

target_link_libraries(magnusorgue
        oboe::oboe
        log)
