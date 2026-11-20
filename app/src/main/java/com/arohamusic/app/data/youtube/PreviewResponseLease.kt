package com.arohamusic.app.data.youtube

/** Tiny memory-only native response lease. A quality change can reuse format URLs without another player request. */
internal class PreviewResponseLease(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val response: String, val nonce: String, val at: Long)
    private val entries = linkedMapOf<String, Entry>()
    @Synchronized fun get(id: String): Pair<String, String>? {
        val entry = entries[id] ?: return null
        if (clock() - entry.at !in 0..90_000) { entries.remove(id); return null }
        return entry.response to entry.nonce
    }
    @Synchronized fun put(id: String, response: String, nonce: String) {
        if (response.length > 2_000_000) return
        entries[id] = Entry(response, nonce, clock())
        while (entries.size > 3) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() { entries.clear() }
}
