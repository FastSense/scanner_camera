package tech.urock.hellokitty

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Button
import android.os.CountDownTimer

import com.android.volley.toolbox.Volley
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONException
import org.json.JSONObject

//import com.android.volley.AuthFailureError
//import com.android.volley.toolbox.StringRequest
//import com.android.volley.VolleyError
//import com.android.volley.toolbox.JsonObjectRequest
//import com.android.volley.toolbox.JsonArrayRequest




class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private var counter: Int = 0
    private lateinit var countDownTimer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)

        val imageButton: ImageButton = findViewById(R.id.imageButton)
        val button : Button = findViewById(R.id.button)

        val queue = Volley.newRequestQueue(this)
        val urlBase: String  = "http://${getString(R.string.server_ip_address)}:5000"

        var pingPayload: JSONObject? = null
        try {
            pingPayload =
                JSONObject("{\"name\": \"urock-awesome-phone\", \"cameraPosition\": \"right\"}")
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val pingRequest = JsonObjectRequest(
            Request.Method.POST,
            "$urlBase/cameras/ping",
            pingPayload,
            { response: JSONObject ->
                println("Ping response: $response")
            }) { obj: VolleyError -> obj.printStackTrace() }

        countDownTimer = object : CountDownTimer(500000, 5000) {
            override fun onTick(millisUntilFinished: Long) {
                textView.text = "Я насчитал ${++counter} ворон"
                queue.add(pingRequest)

            }
            override fun onFinish() {
                this.start(); //start again the CountDownTimer
            }
        }


        imageButton.setOnClickListener {
            countDownTimer.cancel()
        }

        button.setOnClickListener {
            startTimeCounter()
        }
    }

    fun startTimeCounter() {
        countDownTimer.start()
    }
}