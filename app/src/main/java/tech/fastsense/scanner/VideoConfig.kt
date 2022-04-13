package tech.fastsense.scanner

import android.content.SharedPreferences
import android.util.Size
import org.json.JSONException
import org.json.JSONObject

enum class FullResolution {
    FOURK, FULLHD
}

class VideoConfig (pref: SharedPreferences) {
    var fullResolution: FullResolution = FullResolution.FOURK
    var iso: Int = 500
    var exposure: Long = 10000000 // in nanoseconds
    var previewFps: Int = 10
    var previewQuality: Int = 15
    var previewWidth: Int = 216
    var previewHeight: Int = 384

    private var sharedPref: SharedPreferences = pref

    init {
        iso = sharedPref.getInt("iso", 500)
        exposure = sharedPref.getLong("exposure", 10000000)
        previewFps = sharedPref.getInt("preview_fps", 10)
        previewQuality = sharedPref.getInt("preview_quality", 30)
        previewWidth = sharedPref.getInt("preview_width", 216)
        previewHeight = sharedPref.getInt("preview_height", 384)
    }

    private fun print () {
        println("fullResolution = $fullResolution")
        println("iso = $iso")
        println("exposure = $exposure")
        println("preview_fps = $previewFps")
        println("preview_quality = $previewQuality")
        println("preview_width = $previewWidth")
        println("preview_height = $previewHeight")
    }

    fun map (): HashMap<String, Any> {
        val videoConfig = HashMap<String, Any>()
        val previewResolution = HashMap<String, Int>()

        videoConfig["fullResolution"] = if (fullResolution == FullResolution.FOURK) "4k" else "fullhd"
        videoConfig["iso"] = iso
        videoConfig["exposure"] = (1000000000 / exposure.toDouble()).toInt()
        previewResolution["width"] = previewWidth
        previewResolution["height"] = previewHeight
        videoConfig["previewResolution"] = previewResolution
        videoConfig["previewFps"] = previewFps
        videoConfig["previewQuality"] = previewQuality

        return videoConfig
    }

    fun fromJson (data: JSONObject) {
        println("VideoConfig: $data")

        val fullResolutionI: FullResolution
        val isoI: Int
        val exposureI: Long
        val previewFpsI: Int
        val previewQualityI: Int
        val previewWidthI: Int
        val previewHeightI: Int
        val previewResolutionI: JSONObject

        try {
            fullResolutionI = if (data.getString("fullResolution") == "4k") FullResolution.FOURK else FullResolution.FULLHD
            isoI = data.getInt("iso")
            exposureI = data.getLong("exposure")
            previewFpsI = data.getInt("previewFps")
            previewQualityI = data.getInt("previewQuality")
            previewResolutionI = data.get("previewResolution") as JSONObject
            previewWidthI = previewResolutionI.getInt("width")
            previewHeightI = previewResolutionI.getInt("height")
        } catch (e: JSONException) {
            println("VideoConfig.fromJson: JSONException")
            return
        }
        fullResolution = fullResolutionI
        iso = isoI
        val dt: Double = 1/exposureI.toDouble()   // in sec
        exposure = (dt * 1000000000).toLong()     // in nanoseconds
        previewFps = previewFpsI
        previewQuality = previewQualityI
        previewWidth = previewWidthI
        previewHeight = previewHeightI

        saveSharedPref()
    }

    fun getSize (): Size {
        return Size(previewWidth, previewHeight)
    }

    fun saveSharedPref () {
        val editor = sharedPref.edit()
        editor
            .putInt("iso", iso)
            .putLong("exposure", exposure)
            .putInt("preview_fps", previewFps)
            .putInt("preview_quality", previewQuality)
            .putInt("preview_width", previewWidth)
            .putInt("preview_height", previewHeight)
            .apply()
    }
}

