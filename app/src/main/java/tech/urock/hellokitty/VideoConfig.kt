package tech.urock.hellokitty

import android.content.Context
import android.content.SharedPreferences
import android.util.Size
import org.json.JSONException
import org.json.JSONObject
import java.util.HashMap

enum class FullResolution {
    FOURK, FULLHD
}

class VideoConfig (context: Context) {
    var fullResolution: FullResolution = FullResolution.FOURK
    var iso: Int = 1000
    var exposure: Long = 1000000 // in nanoseconds
    var preview_fps: Int = 10
    var preview_quality: Int = 30
    var preview_width: Int = 216
    var preview_height: Int = 384
    
    var context: Context = context
//    private lateinit var sharedPref: SharedPreferences
//    init {
//        sharedPref = context.getSharedPreferences("videoConfig", Context.MODE_PRIVATE)
//
////        if(sharedPref.contains("fullResolution")){
////
////            fullResolution = sharedPref.getInt("fullResolution", 1)
////            iso = sharedPref.getInt("iso", 1500)
////            exposure = sharedPref.getInt("exposure", 640)
////            preview_fps = sharedPref.getInt("preview_fps", 10)
////            preview_quality = sharedPref.getInt("preview_quality", 30)
////            preview_width = sharedPref.getInt("preview_width", 216)
////            preview_height = sharedPref.getInt("preview_height", 384)
////        }
//    }

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

        videoConfig["fullResolution"] = if (fullResolution == FullResolution.FOURK) "4k" else "fullhd"
        videoConfig["iso"] = iso
        videoConfig["exposure"] = (1000000000/exposure.toDouble()).toInt()
        previewResolution["width"] = preview_width
        previewResolution["height"] = preview_height
        videoConfig["previewResolution"] = previewResolution
        videoConfig["previewFps"] = preview_fps
        videoConfig["previewQuality"] = preview_quality

        return videoConfig
    }

    fun fromJson(data: JSONObject) {
        println("VideoConfig: $data")

        var fullResolution_i: FullResolution = FullResolution.FOURK
        var iso_i: Int = 1500
        var exposure_i: Int = 640
        var preview_fps_i: Int = 13
        var preview_quality_i: Int = 30
        var preview_width_i: Int = 216
        var preview_height_i: Int = 384
        val preview_resolution_i: JSONObject

        val message: String
        try {
            fullResolution_i = if (data.getString("fullResolution") == "4k") FullResolution.FOURK else FullResolution.FULLHD
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
        val dt: Double = 1/exposure_i.toDouble()   // in sec
        exposure = (dt*1000000000).toLong()     // in nanoseconds
        preview_fps = preview_fps_i
        preview_quality = preview_quality_i
        preview_width = preview_width_i
        preview_height = preview_height_i

        print()
    }

    fun getSize(): Size {
        return Size(preview_width, preview_height)
    }

//    fun toSharedPref() {
//
//        val editor = sharedPref.edit()
//        editor.putInt("fullResolution", fullResolution).apply()
//        editor.putInt("iso", iso).apply()
//        editor.putInt("exposure", exposure).apply()
//        editor.putInt("preview_fps", preview_fps).apply()
//        editor.putInt("preview_quality", preview_quality).apply()
//        editor.putInt("preview_width", preview_width).apply()
//        editor.putInt("preview_height", preview_height).apply()
//    }

}

