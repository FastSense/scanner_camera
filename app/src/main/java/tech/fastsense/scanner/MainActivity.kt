package tech.fastsense.scanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.os.CountDownTimer
import java.util.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo

import androidx.annotation.RequiresApi

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture

import androidx.core.content.ContextCompat
import android.view.TextureView
import java.text.SimpleDateFormat


class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private var counter: Int = 0
    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    private lateinit var myTextureView: TextureView

    private lateinit var videoConfig: VideoConfig

    private var startTimeMs: Long = System.currentTimeMillis()


    private lateinit var mCameraManager: CameraManager
    private var cameraReady = false

    private var myCamera: CameraService? = null

    private val LOG_TAG = "myLogs"

    private var recording_video: Boolean = false

    @SuppressLint("SourceLockedOrientationActivity")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoConfig = VideoConfig(getSharedPreferences("videoConfig", MODE_PRIVATE))
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setupViews()
//        setupNetwork() // now we setup network after camera is ready
        setupTimer()


    }

    @RequiresApi(Build.VERSION_CODES.S)
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



//        button.setOnClickListener {
//            if (!recording_video) {
//                myCamera?.startRecordVideo("2110")
//                recording_video = true
//            } else {
//                myCamera?.stopRecordVideo()
//                recording_video = false
//            }
//        }
    }

    fun setupNetwork() {
        netIff = NetworkInterface(this,
                                getString(R.string.http_server_ip),
                                getString(R.string.http_port),
                                getString(R.string.socket_server_ip),
                                getString(R.string.s_port),
                                getString(R.string.phone_name),
                                getString(R.string.cam_pose), videoConfig)
        netIff.init()
    }

    fun setupTimer() {
        val sdf = SimpleDateFormat("dd_hh_mm_ss")

        pingTimer = object : CountDownTimer(500000, (1/videoConfig.previewFps.toFloat() * 1000).toLong()) {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onTick(millisUntilFinished: Long) {
                if (cameraReady) {
                    var currentTimeMs: Long = System.currentTimeMillis()

                    var socketConnectionState: String
                    socketConnectionState = if (netIff.getConnectionStatus() == true) "State connected.\n" else "State disconnected.\n"

                    "$socketConnectionState Time from start: ${(currentTimeMs - startTimeMs)/1000} sec".also { textView.text = it }

                    val cameraState: String = if (recording_video) "recording" else "ready"
                    netIff.sendStatus(cameraState, (currentTimeMs - startTimeMs)/1000, myCamera!!.getPreviewImage())

                    if (counter % 5 == 0)
                        netIff.postPingRequest()

                    val hostCmd = netIff.newCommand()

                    when (hostCmd.cmd) {
                        CmdName.SetConfig -> {
                            Log.i(LOG_TAG, "setShutterSpeed")
                            myCamera!!.setShutterSpeedIso()
                        }
                        CmdName.StartVideo -> {
                            Log.i(LOG_TAG, "Start Video Record")
                            val scanId = hostCmd.param
                            val phone_name = getString(R.string.phone_name)
                            val side = getString(R.string.cam_pose)
                            val currentDate = sdf.format(Date())
                            println(" C DATE is  "+currentDate)
                            myCamera!!.startRecordVideo("${side}_${currentDate}_${scanId}")
                            recording_video = true
                            startTimeMs = System.currentTimeMillis()
                        }
                        CmdName.StopVideo -> {
                            Log.i(LOG_TAG, "Stop Video Record")
                            myCamera!!.stopRecordVideo()
                            recording_video = false
                        }

                    }

                }
                ++counter

            }
            override fun onFinish() {
                this.start() //start again the CountDownTimer
            }
        }

        pingTimer.start()
    }


    // camera2

    fun setupCamera() {

        Log.d(LOG_TAG, "Запрашиваем разрешение")
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            ||
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                ), 1
            )
        }



        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {

            // Получение списка камер с устройства
            for (cameraID in mCameraManager.cameraIdList) {
                Log.i(LOG_TAG, "cameraID: $cameraID")
                val id = cameraID.toInt()

                // создаем обработчик для камеры
                if (id == 0) {
                    Log.i(LOG_TAG, "Creating myCamera cameraID=: $cameraID")
                    myCamera = CameraService(this, videoConfig, mCameraManager, cameraID, myTextureView)
                    setupNetwork()
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(LOG_TAG, e.message!!)
            e.printStackTrace()
        }
    }


    // end camera2

}

