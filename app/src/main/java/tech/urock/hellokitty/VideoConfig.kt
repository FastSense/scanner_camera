package tech.urock.hellokitty

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import java.util.HashMap

class VideoConfig (context: Context) {
    var fullResolution: Int = 1 // 0 - fullhd, 1 - 4k
    var iso: Int = 1500
    var exposure: Int = 640
    var preview_fps: Int = 13
    var preview_quality: Int = 30
    var preview_width: Int = 216
    var preview_height: Int = 384
    
    var context: Context = context
    private lateinit var sharedPref: SharedPreferences

    fun print() {
        println("fullResolution = ${fullResolution}")
        println("iso = ${iso}")
        println("exposure = ${exposure}")
        println("preview_fps = ${preview_fps}")
        println("preview_quality = ${preview_quality}")
        println("preview_width = ${preview_width}")
        println("preview_height = ${preview_height}")
    }

    fun map(): HashMap<String, Any> {
        val videoConfig = HashMap<String, Any>()
        val previewResolution = HashMap<String, Int>()

        videoConfig["fullResolution"] = if (fullResolution > 0) "4k" else "fullhd"
        videoConfig["iso"] = iso
        videoConfig["exposure"] = exposure
        previewResolution["width"] = preview_width
        previewResolution["height"] = preview_height
        videoConfig["previewResolution"] = previewResolution
        videoConfig["previewFps"] = preview_fps
        videoConfig["previewQuality"] = preview_quality

        return videoConfig
    }

    fun fromJson(data: JSONObject) {
        println("VideoConfig: $data")

        var fullResolution_i: Int = 1 // 0 - FullHD, 1 - 4K
        var iso_i: Int = 1500
        var exposure_i: Int = 640
        var preview_fps_i: Int = 13
        var preview_quality_i: Int = 30
        var preview_width_i: Int = 216
        var preview_height_i: Int = 384
        val preview_resolution_i: JSONObject

        val message: String
        try {
            fullResolution_i = if (data.getString("fullResolution") == "4k") 1 else 0
            iso_i = data.getInt("iso")
            exposure_i = data.getInt("exposure")
            preview_fps_i = data.getInt("previewFps")
            preview_quality_i = data.getInt("previewQuality")
            preview_resolution_i = data.get("previewResolution") as JSONObject
            preview_width_i = preview_resolution_i.getInt("width")
            preview_height_i = preview_resolution_i.getInt("height")
        } catch (e: JSONException) {
            println("VideoConfig.fromJson: JSONException")
            return
        }
        fullResolution = fullResolution_i
        iso = iso_i
        exposure = exposure_i
        preview_fps = preview_fps_i
        preview_quality = preview_quality_i
        preview_width = preview_width_i
        preview_height = preview_height_i

        print()
    }

    fun toSharedPref() {

        sharedPref = context.getSharedPreferences("videoConfig", Context.MODE_PRIVATE)
//
//        // Запоминаем данные
//        val editor = videoConfig.edit()
//        editor.putInt("fullResolution", 0).apply()
//        editor.putInt("iso", 1000).apply()
//        editor.putInt("exposure", 640).apply()
//        editor.putInt("preview_fps", 10).apply()
//        editor.putInt("preview_width", 216).apply()
//        editor.putInt("preview_height", 384).apply()
    }

}

