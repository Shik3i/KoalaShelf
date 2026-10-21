package net.koalastuff.koalashelf.core.player

import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.network.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerEngineTest {

    private var prefs = KoalaPrefs()
    private val engine = SleepTimerEngine { prefs }

    @Test
    fun `default mode is Off and remaining seconds is zero`() {
        assertEquals(SleepTimerMode.Off, engine.mode.value)
        assertEquals(0.0, engine.remainingSeconds.value, 0.001)
    }

    @Test
    fun `setMinutes sets end time correctly`() {
        val now = 1_000_000L
        engine.setMinutes(minutes = 15, isPlaying = true, now = now)

        val mode = engine.mode.value
        assertTrue(mode is SleepTimerMode.Minutes)
        assertEquals(15, (mode as SleepTimerMode.Minutes).minutes)
        assertEquals(now + 15 * 60_000L, mode.endsAtMillis)
        assertEquals(15 * 60.0, engine.remainingSeconds.value, 0.001)
    }

    @Test
    fun `setMinutes with zero or negative switches to Off`() {
        engine.setMinutes(15, isPlaying = true)
        engine.setMinutes(0, isPlaying = true)
        assertEquals(SleepTimerMode.Off, engine.mode.value)

        engine.setMinutes(-5, isPlaying = true)
        assertEquals(SleepTimerMode.Off, engine.mode.value)
    }

    @Test
    fun `setSmartWindow sets min and max timestamps`() {
        val now = 2_000_000L
        engine.setSmartWindow(minMinutes = 20, maxMinutes = 40, isPlaying = true, now = now)

        val mode = engine.mode.value
        assertTrue(mode is SleepTimerMode.SmartWindow)
        val smart = mode as SleepTimerMode.SmartWindow
        assertEquals(20, smart.minMinutes)
        assertEquals(40, smart.maxMinutes)
        assertEquals(now + 20 * 60_000L, smart.minAtMillis)
        assertEquals(now + 40 * 60_000L, smart.maxAtMillis)
    }

    @Test
    fun `extend sleep adds extra minutes to running timer`() {
        val now = 1_000_000L
        engine.setMinutes(10, isPlaying = true, now = now)
        engine.extend(extraMinutes = 5, currentTime = 100.0, rate = 1.0, currentChapter = null, now = now)

        val mode = engine.mode.value as SleepTimerMode.Minutes
        assertEquals(15, mode.minutes)
        assertEquals(now + 15 * 60_000L, mode.endsAtMillis)
    }

    @Test
    fun `evaluateTick triggers stop when minutes sleep timer expires`() {
        val now = 1_000_000L
        prefs = prefs.copy(sleepRewind = 15)
        engine.setMinutes(1, isPlaying = true, now = now)

        val tickNow = now + 65_000L // 65 seconds later -> expired
        val action = engine.evaluateTick(
            isPlaying = true,
            currentTime = 500.0,
            rate = 1.0,
            currentChapter = null,
            now = tickNow,
        )

        assertTrue(action is SleepTickAction.TriggerStop)
        assertEquals(15, (action as SleepTickAction.TriggerStop).rewindSeconds)
        assertEquals(SleepTimerMode.Off, engine.mode.value)
    }

    @Test
    fun `evaluateTick adjusts volume smoothly during fade out`() {
        val now = 1_000_000L
        prefs = prefs.copy(sleepFade = true, sleepFadeDuration = 30, volume = 1.0f)
        engine.setMinutes(1, isPlaying = true, now = now) // Ends at now + 60s

        // 45 seconds later -> 15 seconds remaining out of 30s fade duration
        val tickNow = now + 45_000L
        val action = engine.evaluateTick(
            isPlaying = true,
            currentTime = 500.0,
            rate = 1.0,
            currentChapter = null,
            now = tickNow,
        )

        assertTrue(action is SleepTickAction.AdjustVolume)
        val vol = (action as SleepTickAction.AdjustVolume).volume
        assertTrue("Volume should be faded down: $vol", vol in 0.01f..0.99f)
    }

    @Test
    fun `evaluateTick stops at end of chapter`() {
        val chapter = Chapter(id = 1, start = 0.0, end = 600.0, title = "Chapter 1")
        engine.setMode(SleepTimerMode.EndOfChapter, isPlaying = true)

        val action = engine.evaluateTick(
            isPlaying = true,
            currentTime = 599.7, // reached end - 0.3s
            rate = 1.0,
            currentChapter = chapter,
        )

        assertTrue(action is SleepTickAction.TriggerStop)
        assertEquals(SleepTimerMode.Off, engine.mode.value)
    }

    @Test
    fun `shiftBy adjusts endsAt timestamp for pause duration`() {
        val now = 1_000_000L
        engine.setMinutes(10, isPlaying = true, now = now)
        val originalEnd = (engine.mode.value as SleepTimerMode.Minutes).endsAtMillis

        // Paused for 5000ms
        engine.shiftBy(5000L)
        val newEnd = (engine.mode.value as SleepTimerMode.Minutes).endsAtMillis

        assertEquals(originalEnd + 5000L, newEnd)
    }
}
