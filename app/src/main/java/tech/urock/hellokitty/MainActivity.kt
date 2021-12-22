package tech.urock.hellokitty

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Button
import android.os.CountDownTimer
import java.util.*
import android.Manifest

import androidx.annotation.RequiresApi

import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.core.content.PackageManagerCompat
import androidx.core.content.PackageManagerCompat.LOG_TAG
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture

import androidx.core.content.ContextCompat
import android.view.TextureView




class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private var counter: Int = 0
    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    private lateinit var myTextureView: TextureView

    private var videoConfig: VideoConfig = VideoConfig(this)

    private var startTimeMs: Long = System.currentTimeMillis()

// camera2

    private lateinit var mCameraManager: CameraManager
    private var cameraReady = false

    private var myCamera: CameraService? = null
    private val CAMERA1 = 0
    private val CAMERA2 = 1
    private val LOG_TAG = "myLogs"
//    private var myCamera: CameraService = CameraService(this, videoConfig)
//end camera2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupNetwork()
        setupTimer()
    }

    fun setupViews() {
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
//        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

        myTextureView = findViewById(R.id.textureView)

        myTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                setupCamera()
                Log.i(LOG_TAG, "Opening camera")
                myCamera?.openCamera()
                Log.i(LOG_TAG, "Camera ready")
                cameraReady = true
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }


//        imageButton.setOnClickListener {
//            pingTimer.cancel()
//        }

        button.setOnClickListener {

        }
    }

    fun setupNetwork() {
        netIff = NetworkInterface(this,
                                getString(R.string.http_server_ip),
                                getString(R.string.http_port),
                                getString(R.string.socket_server_ip),
                                getString(R.string.s_port),
                                getString(R.string.phone_name),
                                getString(R.string.cam_pose), videoConfig, myCamera)
        netIff.init()
    }

    fun setupTimer() {
        pingTimer = object : CountDownTimer(500000, (1/videoConfig.preview_fps.toFloat() * 1000).toLong()) {
            override fun onTick(millisUntilFinished: Long) {
                var currentTimeMs: Long = System.currentTimeMillis()

                var socketConnectionState: String
                socketConnectionState = if (netIff.getConnectionStatus() == true) "State connected.\n" else "State disconnected.\n"

                "$socketConnectionState Time from start: ${(currentTimeMs - startTimeMs)/1000} sec".also { textView.text = it }

//                println("$currentTimeMs")
                if (cameraReady)
                    netIff.sendStatus((currentTimeMs - startTimeMs)/1000, myCamera!!.getPreviewImage())

                if (counter % 5 == 0)
                    netIff.postPingRequest()

                ++counter
            }
            override fun onFinish() {
                this.start(); //start again the CountDownTimer
            }
        }

        pingTimer.start()
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults:
//        IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        myCamera.onRequestPermissionsResult(requestCode, permissions, grantResults)
//    }

    // camera2

    fun setupCamera() {

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }

        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {

            // Получение списка камер с устройства
//            myCamera = arrayOfNulls(mCameraManager.cameraIdList.size)
            for (cameraID in mCameraManager.cameraIdList) {
                Log.i(LOG_TAG, "cameraID: $cameraID")
                val id = cameraID.toInt()

                // создаем обработчик для камеры
                if (id == 0) {
                    Log.i(LOG_TAG, "Creating myCamera cameraID=: $cameraID")
                    myCamera = CameraService(this, videoConfig, mCameraManager, cameraID, myTextureView)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(LOG_TAG, e.message!!)
            e.printStackTrace()
        }
    }


    // end camera2

}

