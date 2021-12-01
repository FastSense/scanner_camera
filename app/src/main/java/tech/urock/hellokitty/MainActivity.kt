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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)

        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)


        imageButton.setOnClickListener {
            textView.setText("Image Button Pressed")
            println("Image Button Pressed")
        }

        button.setOnClickListener {
            startTimeCounter()
        }
    }

    fun startTimeCounter() {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                textView.text = "Я насчитал ${++counter} ворон"
            }
            override fun onFinish() {
                this.start(); //start again the CountDownTimer
            }
        }.start()
    }
}