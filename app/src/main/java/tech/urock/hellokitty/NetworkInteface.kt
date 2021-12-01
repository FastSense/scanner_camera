package tech.urock.hellokitty

import android.content.Context


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

        val pingPayloadMap = HashMap<String, String>()
        pingPayloadMap["name"] = "urock-awesome-phone"
        pingPayloadMap["cameraPosition"] = "right"

        pingPayload = JSONObject(pingPayloadMap as Map<String, String>?)

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