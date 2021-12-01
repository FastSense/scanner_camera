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

class NetworkInterface (context: Context, server_ip: String, port: String,
                        phone_name: String, cam_pose: String)

{
    private val queue = Volley.newRequestQueue(context)
    private val urlBase: String  = "http://${server_ip}:${port}"
    private var pingPayload: JSONObject? = null
    private var pingRequest: JsonObjectRequest? = null
    private val phone_name = phone_name
    private val cam_pose = cam_pose

    fun init() {

        val pingPayloadMap = HashMap<String, String>()
        pingPayloadMap["name"] = phone_name
        pingPayloadMap["cameraPosition"] = cam_pose

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