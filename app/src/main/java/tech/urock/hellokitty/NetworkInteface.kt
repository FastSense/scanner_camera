package tech.urock.hellokitty

import java.util.*
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

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter;
import java.net.URISyntaxException

class NetworkInterface (context: Context, server_ip: String, http_port: String, s_port: String,
                        phone_name: String, cam_pose: String)

{
    private val volleyRequestQueue = Volley.newRequestQueue(context)
    private val serverIp: String = server_ip
    private val urlBase: String  = "http://${serverIp}:${http_port}"
    private val socketPort: String = s_port
    private var pingPayload: JSONObject? = null
    private var pingRequest: JsonObjectRequest? = null
    private val phoneName = phone_name
    private val camPose = cam_pose

    private var mSocket: Socket? = null

    private var onNewMessage: Emitter.Listener? = null


    fun init() {

        val pingPayloadMap = HashMap<String, String>()
        pingPayloadMap["name"] = phoneName
        pingPayloadMap["cameraPosition"] = camPose

        pingPayload = JSONObject(pingPayloadMap as Map<String, String>?)

        pingRequest = JsonObjectRequest(
            Request.Method.POST,
            "$urlBase/cameras/ping",
            pingPayload,
            { response: JSONObject ->
                println("Ping response: $response")
            }) { obj: VolleyError -> obj.printStackTrace() }


        connectToSocketServer()

    }

    fun postPingRequest() {
        volleyRequestQueue.add(pingRequest)
    }

    fun connectToSocketServer() {
        try {
            mSocket = IO.socket("http://${serverIp}:${socketPort}")
        } catch (e: URISyntaxException) {
            println("URISyntaxException")
        }

        onNewMessage = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            val username: String
            val message: String
            try {
                username = data.getString("from")
                message = data.getString("msg")
            } catch (e: JSONException) {
                return@Listener
            }

            // add the message to view
            println("${username}: ${message}")
        }

        mSocket?.on("msg", onNewMessage);
        mSocket?.connect()

        mSocket?.emit("name", phoneName);

    }

    fun sendMsg() {
        val msg_map = HashMap<String, String>()
        msg_map["msg"] = "Hello from Android"
        var msg_json: JSONObject = JSONObject(msg_map as Map<String, String>?)

        mSocket?.emit("msg", msg_json);
    }

}