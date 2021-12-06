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


class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private var counter: Int = 0
    private lateinit var pingTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface
    private lateinit var videoConfig: SharedPreferences

    private lateinit var video_cc: VideoConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupNetwork()
        setupTimer()

        video_cc = VideoConfig()


//        videoConfig = getSharedPreferences("settings", Context.MODE_PRIVATE)
//
//        // Запоминаем данные
//        val editor = videoConfig.edit()
//        editor.putInt("fullResolution", 0).apply()
//        editor.putInt("iso", 1000).apply()
//        editor.putInt("exposure", 640).apply()
//        editor.putInt("preview_fps", 10).apply()
//        editor.putInt("preview_width", 216).apply()
//        editor.putInt("preview_height", 384).apply()
    }

    fun setupViews() {
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

        imageButton.setOnClickListener {
            pingTimer.cancel()
            netIff.sendStatus()
        }

        button.setOnClickListener {
            pingTimer.start()
        }
    }

    fun setupNetwork() {
        netIff = NetworkInterface(this,
                                getString(R.string.http_server_ip),
                                getString(R.string.http_port),
                                getString(R.string.socket_server_ip),
                                getString(R.string.s_port),
                                getString(R.string.phone_name),
                                getString(R.string.cam_pose))
        netIff.init()
    }

    fun setupTimer() {
        pingTimer = object : CountDownTimer(500000, 5000) {
            override fun onTick(millisUntilFinished: Long) {
                textView.text = "Я насчитал ${++counter} ворон"
                netIff.postPingRequest()

            }
            override fun onFinish() {
                this.start(); //start again the CountDownTimer
            }
        }
    }


}

