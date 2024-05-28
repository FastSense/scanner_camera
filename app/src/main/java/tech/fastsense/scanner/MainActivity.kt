package tech.fastsense.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.ktx.setCustomKeys
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {
    private lateinit var chipRecStatus: Chip
    private lateinit var chipConnStatus: Chip
    private lateinit var chipCameraLoc: Chip
    private lateinit var fabSettings: FloatingActionButton

    private lateinit var cardSettings: CardView
    private lateinit var inputCameraName: EditText
    private lateinit var inputServerUri: EditText
    private lateinit var spinnerSide: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private lateinit var pingTimer: Timer
    private lateinit var videoSyncTimer: Timer
    private lateinit var netIff: NetworkInterface

    private lateinit var myTextureView: TextureView

    private lateinit var videoConfig: VideoConfig

    private var startTimeMs: Long = System.currentTimeMillis()

    private var lastTapMs: Long = System.currentTimeMillis()

    private lateinit var mCameraManager: CameraManager
    private var cameraReady = false

    private var myCamera: CameraService? = null

    private lateinit var sensorRecorder: SensorRecorder

    private var recordingVideo: Boolean = false

    private lateinit var statusHandlerThread: HandlerThread
    private lateinit var statusHandler: Handler

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

        sensorRecorder = SensorRecorder(this)

        setupTimer()
        setupVideoSync()
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
        runOnUiThread {
            val lp = window.attributes
            lp.screenBrightness = b
            window.attributes = lp
        }
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
        spinnerSide = findViewById(R.id.spinner_side)
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

        chipCameraLoc.text = cameraPose

        findViewById<TextView>(R.id.textView_version_name).text = BuildConfig.VERSION_NAME
    }

    private fun showCardSettings() {
        cardSettings.visibility = View.VISIBLE
        inputCameraName.setText(netIff.cameraName)
        inputServerUri.setText(netIff.serverURI)
        spinnerSide.setSelection(resources.getStringArray(R.array.sides).indexOf(netIff.cameraPose))
    }

    private fun submitCardSettings() {
        hideCardSettings()

        netIff.cameraName = inputCameraName.text.toString()
        netIff.cameraPose = spinnerSide.selectedItem as String

        if (netIff.serverURI != inputServerUri.text.toString()) {
            netIff.serverURI = inputServerUri.text.toString()
            netIff.connectToSocketServer()
        }

        chipCameraLoc.text = spinnerSide.selectedItem as String

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

    private fun setupVideoSync() {
        videoSyncTimer = Timer()

        videoSyncTimer.schedule(object: TimerTask() {
            override fun run() {
                syncVideos()
            }
        }, 0L, 5000L)
    }

    private fun setupTimer() {
        var prevStatusTs = 0L

        // Создаем HandlerThread для выполнения фоновых задач
        statusHandlerThread = HandlerThread("StatusHandlerThread")
        statusHandlerThread.start()
        statusHandler = Handler(statusHandlerThread.looper)

        // Используем Handler для выполнения периодических задач
        statusHandler.post(object : Runnable {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun run() {
                if (cameraReady) {
                    val currentTimeMs: Long = System.currentTimeMillis()

                    updateConnectionState()
                    updateRecordingState()

                    val cameraState: String = if (recordingVideo) "recording" else "ready"

                    if (System.currentTimeMillis() - prevStatusTs > 90) {
                        val statusStartTime = System.currentTimeMillis()
                        netIff.sendStatus(
                            cameraState,
                            (currentTimeMs - startTimeMs) / 1000,
                            myCamera!!.getPreviewImage(),
                            getBatteryStatus(),
                        )
                        val statusEndTime = System.currentTimeMillis()
                        log("sendStatus execution time: ${statusEndTime - statusStartTime} ms")
                        prevStatusTs = System.currentTimeMillis()
                    }

                    // Обработка команд от сервера
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

                    // Логика изменения яркости экрана
                    if (System.currentTimeMillis() - lastTapMs > 30_000 && cardSettings.visibility != View.VISIBLE) {
                        setScreenBrightness(SCREEN_BRIGHTNESS_LOW)
                    } else {
                        setScreenBrightness(SCREEN_BRIGHTNESS_MEDIUM)
                    }
                }
                // Повторяем задачу через 10 миллисекунд
                statusHandler.postDelayed(this, 10)
            }
        })
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

            val commonPref = getSharedPreferences("common", MODE_PRIVATE)
            val cameraPose = commonPref.getString("cameraPose", "left")!!
            val subSide = if (cameraPose.split("_").size > 1) cameraPose.split("_")[1] else null

            val fileName = if (subSide != null) "$scanId--$subSide--$currentDate" else "$scanId--$currentDate"

            runOnUiThread {
                myCamera!!.startRecordVideo(fileName)
                sensorRecorder.startRecording("$fileName.jsonl") // Начало записи данных с сенсоров
            }
        } else {
            log("Recording is already in progress, ignoring the start command.")
        }
    }



    fun stopRecordVideo() {
        if (recordingVideo) {
            recordingVideo = false
            updateRecordingState()

            runOnUiThread {
                myCamera!!.stopRecordVideo()
                sensorRecorder.stopRecording() // Остановка записи данных с сенсоров
            }
        } else {
            log("Recording is not in progress, ignoring the stop command.")
        }
    }


    private val serverURI: String
        get() {
            val p = getSharedPreferences("network", MODE_PRIVATE)
            return p.getString("serverURI", "http://192.168.123.123:80")!!
        }

    data class FileInfo(
        val name: String,
        val lastModified: Float,
        val size: Long,
    )

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(
                it,
                resources.getString(R.string.app_name)
            ).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    var isCopyingVideo = false

    private fun uploadVideo(file: File) {
        log("VIDEO_SYNC: copy file $file")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "in_file",
                file.name,
                file.asRequestBody("video/mp4".toMediaTypeOrNull())
            )
            .build()

        val commonPref = getSharedPreferences("common", MODE_PRIVATE)
        val cameraPose = commonPref.getString("cameraPose", "left")!!

        val request = Request.Builder()
            .url("$serverURI/api/v0/storage/videos/?source=${cameraPose}")
            .put(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(300))
            .writeTimeout(Duration.ofSeconds(300))
            .build()

        isCopyingVideo = true

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                log("VIDEO_SYNC: upload failed ${e.message} $e")
                isCopyingVideo = false
            }

            override fun onResponse(call: Call, response: Response) {
                log("VIDEO_SYNC: video copied ${response.body?.string()}")
                isCopyingVideo = false
            }
        })
    }

    private fun syncVideos() {
        log("VIDEO_SYNC: checking ...")

        if (recordingVideo) {
            log("VIDEO_SYNC: recording video, skip sync")
            return
        }

        if (isCopyingVideo) {
            log("VIDEO_SYNC: copying video, skip sync")
            return
        }

        val queue = Volley.newRequestQueue(this)
        val jsonArrayRequest = JsonArrayRequest(
            com.android.volley.Request.Method.GET, "$serverURI/api/v0/storage/videos/", null,
            { response ->

                val gson = Gson()
                val filesList: List<FileInfo> = gson.fromJson(response.toString(), Array<FileInfo>::class.java).toList()
                val remoteFilesNames = filesList.map { it.name }

                val localFiles = getOutputDirectory().listFiles()!!
                val localFilesNames = localFiles.map { it.name }

                for (fn in localFilesNames) {
                    if (!remoteFilesNames.contains(fn)) {

                        Timer().schedule(timerTask {
                            netIff.sendNotification("New video [${netIff.cameraPose}]", fn)
                            thread { uploadVideo(localFiles.find { it.name == fn }!!) }
                        }, 2000)

                        break
                    }
                }
            },
            { }
        )
        queue.add(jsonArrayRequest)
    }

    private fun updateRecordingState() {
        runOnUiThread {
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
    }

    private fun updateConnectionState() {
        runOnUiThread {
            if (netIff.getConnectionStatus()!!) {
                chipConnStatus.setText(R.string.conn_status_connected)
                chipConnStatus.setChipIconResource(R.drawable.ic_baseline_link_24)
            } else {
                chipConnStatus.setText(R.string.conn_status_disconnected)
                chipConnStatus.setChipIconResource(R.drawable.ic_baseline_link_off_24)
            }
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

    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем HandlerThread при уничтожении активности
        statusHandlerThread.quitSafely()
    }
    // end camera2
}
