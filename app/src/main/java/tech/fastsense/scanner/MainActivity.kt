package tech.fastsense.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.ktx.setCustomKeys
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var chipRecStatus: Chip
    private lateinit var chipConnStatus: Chip
    private lateinit var chipCameraLoc: Chip
    private lateinit var fabSettings: FloatingActionButton

    private lateinit var cardSettings: CardView
    private lateinit var inputCameraName: EditText
    private lateinit var inputServerUri: EditText
    private lateinit var radioSide: RadioGroup
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    private lateinit var myTextureView: TextureView

    private lateinit var videoConfig: VideoConfig

    private var startTimeMs: Long = System.currentTimeMillis()

    private var lastTapMs: Long = System.currentTimeMillis()

    private lateinit var mCameraManager: CameraManager
    private var cameraReady = false

    private var myCamera: CameraService? = null

    private var recordingVideo: Boolean = false


    companion object {
        const val PERMISSIONS_REQUEST_CODE = 837
        const val TAG = "MainActivity"

        const val SCREEN_BRIGHTNESS_LOW = 0.00F
        const val SCREEN_BRIGHTNESS_MEDIUM = 0.50F
    }

    private fun log(msg: String) {
        Log.i(TAG, "@@@ $msg")
        Firebase.crashlytics.log("${CameraService.LOG_TAG}: $msg")
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoConfig = VideoConfig(getSharedPreferences("videoConfig", MODE_PRIVATE))
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setupTimer()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()

        requestPermissions {
            setupViews()
            setupScreen()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        findViewById<ConstraintLayout>(R.id.main_layout).setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                lastTapMs = System.currentTimeMillis()
            }
            true
        }
    }

    private fun setScreenBrightness(b: Float) {
        val lp = window.attributes
        lp.screenBrightness = b
        window.attributes = lp
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setupViews() {
        setContentView(R.layout.activity_main)

        chipRecStatus = findViewById(R.id.chip_rec_status)
        chipConnStatus = findViewById(R.id.chip_conn_status)
        chipCameraLoc = findViewById(R.id.chip_cam_loc)
        fabSettings = findViewById(R.id.fab_settings)

        cardSettings = findViewById(R.id.card_settings)
        inputCameraName = findViewById(R.id.input_cam_name)
        inputServerUri = findViewById(R.id.input_server_uri)
        radioSide = findViewById(R.id.radio_side_group)
        btnCancel = findViewById(R.id.btn_settings_cancel)
        btnSubmit = findViewById(R.id.btn_settings_submit)

        myTextureView = findViewById(R.id.textureView)

        myTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                setupCamera()
                log("Opening camera")
                myCamera?.openCamera()
                log("Camera ready")
                cameraReady = true
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
        }

        fabSettings.setOnClickListener { showCardSettings() }
        btnSubmit.setOnClickListener { submitCardSettings() }
        btnCancel.setOnClickListener { hideCardSettings() }


        val commonPref = getSharedPreferences("common", MODE_PRIVATE)
        val cameraPose = commonPref.getString("cameraPose", "left")!!

        chipCameraLoc.setText(
            when (cameraPose) {
                "left" -> R.string.cam_loc_left
                "right" -> R.string.cam_loc_right
                else -> R.string.cam_loc_right
            }
        )

        findViewById<TextView>(R.id.textView_version_name).text = BuildConfig.VERSION_NAME
    }

    private fun showCardSettings() {
        cardSettings.visibility = View.VISIBLE
        inputCameraName.setText(netIff.cameraName)
        inputServerUri.setText(netIff.serverURI)
        radioSide.check(
            when (netIff.cameraPose) {
                "left" -> R.id.radio_side_left
                "right" -> R.id.radio_side_right
                else -> R.id.radio_side_left
            }
        )
    }

    private fun submitCardSettings() {
        hideCardSettings()

        netIff.cameraName = inputCameraName.text.toString()
        netIff.cameraPose = when (radioSide.checkedRadioButtonId) {
            R.id.radio_side_left -> "left"
            R.id.radio_side_right -> "right"
            else -> "left"
        }

        if (netIff.serverURI != inputServerUri.text.toString()) {
            netIff.serverURI = inputServerUri.text.toString()
            netIff.connectToSocketServer()
        }

        chipCameraLoc.setText(
            when (radioSide.checkedRadioButtonId) {
                R.id.radio_side_left -> R.string.cam_loc_left
                R.id.radio_side_right -> R.string.cam_loc_right
                else -> R.string.cam_loc_right
            }
        )

        val networkPrefEditor = getSharedPreferences("network", MODE_PRIVATE).edit()
        val commonPrefEditor = getSharedPreferences("common", MODE_PRIVATE).edit()

        networkPrefEditor
            .putString("serverURI", netIff.serverURI)
            .apply()

        commonPrefEditor
            .putString("cameraName", netIff.cameraName)
            .putString("cameraPose", netIff.cameraPose)
            .apply()

        Firebase.crashlytics.setCustomKeys {
            key("serverURI", netIff.serverURI)
            key("cameraName", netIff.cameraName)
            key("cameraPose", netIff.cameraPose)
        }
    }

    private fun hideCardSettings() {
        cardSettings.visibility = View.INVISIBLE
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(textureView.windowToken, 0)
    }

    private fun setupNetwork() {
        val networkPref = getSharedPreferences("network", MODE_PRIVATE)
        val commonPref = getSharedPreferences("common", MODE_PRIVATE)

        val serverURI = networkPref.getString("serverURI", "http://192.168.123.123:80")!!
        val cameraName = commonPref.getString("cameraName", "scanner camera")!!
        val cameraPose = commonPref.getString("cameraPose", "left")!!

        netIff = NetworkInterface(serverURI, cameraName, cameraPose, videoConfig)
        netIff.init()

        Firebase.crashlytics.setCustomKeys {
            key("serverURI", netIff.serverURI)
            key("cameraName", netIff.cameraName)
            key("cameraPose", netIff.cameraPose)
        }
    }

    private fun setupTimer() {
        pingTimer = object : CountDownTimer(500000, 100) {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onTick(millisUntilFinished: Long) {
                if (cameraReady) {
                    val currentTimeMs: Long = System.currentTimeMillis()

                    updateConnectionState()
                    updateRecordingState()

                    val cameraState: String = if (recordingVideo) "recording" else "ready"
                    netIff.sendStatus(
                        cameraState,
                        (currentTimeMs - startTimeMs) / 1000,
                        myCamera!!.getPreviewImage(),
                        getBatteryStatus(),
                    )

                    val hostCmd = netIff.getNewCommand()

                    when (hostCmd.cmd) {
                        CmdName.SetConfig -> {
                            log("setShutterSpeed")
                            myCamera!!.setShutterSpeedIso()
                        }

                        CmdName.StartVideo -> {
                            log("Start Video Record")
                            startRecordVideo(hostCmd.param)
                        }

                        CmdName.StopVideo -> {
                            log("Stop Video Record")
                            stopRecordVideo()
                        }

                        CmdName.TakePhoto -> {
                            log("Take Photo")
                            myCamera!!.takePhoto()
                        }

                        else -> {}
                    }

                }
                if (System.currentTimeMillis() - lastTapMs > 30_000 && cardSettings.visibility != View.VISIBLE) {
                    setScreenBrightness(SCREEN_BRIGHTNESS_LOW)
                } else {
                    setScreenBrightness(SCREEN_BRIGHTNESS_MEDIUM)
                }
            }

            override fun onFinish() {
                this.start() //start again the CountDownTimer
            }
        }

        pingTimer.start()
    }

    private fun getBatteryStatus(): Map<String, Any> {
        val b: Intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!

        val status: Int = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        val batteryPct: Float = b.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        val temperature: Float = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10F

        return mapOf(
            "isCharging" to isCharging,
            "percent" to batteryPct,
            "temperature" to temperature,
        )
    }

    @SuppressLint("SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.S)
    fun startRecordVideo(scanId: String) {
        val currentDate = SimpleDateFormat("dd-hh-mm-ss").format(Date())

        if (!recordingVideo) {
            recordingVideo = true
            startTimeMs = System.currentTimeMillis()
            updateRecordingState()

            myCamera!!.startRecordVideo("$scanId--${currentDate}")
        }
    }

    fun stopRecordVideo() {
        recordingVideo = false
        updateRecordingState()

        myCamera!!.stopRecordVideo()
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
    @AfterPermissionGranted(PERMISSIONS_REQUEST_CODE)
    private fun requestPermissions(callback: () -> Unit) {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )

        if (EasyPermissions.hasPermissions(this, *perms)) {
            callback()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "",
                PERMISSIONS_REQUEST_CODE,
                *perms
            )
        }

    }

    fun setupCamera() {
        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            // Получение списка камер с устройства
            for (cameraID in mCameraManager.cameraIdList) {
                log("cameraID: $cameraID")
                val id = cameraID.toInt()

                // создаем обработчик для камеры
                if (id == 0) {
                    log("Creating myCamera cameraID = $cameraID")
                    myCamera = CameraService(
                        this,
                        videoConfig,
                        mCameraManager,
                        cameraID,
                        myTextureView
                    )
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

