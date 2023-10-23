package tech.fastsense.scanner

import android.content.SharedPreferences
import android.util.Log
import android.util.Size
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import org.json.JSONException
import org.json.JSONObject

enum class FullResolution {
    FOURK, FULLHD
}

class VideoConfig(pref: SharedPreferences) {
    var fullResolution: FullResolution = FullResolution.FOURK
    var iso: Int = 500
    var exposure: Long = 10000000 // in nanoseconds
    var previewFps: Int = 10
    var previewQuality: Int = 15
    var previewWidth: Int = 216
    var previewHeight: Int = 384
    var focusMode: String = "auto"
    var focusDistance: Float = 1.3f

    private var sharedPref: SharedPreferences = pref

    companion object {
        const val LOG_TAG = "Camera2"
    }

    private fun log(msg: String) {
        Log.i(LOG_TAG, "@@@ $msg")
        Firebase.crashlytics.log("$LOG_TAG: msg")
    }

    init {
        iso = sharedPref.getInt("iso", 500)
        exposure = sharedPref.getLong("exposure", 10000000)
        previewFps = sharedPref.getInt("preview_fps", 10)
        previewQuality = sharedPref.getInt("preview_quality", 30)
        previewWidth = sharedPref.getInt("preview_width", 216)
        previewHeight = sharedPref.getInt("preview_height", 384)
        focusMode = sharedPref.getString("focus_mode", "auto")!!
        focusDistance = sharedPref.getFloat("focus_distance", 1.3f)
    }

    fun map(): HashMap<String, Any> {
        val videoConfig = HashMap<String, Any>()
        val previewResolution = HashMap<String, Int>()

        videoConfig["fullResolution"] =
            if (fullResolution == FullResolution.FOURK) "4k" else "fullhd"
        videoConfig["iso"] = iso
        videoConfig["exposure"] = (1000000000 / exposure.toDouble()).toInt()
        previewResolution["width"] = previewWidth
        previewResolution["height"] = previewHeight
        videoConfig["previewResolution"] = previewResolution
        videoConfig["previewFps"] = previewFps
        videoConfig["previewQuality"] = previewQuality
        videoConfig["focusMode"] = focusMode
        videoConfig["focusDistance"] = focusDistance

        return videoConfig
    }

    fun fromJson(data: JSONObject) {
        log("VideoConfig: $data")

        val fullResolutionI: FullResolution
        val isoI: Int
        val exposureI: Long
        val previewFpsI: Int
        val previewQualityI: Int
        val previewWidthI: Int
        val previewHeightI: Int
        val previewResolutionI: JSONObject
        val focusModeI: String
        val focusDistanceI: Float

        try {
            fullResolutionI =
                if (data.getString("fullResolution") == "4k") FullResolution.FOURK else FullResolution.FULLHD
            isoI = data.getInt("iso")
            exposureI = data.getLong("exposure")
            previewFpsI = data.getInt("previewFps")
            previewQualityI = data.getInt("previewQuality")
            previewResolutionI = data.get("previewResolution") as JSONObject
            previewWidthI = previewResolutionI.getInt("width")
            previewHeightI = previewResolutionI.getInt("height")
            focusModeI = data.getString("focusMode")
            focusDistanceI = data.getDouble("focusDistance").toFloat()
        } catch (e: JSONException) {
            log("VideoConfig.fromJson: JSONException")
            return
        }
        fullResolution = fullResolutionI
        iso = isoI
        val dt: Double = 1 / exposureI.toDouble()   // in sec
        exposure = (dt * 1000000000).toLong()     // in nanoseconds
        previewFps = previewFpsI
        previewQuality = previewQualityI
        previewWidth = previewWidthI
        previewHeight = previewHeightI
        focusMode = focusModeI
        focusDistance = focusDistanceI

        saveSharedPref()
    }

    private fun saveSharedPref() {
        val editor = sharedPref.edit()
        editor
            .putInt("iso", iso)
            .putLong("exposure", exposure)
            .putInt("preview_fps", previewFps)
            .putInt("preview_quality", previewQuality)
            .putInt("preview_width", previewWidth)
            .putInt("preview_height", previewHeight)
            .putString("focus_mode", focusMode)
            .putFloat("focus_distance", focusDistance)
            .apply()
    }
}

