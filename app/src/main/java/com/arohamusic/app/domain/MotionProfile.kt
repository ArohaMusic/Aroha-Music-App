package com.arohamusic.app.domain

import androidx.compose.runtime.Immutable

/** Shared policy for distance, timing and feedback. Reduced motion always wins. */
@Immutable
data class MotionProfile(
    val enabled: Boolean,
    val expressive: Boolean,
    val durationScale: Float,
    val travelScale: Float,
    val damping: Float,
    val staggerMs: Int,
) {
    fun duration(milliseconds: Int): Int = (milliseconds * durationScale).toInt()
    fun pressScale(fullScale: Float): Float = 1f - (1f - fullScale) * travelScale
    fun delay(index: Int): Int = index.coerceIn(0, 5) * staggerMs

    companion object {
        fun from(preferences: ThemePreferences): MotionProfile = when {
            preferences.reduceMotion || preferences.motion == MotionLevel.OFF ->
                MotionProfile(false, false, 0f, 0f, 1f, 0)
            preferences.motion == MotionLevel.SUBTLE ->
                MotionProfile(true, false, .8f, .3f, 1f, 0)
            else -> MotionProfile(true, true, 1f, 1f, .82f, 32)
        }
    }
}
