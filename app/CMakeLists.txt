cmake_minimum_required(VERSION 3.10.2)

project("flamappassignment")

# Path to the OpenCV Android SDK folder (change if you placed it elsewhere)
# We assume it's at <project-root>/OpenCV-android-sdk
set(OPENCV_ANDROID_DIR "${CMAKE_SOURCE_DIR}/../OpenCV-android-sdk")

# Add the native library (your cpp file)
add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

# If OpenCV SDK is present, import libopencv_java4 for the current ABI
if(EXISTS "${OPENCV_ANDROID_DIR}/sdk/native/jni/include")
    message(STATUS "OpenCV SDK found at ${OPENCV_ANDROID_DIR}")
    include_directories("${OPENCV_ANDROID_DIR}/sdk/native/jni/include")

    # imported shared library for libopencv_java4
    add_library(opencv_java4 SHARED IMPORTED)
    set_target_properties(opencv_java4 PROPERTIES
            IMPORTED_LOCATION "${OPENCV_ANDROID_DIR}/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so")
endif()

# Android log
find_library(log-lib log)

# Link libraries
if(TARGET opencv_java4)
    target_link_libraries(native-lib ${log-lib} opencv_java4)
else()
    target_link_libraries(native-lib ${log-lib})
endif()
