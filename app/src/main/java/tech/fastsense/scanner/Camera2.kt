package tech.fastsense.scanner

import android.content.Context
import androidx.annotation.RequiresApi

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log


import android.Manifest
import android.content.pm.PackageManager

import androidx.core.content.ContextCompat
import java.io.File

import android.util.Base64

import android.graphics.*
import java.io.ByteArrayOutputStream

import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.media.MediaRecorder

import android.media.CamcorderProfile
import android.os.*

import java.lang.Exception
import android.os.HandlerThread
import java.lang.IllegalStateException


class CameraService(context: Context, videoConfig: VideoConfig,
                    cameraManager: CameraManager, cameraID: String,
                    imageView: TextureView
) {

    private lateinit var outputDirectory: File


    private var context: Context = context

    private var videoConfig: VideoConfig = videoConfig

    private val base64DefString: String = ""

    private var mCameraID: String = cameraID
    private var mCameraManager: CameraManager = cameraManager
    private var mCameraDevice: CameraDevice? = null
    private lateinit var mCaptureSession: CameraCaptureSession

    private var mImageView: TextureView = imageView

    private val LOG_TAG = "myLogs"

    private lateinit var builder: CaptureRequest.Builder

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    private var mMediaRecorder1: MediaRecorder? = null

    private var mMediaRecorder2: MediaRecorder? = null

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread?.start()
        mBackgroundHandler = mBackgroundThread?.getLooper()?.let { Handler(it) }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

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

    fun setShutterSpeedIso() {
        createCameraPreviewSession(false)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getPreviewImage(): String {

        if (mImageView.surfaceTexture == null) {
            Log.i(LOG_TAG, "error! getPreviewImage surfaceTexture = null")
            return base64DefString
        }


        val s: String
        val view = mImageView

        s = if (view.bitmap == null) {
            Log.i(LOG_TAG, "error! getPreviewImage view.bitmap = null")
            base64DefString
        } else {
            val b: Bitmap = getResizedBitmap(
                view.bitmap!!,
                videoConfig.previewWidth,
                videoConfig.previewHeight
            )

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

    @RequiresApi(Build.VERSION_CODES.S)
    fun startRecordVideo(file_name: String) {
        mMediaRecorder1 = setUpMediaRecorder("$file_name--4K", CamcorderProfile.QUALITY_2160P)
        mMediaRecorder2 = setUpMediaRecorder("$file_name--low", CamcorderProfile.QUALITY_720P)

        createCameraPreviewSession(true)
        mMediaRecorder1?.start()
        mMediaRecorder2?.start()
    }

    fun stopRecordVideo() {
        try {
            mCaptureSession.stopRepeating()
            mCaptureSession.abortCaptures()
            mCaptureSession.close()

            mMediaRecorder1!!.stop()
            mMediaRecorder1!!.release()

            mMediaRecorder2!!.stop()
            mMediaRecorder2!!.release()
            createCameraPreviewSession(false)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    fun isOpen(): Boolean {
        return mCameraDevice != null
    }

    private val mCameraCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                Log.i(LOG_TAG, "Open camera  with id:" + mCameraDevice!!.id)
                createCameraPreviewSession(false)
            }

            override fun onDisconnected(camera: CameraDevice) {
                mCameraDevice!!.close()
                Log.i(LOG_TAG, "disconnect camera  with id:" + mCameraDevice!!.id)
                mCameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.i(LOG_TAG, "error! camera id:" + camera.id + " error:" + error)
            }
        }

    fun openCamera() {
        try {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) === PackageManager.PERMISSION_GRANTED) {
                println("before mCameraManager.openCamera")
                outputDirectory = getOutputDirectory()
                mCameraManager.openCamera(mCameraID, mCameraCallback, null)
            }
        } catch (e: CameraAccessException) {
            Log.i(LOG_TAG, e.message!!)
        }
    }

    private fun createCameraPreviewSession(record_video: Boolean) {
        val texture: SurfaceTexture? = mImageView.surfaceTexture
        println("createCameraPreviewSession 0 ${mImageView} ${texture}")
//        return
        texture?.setDefaultBufferSize(3840,2160);
        val surface = Surface(texture)
        println("createCameraPreviewSession 1")
//        return
        try {
            builder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            println("createCameraPreviewSession 2")
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, videoConfig.exposure)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, videoConfig.iso)

            if (record_video) {
                val recorderSurface1 = mMediaRecorder1?.surface
                val recorderSurface2 = mMediaRecorder2?.surface
                if (recorderSurface1 != null) {
                    builder.addTarget(recorderSurface1)
                }
                if (recorderSurface2 != null) {
                    builder.addTarget(recorderSurface2)
                }
                mCameraDevice!!.createCaptureSession(
                    listOf(surface, mMediaRecorder1?.surface, mMediaRecorder2?.surface),
                    object : CameraCaptureSession.StateCallback() {
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
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, mBackgroundHandler
                )
            } else {
                mCameraDevice!!.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
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
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, mBackgroundHandler
                )
            }



        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            (context as MainActivity).baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = (context as MainActivity).externalMediaDirs.firstOrNull()?.let {
            File(it, (context as MainActivity).resources.getString(R.string.app_name)).apply { mkdirs() } }

        Log.v("AAA", "@@@ $mediaDir")
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else (context as MainActivity).filesDir
    }



    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // return 0 on ok, -1 on error
    @RequiresApi(Build.VERSION_CODES.S)
    private fun setUpMediaRecorder(file_name: String, p: Int): MediaRecorder {

        val profile = CamcorderProfile.get(p)

        val mMediaRecorder = MediaRecorder()
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate)


        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder.setVideoFrameRate(30)
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)

        mMediaRecorder.setOrientationHint(90);
        val mCurrentFile = File(outputDirectory, "$file_name.mp4")

        mMediaRecorder.setOutputFile(mCurrentFile.absolutePath)


        Log.i(LOG_TAG, mCurrentFile.absolutePath)


        try {
            mMediaRecorder.prepare()
            Log.i(LOG_TAG, "mMediaRecorder started OK")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(LOG_TAG, "mMediaRecorder failed to start")
        }
        return mMediaRecorder
    }

}