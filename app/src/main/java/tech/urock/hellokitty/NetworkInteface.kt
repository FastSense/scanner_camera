package tech.urock.hellokitty

import android.content.Context
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

class NetworkInterface (var context: Context, var server_ip: String)
{
    private val queue = Volley.newRequestQueue(context)
    private val urlBase: String  = "http://${server_ip}:5000"
    private var pingPayload: JSONObject? = null
    private var pingRequest: JsonObjectRequest? = null

    fun init() {
        try {
            pingPayload =
                JSONObject("{\"name\": \"urock-awesome-phone\", \"cameraPosition\": \"right\"}")
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        pingRequest = JsonObjectRequest(
            Request.Method.POST,
            "$urlBase/cameras/ping",
            pingPayload,
            { response: JSONObject ->
                println("Ping response: $response")
            }) { obj: VolleyError -> obj.printStackTrace() }

    }

    fun postPingRequest() {
        queue.add(pingRequest)
    }
}