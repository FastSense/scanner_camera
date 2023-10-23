package tech.fastsense.scanner

import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.ktx.setCustomKeys
import com.google.firebase.ktx.Firebase
import java.util.*

import org.json.JSONException
import org.json.JSONObject

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.net.URISyntaxException

enum class CmdName {
    None, SetConfig, StartVideo, StopVideo
}

class HostCmd(iCmd: CmdName, iParam: String) {
    var cmd: CmdName = iCmd
    var param: String = iParam
}

class NetworkInterface(
    var serverURI: String,
    var cameraName: String,
    var cameraPose: String,
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

    companion object {
        const val LOG_TAG = "NetworkInterface"
    }

    private fun log(msg: String) {
        Log.i(LOG_TAG, "@@@ $msg")
        Firebase.crashlytics.log("$LOG_TAG: $msg")
    }

    fun init() {
        connectToSocketServer()
    }

    fun getNewCommand(): HostCmd {
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

    fun connectToSocketServer() {
        log("connectToSocketServer $serverURI")
        try {
            mSocket = IO.socket(serverURI)
        } catch (e: URISyntaxException) {
            Firebase.crashlytics.recordException(e)
            log("URISyntaxException")
        }

        onConfig = Emitter.Listener { args ->
            val data = args[0] as JSONObject

            val side = data.getString("side")

            if (side.equals(cameraPose)) {
                videoConfig.fromJson(data)
                configUpdated = true
            }
        }

        onStart = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val side = data.getString("side")

                log("onStart $data")

                if (side.equals(cameraPose)) {
                    val scanId = data.getString("id")
                    currentScanID = scanId
                    startCmdReceived = true

                    Firebase.crashlytics.setCustomKeys {
                        key("scanId", scanId)
                    }
                }
            } catch (e: JSONException) {
                Firebase.crashlytics.recordException(e)
                log("onStart JSONException $data")
                return@Listener
            }
        }

        onStop = Emitter.Listener { args ->
            val data = args[0] as JSONObject

            try {
                val side = data.getString("side")

                if (side.equals(cameraPose)) {
                    log("onStop $data")
                    stopCmdReceived = true
                }
            } catch (e: JSONException) {
                Firebase.crashlytics.recordException(e)
                return@Listener
            }
        }

        mSocket?.on("config", onConfig)
        mSocket?.on("start", onStart)
        mSocket?.on("stop", onStop)
        mSocket?.connect()
    }

    fun getConnectionStatus(): Boolean? {
        return mSocket?.connected()
    }


    fun sendStatus(cameraState: String, timeFromStart: Long, imageStr: String, batteryStatus: Map<String, Any>) {
        val statusMap = HashMap<String, Any>()

        val videoDuration: Int = if (cameraState == "ready") 0 else timeFromStart.toInt()

        statusMap["cameraState"] = cameraState
        statusMap["videoConfig"] = videoConfig.map()
        statusMap["videoDuration"] = videoDuration
        statusMap["frame"] = imageStr
        statusMap["name"] = cameraName
        statusMap["side"] = cameraPose

        statusMap["versionName"] = BuildConfig.VERSION_NAME
        statusMap["battery"] = batteryStatus
        statusMap["model"] = Build.MODEL

        val statusJson = JSONObject(statusMap as Map<String, Any>?)
        mSocket?.emit("status", statusJson)


        Firebase.crashlytics.setCustomKeys {
            key("videoConfig", JSONObject(videoConfig.map() as Map<*, *>?).toString())
        }
    }

}