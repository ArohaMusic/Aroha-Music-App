package com.arohamusic.app.updates

internal enum class UpdateStatus { CHECKING, AVAILABLE, CURRENT, FAILED, UNKNOWN }

internal data class UpdatePresentation(val status: UpdateStatus, val title: String, val detail: String)

/** The modal and Settings must describe the same result, including cached/error states. */
internal fun UpdateState.presentation(installed: String): UpdatePresentation = when {
    checking -> UpdatePresentation(UpdateStatus.CHECKING, "Checking for updates", "Looking for a newer version of Aroha Music…")
    release?.isNewerThan(installed) == true -> UpdatePresentation(UpdateStatus.AVAILABLE, "Update available", "Aroha Music ${release.version} is ready to download.")
    error != null -> UpdatePresentation(UpdateStatus.FAILED, "Couldn't check for updates",
        if (retryAt > 0) "GitHub temporarily limited checks. Your installed version is $installed."
        else "Your installed version is $installed. Try checking again when you're connected.")
    lastChecked > 0 -> UpdatePresentation(UpdateStatus.CURRENT, "You're up to date", "No newer version is available for Aroha Music $installed.")
    else -> UpdatePresentation(UpdateStatus.UNKNOWN, "App updates", "Check whether a newer version of Aroha Music is available.")
}

/** GitHub retains installation and source notices; the app displays product changes only. */
internal fun releaseHighlights(notes: String): List<String> {
    val lines = notes.replace(Regex("(?s)<!--.*?-->"), "").lines()
    val result = mutableListOf<String>()
    var inCode = false
    for (raw in lines) {
        val line = raw.trim()
        if (line.startsWith("```")) { inCode = !inCode; continue }
        if (inCode || line.isBlank()) continue
        val plain = line.trimStart('#', ' ').replace("**", "")
        if (Regex("(?i)^(corresponding (dependency )?sources|dependency sources|source (code|and dependency)|installation|downloads?|checksums?|assets|for (maintainers|developers))\\b").containsMatchIn(plain) ||
            Regex("(?i)^install\\s+.*\\.apk\\b").containsMatchIn(plain)) break
        if (Regex("(?i)^(what['’]s new|release notes|aroha music v?\\d)").containsMatchIn(plain)) continue
        if (Regex("(?i)sha256sums|dependency-source-revisions|dependency-sha256sums|runtime-dependencies|source inventories|verify downloads|revision `?[a-f0-9]{20}").containsMatchIn(plain)) continue
        val text = plain.replace(Regex("^[-*+]\\s+|^\\d+[.)]\\s+"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1").replace("`", "").trim()
        if (text.isNotBlank()) result += text.take(1_200)
        if (result.size == 24) break
    }
    return result
}
