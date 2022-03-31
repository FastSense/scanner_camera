package tech.fastsense.scanner

import java.util.*
import android.content.Context


import com.android.volley.toolbox.Volley
import com.android.volley.Request
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

enum class CmdName {
    None, SetConfig, StartVideo, StopVideo
}

class HostCmd(iCmd: CmdName, iParam: String) {
    var cmd: CmdName = iCmd
    var param: String = iParam
}

class NetworkInterface (context: Context, http_server_ip: String, http_port: String,
                        socket_server_ip: String, s_port: String,
                        phone_name: String, cam_pose: String, video_config: VideoConfig
                        )

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

    private var configUpdated: Boolean = false
    private var StartCmdReceived: Boolean = false
    private var StopCmdReceived: Boolean = false

    private lateinit var currentScanID: String


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


    fun newCommand (): HostCmd {
        return when {
            configUpdated -> {
                configUpdated = false
                HostCmd(CmdName.SetConfig, "")
            }
            StartCmdReceived -> {
                StartCmdReceived = false
                HostCmd(CmdName.StartVideo, currentScanID)
            }
            StopCmdReceived -> {
                StopCmdReceived = false
                HostCmd(CmdName.StopVideo, "")
            }
            else -> HostCmd(CmdName.None, "")
        }
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
            configUpdated = true
        }

        onStart = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val scan_id = data.getString("id")
                currentScanID = scan_id
                StartCmdReceived = true
            } catch (e: JSONException) {
                println("onStart: JSONException")
                return@Listener
            }
        }

        onStop = Emitter.Listener { args ->
            println("Stop recording")
            StopCmdReceived = true
        }


        mSocket?.on("config", onConfig);
        mSocket?.on("start", onStart);
        mSocket?.on("stop", onStop);
        mSocket?.connect()

    }

    fun getConnectionStatus(): Boolean? {
        return mSocket?.connected()
    }


    fun sendStatus(cameraState: String, timeFromStart: Long, image_str: String) {
        val status_map = HashMap<String, Any>()

        val videoDuration: Int = if (cameraState == "ready") 0 else timeFromStart.toInt()

        status_map["cameraState"] = cameraState
        status_map["videoConfig"] = videoConfig.map()
        status_map["videoDuration"] = videoDuration
        status_map["frame"] = image_str

        val status_json: JSONObject = JSONObject(status_map as Map<String, Any>?)
//        println("${status_json}")
        mSocket?.emit("status", status_json);
    }

}