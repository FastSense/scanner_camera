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

    private var videoConfig: VideoConfig = VideoConfig(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupTimer()
        setupNetwork()
    }

    fun setupViews() {
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

        imageButton.setOnClickListener {
            pingTimer.cancel()
        }

        button.setOnClickListener {
            netIff.sendStatus()
        }
    }

    fun setupNetwork() {
        pingTimer.start()
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

