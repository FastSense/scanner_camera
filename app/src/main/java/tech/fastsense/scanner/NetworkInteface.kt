package tech.fastsense.scanner

import android.util.Log
import java.util.*

import org.json.JSONException
import org.json.JSONObject

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

class NetworkInterface(
    private val serverURI: String,
    private val cameraName: String,
    private val cameraPose: String,
    private var videoConfig: VideoConfig
) {
    private var mSocket: Socket? = null

    private var onConfig: Emitter.Listener? = null
    private var onStart: Emitter.Listener? = null
    private var onStop: Emitter.Listener? = null

    private var configUpdated: Boolean = false
    private var startCmdReceived: Boolean = false
    private var stopCmdReceived: Boolean = false

    private lateinit var currentScanID: String

    fun init() {
        val pingPayloadMap = HashMap<String, String>()
        pingPayloadMap["name"] = cameraName
        pingPayloadMap["cameraPosition"] = cameraPose

        connectToSocketServer()
    }

    fun postPingRequest() {
    }


    fun newCommand (): HostCmd {
        return when {
            configUpdated -> {
                configUpdated = false
                HostCmd(CmdName.SetConfig, "")
            }
            startCmdReceived -> {
                startCmdReceived = false
                HostCmd(CmdName.StartVideo, currentScanID)
            }
            stopCmdReceived -> {
                stopCmdReceived = false
                HostCmd(CmdName.StopVideo, "")
            }
            else -> HostCmd(CmdName.None, "")
        }
    }

    private fun connectToSocketServer() {
        Log.v("fff", "@@@ $serverURI")
        try {
            mSocket = IO.socket(serverURI)
        } catch (e: URISyntaxException) {
            Log.v("fff", "@@@ URISyntaxException $e")
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
                startCmdReceived = true
            } catch (e: JSONException) {
                println("onStart: JSONException")
                return@Listener
            }
        }

        onStop = Emitter.Listener { args ->
            println("Stop recording")
            stopCmdReceived = true
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

        val statusJson = JSONObject(status_map as Map<String, Any>?)
        mSocket?.emit("status", statusJson);
    }

}