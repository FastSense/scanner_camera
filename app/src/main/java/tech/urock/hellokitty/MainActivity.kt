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
//        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

//        imageButton.setOnClickListener {
//            pingTimer.cancel()
//        }

        button.setOnClickListener {

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
        pingTimer = object : CountDownTimer(500000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                var socketConnectionState: String
                socketConnectionState = if (netIff.getConnectionStatus() == true) "State connected.\n" else "State disconnected.\n"
                "$socketConnectionState Time from start: ${++counter} sec".also { textView.text = it }
                netIff.sendStatus(counter)
                if (counter % 5 == 0)
                    netIff.postPingRequest()
            }
            override fun onFinish() {
                this.start(); //start again the CountDownTimer
            }
        }
    }


}

