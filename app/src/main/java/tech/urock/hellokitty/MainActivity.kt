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


class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private var counter: Int = 0
    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    private var videoConfig: VideoConfig = VideoConfig(this)
    private var camera: MyCamera = MyCamera(this, videoConfig)

    private var startTimeMs: Long = System.currentTimeMillis()




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupCamera()
        setupNetwork()
        setupTimer()
    }

    fun setupViews() {
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
//        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

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
                                getString(R.string.cam_pose), videoConfig, camera)
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

                netIff.sendStatus((currentTimeMs - startTimeMs)/1000, camera.getPreviewImage())
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

    fun setupCamera() {
        camera.setup()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        camera.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}

