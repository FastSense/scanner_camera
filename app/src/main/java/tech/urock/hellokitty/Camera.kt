package tech.urock.hellokitty

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

import android.util.Base64

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.*
import java.io.ByteArrayOutputStream

import android.media.Image
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import java.text.SimpleDateFormat
import java.util.*



class Camera(context: Context, videoConfig: VideoConfig) {

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var activeRecording: ActiveRecording

    private var context: Context = context

    private var videoConfig: VideoConfig = videoConfig


    private val base64DefString: String = "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"


    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    fun getPreviewImage(): String {

        var s: String
        val view = (context as MainActivity).viewFinder

        if (view.bitmap == null) {
            s = base64DefString
        } else {
            var b: Bitmap = getResizedBitmap(
                view.bitmap!!,
                videoConfig.preview_width,
                videoConfig.preview_height
            )

            val baos = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, videoConfig.preview_quality, baos)
            val imageBytes: ByteArray = baos.toByteArray()
            s = Base64.encodeToString(imageBytes, Base64.DEFAULT)
        }
        return s

    }

    fun startRecordVideo() {
        // Create MediaStoreOutputOptions for our recorder
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()


// 2. Configure Recorder and Start recording to the mediaStoreOutput.

        activeRecording = videoCapture.output.prepareRecording(context, mediaStoreOutput)
            .start()


    }

    fun stopRecordVideo() {
        activeRecording.stop()
    }

    fun setup() {
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                (context as MainActivity), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)


        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val surfacePr: Preview.SurfaceProvider = (context as MainActivity).viewFinder.surfaceProvider

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfacePr)
                }


            // video recording settings

            val qualitySelector = QualitySelector
                .firstTry(QualitySelector.QUALITY_UHD)
                .thenTry(QualitySelector.QUALITY_FHD)
                .thenTry(QualitySelector.QUALITY_HD)
                .finallyTry(QualitySelector.QUALITY_SD,
                    QualitySelector.FALLBACK_STRATEGY_LOWER)

            val recorder = Recorder.Builder()
                .setExecutor(cameraExecutor).setQualitySelector(qualitySelector)
                .build()


            videoCapture = VideoCapture.withOutput(recorder)


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    (context as MainActivity), cameraSelector, preview, videoCapture)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            (context as MainActivity).baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = (context as MainActivity).externalMediaDirs.firstOrNull()?.let {
//            File(it, (context as MainActivity).resources.getString(R.string.app_name)).apply { mkdirs() } }
        File(it, "Camera Control App").apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else (context as MainActivity).filesDir
    }

    fun onDestroy() {
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText((context as MainActivity),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                (context as MainActivity).finish()
            }
        }
    }
}