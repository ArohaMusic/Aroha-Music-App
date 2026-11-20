package com.arohamusic.app.data

import com.arohamusic.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stable metadata only; the first visit can rank a cached runway without waiting for discovery. */
class GlimpseFeedStore(private val file: File) {
    suspend fun read(): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.isFile || file.length() > 512_000 || System.currentTimeMillis() - file.lastModified() > 86_400_000) emptyList()
            else LibraryCodec.decode(file.readText()).savedTracks.take(80)
        }.getOrDefault(emptyList())
    }
    suspend fun write(tracks: List<Track>) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            val safe = tracks.filter { it.source == Source.YOUTUBE && it.uri == it.id }.distinctBy { it.id }.take(80)
            val pending = File(file.parentFile, file.name + ".tmp")
            pending.writeText(LibraryCodec.encode(LibraryState(savedTracks = safe)))
            pending.renameTo(file)
        }
        Unit
    }
}
