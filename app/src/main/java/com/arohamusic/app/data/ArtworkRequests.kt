package com.arohamusic.app.data

import android.content.Context
import coil.request.ImageRequest
import coil.size.Precision
import com.arohamusic.app.data.youtube.ArtworkUrls

/** One preview identity shared by rows, prefetch and the player's immediate placeholder. */
object ArtworkRequests {
    fun previewKey(url: String) = "cover-preview:$url"
    fun source(url: String, pixels: Int): String = if (url.startsWith("asset://"))
        "file:///android_asset/${url.removePrefix("asset://")}" else ArtworkUrls.highResolution(url, pixels)

    fun preview(context: Context, url: String): ImageRequest = ImageRequest.Builder(context)
        .data(source(url, 384)).memoryCacheKey(previewKey(url)).size(384).precision(Precision.INEXACT).build()
}
