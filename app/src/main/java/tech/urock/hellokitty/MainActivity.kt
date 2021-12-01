package tech.urock.hellokitty

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
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var netIff: NetworkInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupNetwork()
        setupTimer()
    }

    fun setupViews() {
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

        imageButton.setOnClickListener {
            countDownTimer.cancel()
        }

        button.setOnClickListener {
            countDownTimer.start()
        }
    }

    fun setupNetwork() {
        netIff = NetworkInterface(this,
                                getString(R.string.server_ip_address),
                                getString(R.string.port),
                                getString(R.string.phone_name),
                                getString(R.string.cam_pose))
        netIff.init()
    }

    fun setupTimer() {
        countDownTimer = object : CountDownTimer(500000, 5000) {
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