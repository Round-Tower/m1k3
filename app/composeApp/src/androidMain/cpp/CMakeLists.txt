cmake_minimum_required(VERSION 3.22.1)
project(ma VERSION 1.0 LANGUAGES C CXX)

# ============================================================================
# llama.cpp - CPU inference for GGUF models
# ============================================================================
# Setup (one-time):
#   cd app/composeApp/src/androidMain/cpp
#   git submodule add https://github.com/ggerganov/llama.cpp.git llama.cpp
#   cd llama.cpp && git checkout <b8637+ commit hash>
#
# Pinned at b8637+ for Gemma 4 support (Llamatik was stuck at b7815, Jan 2025)
# ============================================================================

set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Android-optimised llama.cpp build (CPU only, no GPU, no tools)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)
set(GGML_METAL            OFF CACHE BOOL "" FORCE)
set(GGML_CUDA             OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN           OFF CACHE BOOL "" FORCE)
set(LLAMA_ANDROID         ON  CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

# ============================================================================
# Ma JNI bridge
# ============================================================================

add_library(ma SHARED ma_bridge.cpp)

target_include_directories(ma PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(ma
    llama
    android
    log
)

target_compile_features(ma PRIVATE cxx_std_17)

# Reduce .so size: strip debug symbols in release builds
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(ma PROPERTIES LINK_FLAGS "-Wl,--strip-all")
endif ()
