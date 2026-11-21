package com.arohamusic.app.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

data class ListenerProfile(val name: String = "", val namePromptSeen: Boolean = false) {
    fun sanitized() = copy(name = name.replace(Regex("\\s+"), " ").replace(Regex("[\\p{Cc}\\p{Cf}]"), "").trim().take(40))
    val displayName: String get() = sanitized().name
}

data class HomeGreeting(val title: String, val message: String)

/** Local device time, with daily variety that stays calm while the user browses. */
fun homeGreeting(profile: ListenerProfile, now: LocalDateTime): HomeGreeting {
    val period = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
    val messages = when {
        now.hour < 5 || now.hour >= 22 -> listOf("A little music after hours.", "Your quiet-hours soundtrack.", "Find your night-time rhythm.")
        now.dayOfWeek == DayOfWeek.FRIDAY -> listOf("Your Friday soundtrack awaits.", "Find your weekend soundtrack.", "End the week on a good note.")
        now.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> listOf("Less rush. More weekend music.", "Find a new weekend favorite.", "A soundtrack for your day off.")
        now.dayOfWeek == DayOfWeek.MONDAY -> listOf("New week, new favorites.", "Start the week with a favorite.", "Find your rhythm this week.")
        now.hour < 12 -> listOf("Start with something you love.", "New day, new sounds.", "A melody for your morning.")
        now.hour < 17 -> listOf("Your afternoon reset.", "Find your flow.", "Your next favorite awaits.")
        else -> listOf("Wind down with a favorite.", "Let the evening play.", "A soundtrack for your evening.")
    }
    val variation = ((now.toLocalDate().toEpochDay() / 7) + now.dayOfWeek.value).toInt()
    val title = profile.displayName.takeIf { it.isNotBlank() }?.let { "$period, $it" } ?: period
    return HomeGreeting(title, messages[Math.floorMod(variation, messages.size)])
}
