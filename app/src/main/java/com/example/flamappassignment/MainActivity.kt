package com.example.flamappassignment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION") // using Camera1 for quicker implementation
class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private val TAG = "MainActivity"

    // UI
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private lateinit var btnToggle: Button

    // Camera
    private var camera: Camera? = null
    private var previewWidth = 640
    private var previewHeight = 480

    // Background thread for handling preview callback processing
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    // Queue to hold latest NV21 frames (capacity 2 to avoid memory buildup)
    private val frameQueue = ArrayBlockingQueue<ByteArray>(2)

    // toggle whether we "process" frames (placeholder for native call)
    private val processing = AtomicBoolean(false)

    companion object {
        private const val REQ_CAMERA = 1001

        init {
            // Load native library (make sure app/CMakeLists.txt built native-lib)
            System.loadLibrary("native-lib")
        }
    }

    // JNI function declaration (implemented in native-lib.cpp)
    external fun stringFromJNI(): String
    // (Later we will add nativeProcessFrame(...) when native processing is ready)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the XML layout which contains TextureView, status TextView and Button.
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        statusText = findViewById(R.id.statusText)
        btnToggle = findViewById(R.id.btnToggleProcessing)

        textureView.surfaceTextureListener = this

        btnToggle.setOnClickListener {
            val now = processing.get()
            processing.set(!now)
            btnToggle.text = if (!now) "Stop" else "Start"
            statusText.text = if (!now) "Processing ON" else "Processing OFF"
        }

        // Start background worker thread
        workerThread = HandlerThread("FrameWorker")
        workerThread?.start()
        workerHandler = Handler(workerThread!!.looper)

        // Quick JNI test
        try {
            val msg = stringFromJNI()
            Log.d("JNI", "Message: $msg")
        } catch (ex: UnsatisfiedLinkError) {
            Log.e("JNI", "Native lib not loaded: ${ex.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        } else {
            // permission already granted — if TextureView already available, open camera
            if (textureView.isAvailable) openCameraAndStartPreview(textureView.surfaceTexture!!)
        }
    }

    override fun onPause() {
        stopCamera()
        super.onPause()
    }

    override fun onDestroy() {
        workerThread?.quitSafely()
        workerThread?.join()
        super.onDestroy()
    }

    // Permission result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (textureView.isAvailable) openCameraAndStartPreview(textureView.surfaceTexture!!)
            } else {
                statusText.text = "Camera permission required"
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // TextureView callbacks
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        // when available, open camera
        openCameraAndStartPreview(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { /* no-op */ }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { stopCamera(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { /* no-op */ }

    // Open camera and start preview with NV21 preview callback
    private fun openCameraAndStartPreview(surface: SurfaceTexture) {
        try {
            camera = Camera.open()
            val params = camera!!.parameters

            // choose supported preview size closest to 640x480
            val sizes = params.supportedPreviewSizes
            val chosen = sizes.minByOrNull { Math.abs(it.width - previewWidth) + Math.abs(it.height - previewHeight) }
            chosen?.let {
                previewWidth = it.width
                previewHeight = it.height
                params.setPreviewSize(previewWidth, previewHeight)
            }

            params.previewFormat = ImageFormat.NV21
            camera!!.parameters = params

            // attach preview texture
            camera!!.setPreviewTexture(surface)

            // set callback buffer to reduce allocations
            val frameSize = previewWidth * previewHeight * 3 / 2
            val buffer = ByteArray(frameSize)
            camera!!.addCallbackBuffer(buffer)

            camera!!.setPreviewCallbackWithBuffer { data, _camera ->
                // Copy incoming data into a new byte array to avoid corruption from reused buffer
                val copy = ByteArray(data.size)
                System.arraycopy(data, 0, copy, 0, data.size)

                // offer to queue (drop if full)
                frameQueue.offer(copy)

                // re-add buffer for next frame to reduce GC churn
                _camera.addCallbackBuffer(buffer)

                // if processing enabled, handle one frame on worker thread (we won't block UI)
                if (processing.get()) {
                    workerHandler?.post {
                        processFrame() // processes latest frame if any
                    }
                }
            }

            camera!!.startPreview()
            statusText.text = "Preview started: ${previewWidth}x${previewHeight}"
            Log.i(TAG, "Camera preview started (${previewWidth}x${previewHeight})")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to open/start camera: ${ex.message}", ex)
            statusText.text = "Camera error: ${ex.message}"
            stopCamera()
        }
    }

    // Stop and release camera
    private fun stopCamera() {
        try {
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
            statusText.text = "Camera stopped"
            frameQueue.clear()
        } catch (ex: Exception) {
            Log.w(TAG, "Error stopping camera: ${ex.message}")
        }
    }

    // Process latest frame from queue (placeholder for calling native)
    private fun processFrame() {
        // take latest frame (drain older frames)
        var latest: ByteArray? = null
        while (true) {
            val f = frameQueue.poll() ?: break
            latest = f
        }
        latest?.let { nv21 ->
            // Placeholder: compute a quick lightweight FPS and log size
            Log.d(TAG, "Processing frame bytes=${nv21.size} w=$previewWidth h=$previewHeight")
            // TODO: call nativeProcessFrame(nv21, previewWidth, previewHeight) once JNI is ready
        }
    }
}
