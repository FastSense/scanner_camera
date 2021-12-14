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

class NetworkInterface (context: Context, http_server_ip: String, http_port: String,
                        socket_server_ip: String, s_port: String,
                        phone_name: String, cam_pose: String, video_config: VideoConfig,
                        camera: Camera)

{
    private val volleyRequestQueue = Volley.newRequestQueue(context)
    private val httpServerIp: String = http_server_ip
    private val urlBase: String  = "http://${httpServerIp}:${http_port}"
    private val socketServerIp: String = socket_server_ip
    private val socketPort: String = s_port
    private var pingPayload: JSONObject? = null
    private var pingRequest: JsonObjectRequest? = null
    private val phoneName = phone_name
    private val camPose = cam_pose

    private var mSocket: Socket? = null

    private var onConfig: Emitter.Listener? = null
    private var onStart: Emitter.Listener? = null
    private var onStop: Emitter.Listener? = null

    private var videoConfig: VideoConfig = video_config

    private var camera: Camera = camera




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
        println("${pingRequest}")
        volleyRequestQueue.add(pingRequest)
    }

    fun connectToSocketServer() {
        try {
            mSocket = IO.socket("http://${socketServerIp}:${socketPort}")
        } catch (e: URISyntaxException) {
            println("URISyntaxException")
        }

        onConfig = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            videoConfig.fromJson(data)
//            videoConfig.toSharedPref()
        }

        onStart = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val scan_id = data.getString("id")
            } catch (e: JSONException) {
                println("onStart: JSONException")
                return@Listener
            }

            println("Start recording")
            camera.startRecordVideo()
        }

        onStop = Emitter.Listener { args ->
            println("Stop recording")
            camera.stopRecordVideo()
        }


        mSocket?.on("config", onConfig);
        mSocket?.on("start", onStart);
        mSocket?.on("stop", onStop);
        mSocket?.connect()

    }

    fun getConnectionStatus(): Boolean? {
        return mSocket?.connected()
    }


    fun sendStatus(timeFromStart: Long, image_str: String) {
        val status_map = HashMap<String, Any>()

        status_map["cameraState"] = "ready"
        status_map["videoConfig"] = videoConfig.map()
        status_map["videoDuration"] = timeFromStart.toInt()
        status_map["frame"] = image_str

        val status_json: JSONObject = JSONObject(status_map as Map<String, Any>?)
//        println("${status_json}")
        mSocket?.emit("status", status_json);
    }

}