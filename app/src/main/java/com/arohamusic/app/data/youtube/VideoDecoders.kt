package com.arohamusic.app.data.youtube

import android.media.MediaCodecList
import android.media.MediaFormat
import java.util.concurrent.ConcurrentHashMap

/** Exclude formats that cannot be decoded on this device, rather than advertising an unplayable resolution. */
internal object VideoDecoders {
    private val support = ConcurrentHashMap<String, Boolean>()
    fun supports(container: String, codec: String, width: Int, height: Int, fps: Int): Boolean {
        val mime = when {
            codec.startsWith("avc") -> MediaFormat.MIMETYPE_VIDEO_AVC
            codec.startsWith("vp09") || codec.startsWith("vp9") -> MediaFormat.MIMETYPE_VIDEO_VP9
            codec.startsWith("av01") -> "video/av01"
            codec.startsWith("hev") || codec.startsWith("hvc") -> MediaFormat.MIMETYPE_VIDEO_HEVC
            codec.startsWith("vp8") -> MediaFormat.MIMETYPE_VIDEO_VP8
            else -> if (container == "video/mp4") MediaFormat.MIMETYPE_VIDEO_AVC else return false
        }
        val key = "$mime|$width|$height|$fps"
        if (support.size >= 128 && !support.containsKey(key)) support.keys.firstOrNull()?.let { support.remove(it) }
        return support.getOrPut(key) {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) } && runCatching {
                    val caps = info.getCapabilitiesForType(mime).videoCapabilities
                    caps != null && (width <= 0 || caps.areSizeAndRateSupported(width, height, fps.toDouble()))
                }.getOrDefault(false)
            }
        }
    }
}
