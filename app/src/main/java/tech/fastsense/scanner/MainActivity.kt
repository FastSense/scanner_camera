package tech.fastsense.scanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
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
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat


class MainActivity : AppCompatActivity() {
    private lateinit var chipRecStatus: Chip
    private lateinit var chipConnStatus: Chip
    private lateinit var chipCameraLoc: Chip
    private lateinit var fabSettings: FloatingActionButton


    private var counter: Int = 0
    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    private lateinit var myTextureView: TextureView

    private lateinit var videoConfig: VideoConfig

    private var startTimeMs: Long = System.currentTimeMillis()

    private lateinit var mCameraManager: CameraManager
    private var cameraReady = false

    private var myCamera: CameraService? = null

    private val TAG = "myLogs"

    private var recordingVideo: Boolean = false

    @SuppressLint("SourceLockedOrientationActivity")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoConfig = VideoConfig(getSharedPreferences("videoConfig", MODE_PRIVATE))
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setupViews()
        setupTimer()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setupViews() {
        setContentView(R.layout.activity_main)

        chipRecStatus = findViewById(R.id.chip_rec_status)
        chipConnStatus = findViewById(R.id.chip_conn_status)
        chipCameraLoc = findViewById(R.id.chip_cam_loc)
        fabSettings = findViewById(R.id.fab_settings)

        myTextureView = findViewById(R.id.textureView)

        myTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                setupCamera()
                Log.i(TAG, "Opening camera")
                myCamera?.openCamera()
                Log.i(TAG, "Camera ready")
                cameraReady = true
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    fun setupNetwork() {
        val networkPref = getSharedPreferences("network", MODE_PRIVATE)
        val commonPref = getSharedPreferences("common", MODE_PRIVATE)

        val serverURI = networkPref.getString("serverURI", "http://192.168.118.243:8000")!!
        val cameraName = commonPref.getString("cameraName", "scanner camera")!!
        val cameraPose = commonPref.getString("cameraPose", "left")!!

        netIff = NetworkInterface(serverURI, cameraName, cameraPose, videoConfig)
        netIff.init()
    }

    fun setupTimer() {
        pingTimer = object : CountDownTimer(500000, (1/videoConfig.previewFps.toFloat() * 1000).toLong()) {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onTick(millisUntilFinished: Long) {
                if (cameraReady) {
                    var currentTimeMs: Long = System.currentTimeMillis()

                    updateConnectionState()
                    updateRecordingState()

                    val cameraState: String = if (recordingVideo) "recording" else "ready"
                    netIff.sendStatus(cameraState, (currentTimeMs - startTimeMs) / 1000, myCamera!!.getPreviewImage())

                    if (counter % 5 == 0)
                        netIff.postPingRequest()

                    val hostCmd = netIff.newCommand()

                    when (hostCmd.cmd) {
                        CmdName.SetConfig -> {
                            Log.i(TAG, "setShutterSpeed")
                            myCamera!!.setShutterSpeedIso()
                        }
                        CmdName.StartVideo -> {
                            Log.i(TAG, "Start Video Record")
                            startRecordVideo(hostCmd.param)
                        }
                        CmdName.StopVideo -> {
                            Log.i(TAG, "Stop Video Record")
                            stopRecordVideo()
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

    @SuppressLint("SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.S)
    fun startRecordVideo (scanId: String) {
        val side = getString(R.string.cam_pose)
        val sdf = SimpleDateFormat("dd_hh_mm_ss")
        val currentDate = sdf.format(Date())

        if (!recordingVideo) {
            myCamera!!.startRecordVideo("${side}_${currentDate}_${scanId}")

            recordingVideo = true
            startTimeMs = System.currentTimeMillis()

            updateRecordingState()
        }
    }

    fun stopRecordVideo () {
        chipRecStatus.setText(R.string.rec_status_recording)
        myCamera!!.stopRecordVideo()
        recordingVideo = false

        updateRecordingState()
    }

    private fun updateRecordingState() {
        if (recordingVideo) {
            val sec = (System.currentTimeMillis() - startTimeMs) / 1000
            val mm = (sec / 60).toString().padStart(2, '0')
            val ss = (sec % 60).toString().padStart(2, '0')

            chipRecStatus.text = getString(R.string.rec_status_recording, "$mm:$ss")
            chipRecStatus.setChipIconResource(R.drawable.ic_baseline_radio_button_checked_24)
        } else {
            chipRecStatus.setText(R.string.rec_status_ready)
            chipRecStatus.setChipIconResource(R.drawable.ic_baseline_check_24)
        }
    }

    private fun updateConnectionState() {
        if (netIff.getConnectionStatus()!!) {
            chipConnStatus.setText(R.string.conn_status_connected)
            chipConnStatus.setChipIconResource(R.drawable.ic_baseline_link_24)
        } else {
            chipConnStatus.setText(R.string.conn_status_disconnected)
            chipConnStatus.setChipIconResource(R.drawable.ic_baseline_link_off_24)
        }
    }

    // camera2

    fun setupCamera() {

        Log.d(TAG, "Запрашиваем разрешение")
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
                Log.i(TAG, "cameraID: $cameraID")
                val id = cameraID.toInt()

                // создаем обработчик для камеры
                if (id == 0) {
                    Log.i(TAG, "Creating myCamera cameraID=: $cameraID")
                    myCamera = CameraService(this, videoConfig, mCameraManager, cameraID, myTextureView)
                    setupNetwork()
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message!!)
            e.printStackTrace()
        }
    }


    // end camera2

}

