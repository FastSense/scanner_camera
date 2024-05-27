package tech.fastsense.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

class CameraService(
    private var context: Context, private var videoConfig: VideoConfig,
    cameraManager: CameraManager, cameraID: String,
    imageView: TextureView
) {

    private lateinit var outputDirectory: File

    private var requestQueue: RequestQueue = Volley.newRequestQueue(context)

    private val base64DefString: String = ""

    private var mCameraID: String = cameraID
    private var mCameraManager: CameraManager = cameraManager
    private var mCameraDevice: CameraDevice? = null
    private lateinit var mCaptureSession: CameraCaptureSession

    private var mImageView: TextureView = imageView

    private lateinit var builder: CaptureRequest.Builder

    private var mBackgroundHandler: Handler? = null

    private var mMediaRecorder: MediaRecorder? = null

    private val serverURI: String
        get() {
            val p = context.getSharedPreferences("network", AppCompatActivity.MODE_PRIVATE)
            return p.getString("serverURI", "http://192.168.123.123:80")!!
        }

    private val cameraPose: String
        get() {
            val p = context.getSharedPreferences("common", AppCompatActivity.MODE_PRIVATE)
            return p.getString("cameraPose", "left")!!
        }

    companion object {
        const val LOG_TAG = "Camera2"
    }

    private fun log(msg: String) {
        Log.i(LOG_TAG, "@@@ $msg")
        Firebase.crashlytics.log("$LOG_TAG: $msg")
    }

    fun setShutterSpeedIso() {
        createCameraPreviewSession(false)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getPreviewImage(): String {
        if (mImageView.surfaceTexture == null) {
            log("error! getPreviewImage surfaceTexture = null")
            return base64DefString
        }

        val s: String
        val view = mImageView

        s = if (view.bitmap == null) {
            log("error! getPreviewImage view.bitmap = null")
            base64DefString
        } else {
            val b: Bitmap = view.getBitmap(videoConfig.previewWidth, videoConfig.previewHeight)!!

            val baos = ByteArrayOutputStream()
            b.compress(
                Bitmap.CompressFormat.JPEG,
                videoConfig.previewQuality,
                baos
            )
            val imageBytes: ByteArray = baos.toByteArray()
            Base64.encodeToString(imageBytes, Base64.DEFAULT)
        }
        return s

    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun takePhoto() {
        thread {
            val b: Bitmap = if (videoConfig.fullResolution == FullResolution.FOURK) {
                mImageView.getBitmap(2160, 3840)!!
            } else {
                mImageView.getBitmap(1080, 1920)!!
            }

            val b1 = drawStringOnBitmap(
                b,
                arrayOf(
                    "iso: ${videoConfig.iso}",
                    "exposure: ${1e9 / videoConfig.exposure}",
                    "focus: ${if (videoConfig.focusMode == "auto") "auto" else videoConfig.focusDistance}",
                ),
                Point(100, 100)
            )

            val r = object : VolleyMultipartRequest(
                Method.POST,
                "${serverURI}/api/v0/tools/uploadPhoto?side=${cameraPose}",
                {},
                {}
            ) {
                override fun getByteData(): MutableMap<String, DataPart> {
                    val baos = ByteArrayOutputStream()
                    b1.compress(Bitmap.CompressFormat.JPEG, 90, baos)

                    return mutableMapOf(
                        "in_file" to DataPart(
                            "${System.currentTimeMillis()}.jpg",
                            baos.toByteArray(),
                            "image/jpeg"
                        )
                    )
                }
            }
            requestQueue.add(r)
        }
    }

    private fun drawStringOnBitmap(
        src: Bitmap,
        string: Array<String>,
        location: Point,
    ): Bitmap {
        val result = if (videoConfig.fullResolution == FullResolution.FOURK) {
            Bitmap.createBitmap(2160, 3840, src.config)
        } else {
            Bitmap.createBitmap(1080, 1920, src.config)
        }
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint()
        paint.color = 0xFF00FF
        paint.alpha = 255
        paint.textSize = 96F
        paint.isAntiAlias = true
        for ((i, row) in string.withIndex()) {
            canvas.drawText(
                row,
                location.x.toFloat(),
                location.y.toFloat() + i * 96F * 1.2F,
                paint
            )
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun startRecordVideo(fileName: String) {
        if (setUpMediaRecorder(fileName) == 0) {
            createCameraPreviewSession(true)
            mMediaRecorder?.start()
        }
    }

    fun stopRecordVideo() {
        try {
            mCaptureSession.stopRepeating()
            mCaptureSession.abortCaptures()
            mCaptureSession.close()

            mMediaRecorder!!.stop()
            mMediaRecorder!!.release()
            createCameraPreviewSession(false)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    private val mCameraCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                log("Open camera  with id:" + mCameraDevice!!.id)
                createCameraPreviewSession(false)
            }

            override fun onDisconnected(camera: CameraDevice) {
                mCameraDevice!!.close()
                log("disconnect camera  with id:" + mCameraDevice!!.id)
                mCameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                log("error! camera id:" + camera.id + " error:" + error)
            }
        }

    fun openCamera() {
        try {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                log("before mCameraManager.openCamera")
                outputDirectory = getOutputDirectory()
                mCameraManager.openCamera(mCameraID, mCameraCallback, null)
            }
        } catch (e: CameraAccessException) {
            log(e.message!!)
        }
    }

    private fun createCameraPreviewSession(recordVideo: Boolean) {
        val texture: SurfaceTexture? = mImageView.surfaceTexture
        log("createCameraPreviewSession 0 $mImageView $texture")

        if (videoConfig.fullResolution == FullResolution.FOURK) {
            texture?.setDefaultBufferSize(3840, 2160)
        } else {
            texture?.setDefaultBufferSize(1920, 1080)
        }

        val surface = Surface(texture)
        log("createCameraPreviewSession 1")

        try {
            builder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            log("createCameraPreviewSession 2")
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, videoConfig.exposure)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, videoConfig.iso)

            if ("manual" == videoConfig.focusMode) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 1f / videoConfig.focusDistance)
            } else {
                // ...
            }

            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mCaptureSession = session
                    try {
                        mCaptureSession.setRepeatingRequest(
                            builder.build(),
                            null,
                            mBackgroundHandler
                        )
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }

            if (recordVideo) {
                val recorderSurface = mMediaRecorder?.surface
                if (recorderSurface != null) {
                    builder.addTarget(recorderSurface)
                }
                mCameraDevice!!.createCaptureSession(
                    listOf(surface, mMediaRecorder?.surface),
                    callback,
                    null // Использование null, чтобы использовать looper текущего потока (Main Thread)
                )
            } else {
                mCameraDevice!!.createCaptureSession(
                    listOf(surface),
                    callback,
                    null // Использование null, чтобы использовать looper текущего потока (Main Thread)
                )
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private fun getOutputDirectory(): File {
        val mediaDir = (context as MainActivity).externalMediaDirs.firstOrNull()?.let {
            File(
                it,
                (context as MainActivity).resources.getString(R.string.app_name)
            ).apply { mkdirs() }
        }

        Log.v(LOG_TAG, "@@@ $mediaDir")
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else (context as MainActivity).filesDir
    }

    // return 0 on ok, -1 on error
    @RequiresApi(Build.VERSION_CODES.S)
    private fun setUpMediaRecorder(fileName: String): Int {

        val profile =
            if (videoConfig.fullResolution == FullResolution.FOURK) {
                CamcorderProfile.get(CamcorderProfile.QUALITY_2160P)
            } else {
                CamcorderProfile.get(CamcorderProfile.QUALITY_1080P)
            }

        mMediaRecorder = MediaRecorder()
        mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        mMediaRecorder?.setVideoEncodingBitRate(profile.videoBitRate)

        mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder?.setVideoFrameRate(profile.videoFrameRate)
        mMediaRecorder?.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)

        mMediaRecorder?.setOrientationHint(90)
        val mCurrentFile = File(outputDirectory, "$fileName.mp4")

        mMediaRecorder?.setOutputFile(mCurrentFile.absolutePath)

        log("File path: ${mCurrentFile.absolutePath}")

        try {
            mMediaRecorder?.prepare()
            log("mMediaRecorder started OK")
        } catch (e: Exception) {
            e.printStackTrace()
            log("mMediaRecorder failed to start")
            return -1
        }
        return 0
    }
}
