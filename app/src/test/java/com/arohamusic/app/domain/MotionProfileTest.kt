package com.arohamusic.app.domain

import com.arohamusic.app.data.FeatureCodec
import org.junit.Assert.*
import org.junit.Test

class MotionProfileTest {
    @Test fun savedMotionAndAccessibilityChoicesSurviveUpgrade() {
        MotionLevel.entries.forEach { level -> listOf(false, true).forEach { reduce ->
            val original = FeatureState(theme = ThemePreferences(motion = level, reduceMotion = reduce, effects = false))
            val restored = FeatureCodec.decode(FeatureCodec.encode(original)).theme
            assertEquals(level, restored.motion)
            assertEquals(reduce, restored.reduceMotion)
            assertFalse(restored.effects)
        } }
        assertEquals(MotionLevel.FULL, FeatureCodec.decode(null).theme.motion)
        assertEquals(MotionLevel.FULL, FeatureCodec.decode("{\"theme\":{}}").theme.motion)
    }

    @Test fun disablingMotionRemovesDistanceTimingAndStaggerForEveryLevel() {
        MotionLevel.entries.forEach { level ->
            val policy = MotionProfile.from(ThemePreferences(motion = level, reduceMotion = true))
            assertFalse(policy.enabled)
            assertFalse(policy.expressive)
            assertEquals(0, policy.duration(420))
            assertEquals(0, policy.delay(100))
            assertEquals(1f, policy.pressScale(.9f), 0f)
        }
        assertFalse(MotionProfile.from(ThemePreferences(motion = MotionLevel.OFF)).enabled)
    }

    @Test fun subtleActuallyReducesMovementAndFeedbackRatherThanOnlyDuration() {
        val full = MotionProfile.from(ThemePreferences(motion = MotionLevel.FULL))
        val subtle = MotionProfile.from(ThemePreferences(motion = MotionLevel.SUBTLE))
        assertTrue(full.expressive)
        assertFalse(subtle.expressive)
        assertTrue(subtle.enabled)
        assertTrue(subtle.travelScale < full.travelScale)
        assertTrue(subtle.pressScale(.9f) > full.pressScale(.9f))
        assertTrue(subtle.duration(360) < full.duration(360))
        assertEquals(0, full.delay(-5))
        assertTrue("Large lists must never accumulate long entrance delays", full.delay(10_000) <= 160)
    }
}
