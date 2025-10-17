project(wav2vec2)

cmake_minimum_required(VERSION 3.10)

# 检查ANDROID_ABI
if(NOT DEFINED ANDROID_ABI)
    message(FATAL_ERROR "ANDROID_ABI not defined!")
endif()
message(STATUS "Building for ANDROID_ABI: ${ANDROID_ABI}")

# 设置ncnn路径
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20241226-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
message(STATUS "Looking for ncnn in: ${ncnn_DIR}")

if(NOT EXISTS ${ncnn_DIR})
    message(FATAL_ERROR "ncnn directory not found: ${ncnn_DIR}")
endif()

find_package(ncnn REQUIRED)

# 添加wav2vec2源文件
set(WAV2VEC2_SOURCES
    wav2vec2_jni.cpp
    vulkan_manager.cpp
    force_aligner.cpp
    phoneme/phoneme_sets.cpp
    phoneme/liaison_rules.cpp
    phoneme/phoneme_analyzer.cpp
    phoneme/phoneme_jni.cpp
    phoneme/token_mapper.cpp
)


# 创建wav2vec2共享库
add_library(wav2vec2 SHARED
    ${WAV2VEC2_SOURCES}
)

# 设置包含目录 - 修正包含路径
target_include_directories(wav2vec2 PRIVATE
    ${CMAKE_SOURCE_DIR}/ncnn-20241226-android-vulkan/${ANDROID_ABI}/include/ncnn
    ${CMAKE_SOURCE_DIR}  # 添加当前目录到包含路径
)


# 链接库
target_link_libraries(wav2vec2
    ncnn
    android
    log
    vulkan
)

