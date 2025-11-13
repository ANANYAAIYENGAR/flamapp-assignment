#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>

#define LOG_TAG "native-lib"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// If OpenCV is available, include it.
#ifdef __has_include
#  if __has_include(<opencv2/opencv.hpp>)
#    include <opencv2/opencv.hpp>
#    define HAS_OPENCV 1
#  else
#    define HAS_OPENCV 0
#  endif
#else
#  define HAS_OPENCV 0
#endif

#if HAS_OPENCV
using namespace cv;
#endif

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_flamappassignment_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from C++ (native-lib with OpenCV: "
                        #if HAS_OPENCV
                        "yes"
                        #else
                        "no"
                        #endif
                        ")";
    return env->NewStringUTF(hello.c_str());
}

/*
 * nativeProcessFrame:
 *   Input: NV21 byte[] (preview data), width, height
 *   Output: RGBA byte[] (same width*height*4) after Canny edges (edges shown as white on black)
 *
 * JNI signature must match the package/class name:
 * Java_<package_with_underscores>_<Class>_nativeProcessFrame
 *
 * Here package = com.example.flamappassignment, class = MainActivity.
 */

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_flamappassignment_MainActivity_nativeProcessFrame(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray nv21Array,
        jint width,
        jint height) {

    if (nv21Array == nullptr || width == 0 || height == 0) {
        ALOGE("nativeProcessFrame: invalid args");
        return nullptr;
    }

    jbyte* nv21 = env->GetByteArrayElements(nv21Array, NULL);
    jsize nv21_len = env->GetArrayLength(nv21Array);

#if HAS_OPENCV
    try {
        // Create Mat from NV21 buffer (Y + VU)
        // NV21 layout: height + height/2 rows, single channel
        Mat yuv(height + height/2, width, CV_8UC1, reinterpret_cast<unsigned char*>(nv21));

        // Convert to RGBA
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // Convert to grayscale
        Mat gray;
        cvtColor(rgba, gray, COLOR_RGBA2GRAY);

        // Optionally resize for speed (commented out)
        // if (width > 640) resize(gray, gray, Size(), 0.5, 0.5);

        // Run Canny
        Mat edges;
        Canny(gray, edges, 50, 150);

        // Convert edges (single channel) to RGBA
        Mat edgesRGBA;
        cvtColor(edges, edgesRGBA, COLOR_GRAY2RGBA);

        // Ensure continuous memory and return byte array
        if (!edgesRGBA.isContinuous()) {
            edgesRGBA = edgesRGBA.clone();
        }

        int outSize = static_cast<int>(edgesRGBA.total() * edgesRGBA.elemSize()); // width*height*4
        jbyteArray outArray = env->NewByteArray(outSize);
        env->SetByteArrayRegion(outArray, 0, outSize, reinterpret_cast<const jbyte*>(edgesRGBA.data));

        env->ReleaseByteArrayElements(nv21Array, nv21, JNI_ABORT);
        return outArray;
    } catch (const cv::Exception& e) {
        ALOGE("OpenCV exception: %s", e.what());
        env->ReleaseByteArrayElements(nv21Array, nv21, JNI_ABORT);
        return nullptr;
    } catch (...) {
        ALOGE("Unknown native exception");
        env->ReleaseByteArrayElements(nv21Array, nv21, JNI_ABORT);
        return nullptr;
    }
#else
    // If OpenCV not available, fall back to a simple grayscale conversion (no edges)
    // This produces RGBA where R=G=B=Y and A=255.
    ALOGE("OpenCV not available at compile time; doing simple NV21->RGBA (no Canny).");

    int frameSize = width * height;
    int outSize = frameSize * 4;
    std::vector<unsigned char> outBuf(outSize);

    unsigned char* nv21_u = reinterpret_cast<unsigned char*>(nv21);
    // Y plane first (frameSize), then interleaved VU
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int yIndex = y * width + x;
            unsigned char Y = nv21_u[yIndex];
            // Fill RGBA with Y
            int outIdx = (yIndex) * 4;
            outBuf[outIdx + 0] = Y;
            outBuf[outIdx + 1] = Y;
            outBuf[outIdx + 2] = Y;
            outBuf[outIdx + 3] = 0xFF;
        }
    }

    jbyteArray outArray = env->NewByteArray(outSize);
    env->SetByteArrayRegion(outArray, 0, outSize, reinterpret_cast<const jbyte*>(outBuf.data()));
    env->ReleaseByteArrayElements(nv21Array, nv21, JNI_ABORT);
    return outArray;
#endif
}
