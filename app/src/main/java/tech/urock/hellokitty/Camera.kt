package tech.urock.hellokitty

import java.util.HashMap

class VideoConfig {
    var fullResolution: Int = 1 // 0 - FullHD, 1 - 4K
    var iso: Int = 1500
    var exposure: Int = 640
    var preview_fps: Int = 13
    var preview_quality: Int = 30
    var preview_width: Int = 216
    var preview_height: Int = 384

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

}

