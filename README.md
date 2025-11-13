See http://opencv.org/platforms/android
# Flamapp Assignment – Android + JNI + OpenCV

This project is my submission for the Flamapp.AI Software Engineering Internship Assignment.

 Features Implemented

Android
- Camera preview using TextureView
- NV21 frame capture from Camera1 API
- Background thread for frame processing
- JNI bridge to native C++ code
- Base structure ready for OpenCV processing

Native (C++)
- JNI function `stringFromJNI()`
- C++ file in `app/src/main/cpp/native-lib.cpp`
- CMakeLists configured for building native library
- OpenCV SDK integrated inside project root

Folder Structure
app/src/main/java/.../MainActivity.kt
app/src/main/cpp/native-lib.cpp
app/CMakeLists.txt
OpenCV-android-sdk/

Setup Instructions

Android Studio Requirements
- Android Studio (Otter)
- NDK
- CMake

OpenCV Setup
1. Download `opencv-4.x.x-android-sdk.zip`
2. Extract → rename to `OpenCV-android-sdk`
3. Place inside project root:
/flamapp/OpenCV-android-sdk


Architecture Explanation

Frame Flow
1. Camera1 → NV21 preview frame
2. Frame pushed into queue
3. Worker thread processes frames
4. JNI bridge sends data to C++
5. Native layer will run OpenCV (when added)

JNI
- `System.loadLibrary("native-lib")`
- C++ function exposed to Kotlin

TypeScript/Web Part (Not Implemented)
The assignment also asks for a web viewer; can be added if needed.

Status
The project **builds successfully**, native code is linked, OpenCV integrated, and camera preview ready.

