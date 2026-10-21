package net.koalastuff.koalashelf.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import net.koalastuff.koalashelf.core.data.util.SleepForecast
import net.koalastuff.koalashelf.core.data.util.forecastSleepWindow
import net.koalastuff.koalashelf.core.data.util.inNightWindow
import net.koalastuff.koalashelf.core.network.Chapter
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

sealed interface SleepTimerMode {
    data object Off : SleepTimerMode
    data object EndOfChapter : SleepTimerMode
    data class Minutes(val minutes: Int, val endsAtMillis: Long) : SleepTimerMode
    data class SmartWindow(
        val minMinutes: Int,
        val maxMinutes: Int,
        val minAtMillis: Long,
        val maxAtMillis: Long,
    ) : SleepTimerMode
}

sealed interface SleepTickAction {
    data object None : SleepTickAction
    data class AdjustVolume(val volume: Float) : SleepTickAction
    data class TriggerStop(val rewindSeconds: Int) : SleepTickAction
}

/**
 * Pure Kotlin Sleep Timer engine managing sleep modes, countdowns, smart chapter window forecasting,
 * nighttime auto-timer arming, and smooth volume fade-out calculations.
 */
class SleepTimerEngine(
    private val getPrefs: () -> KoalaPrefs,
) {
    private val _mode = MutableStateFlow<SleepTimerMode>(SleepTimerMode.Off)
    val mode: StateFlow<SleepTimerMode> = _mode.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0.0)
    val remainingSeconds: StateFlow<Double> = _remainingSeconds.asStateFlow()

    var pausedAtMillis: Long? = null

    fun setMode(
        newMode: SleepTimerMode,
        isPlaying: Boolean,
        currentTime: Double = 0.0,
        rate: Double = 1.0,
        currentChapter: Chapter? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        _mode.value = newMode
        pausedAtMillis = if (!isPlaying && newMode !is SleepTimerMode.Off) now else null
        updateRemaining(currentTime, rate, currentChapter, now)
    }

    fun setMinutes(
        minutes: Int,
        isPlaying: Boolean,
        currentTime: Double = 0.0,
        rate: Double = 1.0,
        currentChapter: Chapter? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (minutes <= 0) {
            setMode(SleepTimerMode.Off, isPlaying, currentTime, rate, currentChapter, now)
        } else {
            setMode(
                SleepTimerMode.Minutes(
                    minutes = minutes,
                    endsAtMillis = now + minutes * 60_000L,
                ),
                isPlaying,
                currentTime,
                rate,
                currentChapter,
                now,
            )
        }
    }

    fun setSmartWindow(
        minMinutes: Int,
        maxMinutes: Int,
        isPlaying: Boolean,
        currentTime: Double = 0.0,
        rate: Double = 1.0,
        currentChapter: Chapter? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        setMode(
            SleepTimerMode.SmartWindow(
                minMinutes = minMinutes,
                maxMinutes = maxMinutes,
                minAtMillis = now + minMinutes * 60_000L,
                maxAtMillis = now + maxMinutes * 60_000L,
            ),
            isPlaying,
            currentTime,
            rate,
            currentChapter,
            now,
        )
    }

    fun extend(
        extraMinutes: Int,
        currentTime: Double,
        rate: Double,
        currentChapter: Chapter?,
        now: Long = System.currentTimeMillis(),
    ) {
        val extraMs = extraMinutes * 60_000L
        when (val s = _mode.value) {
            SleepTimerMode.Off -> setMinutes(extraMinutes, isPlaying = true, currentTime = currentTime, rate = rate, currentChapter = currentChapter, now = now)
            is SleepTimerMode.Minutes -> {
                _mode.value = s.copy(
                    minutes = s.minutes + extraMinutes,
                    endsAtMillis = s.endsAtMillis + extraMs,
                )
            }
            is SleepTimerMode.SmartWindow -> {
                _mode.value = s.copy(
                    minAtMillis = s.minAtMillis + extraMs,
                    maxAtMillis = s.maxAtMillis + extraMs,
                )
            }
            SleepTimerMode.EndOfChapter -> {
                val chEnd = currentChapter?.end ?: currentTime
                val leftSec = max(0.0, (chEnd - currentTime) / max(0.1, rate))
                val endsAt = now + (leftSec * 1000.0).roundToLong() + extraMs
                _mode.value = SleepTimerMode.Minutes(minutes = extraMinutes, endsAtMillis = endsAt)
            }
        }
        updateRemaining(currentTime, rate, currentChapter, now)
    }

    fun shiftBy(deltaMs: Long) {
        when (val s = _mode.value) {
            is SleepTimerMode.Minutes -> _mode.value = s.copy(endsAtMillis = s.endsAtMillis + deltaMs)
            is SleepTimerMode.SmartWindow -> _mode.value = s.copy(
                minAtMillis = s.minAtMillis + deltaMs,
                maxAtMillis = s.maxAtMillis + deltaMs,
            )
            else -> Unit
        }
    }

    fun checkAutoSleepOnStart(
        isPlaying: Boolean,
        nowTime: LocalTime = LocalTime.now(),
        currentTime: Double = 0.0,
        rate: Double = 1.0,
        currentChapter: Chapter? = null,
        now: Long = System.currentTimeMillis(),
    ): SleepTimerMode? {
        val prefs = getPrefs()
        if (!prefs.autoSleep || _mode.value !is SleepTimerMode.Off || isPlaying) return null
        val nowMins = nowTime.hour * 60 + nowTime.minute
        if (!inNightWindow(nowMins, prefs.autoSleepFrom, prefs.autoSleepTo)) return null

        val newMode = when (prefs.autoSleepKind) {
            "chapter" -> SleepTimerMode.EndOfChapter
            "smart" -> SleepTimerMode.SmartWindow(
                minMinutes = prefs.sleepMin,
                maxMinutes = prefs.sleepMax,
                minAtMillis = now + prefs.sleepMin * 60_000L,
                maxAtMillis = now + prefs.sleepMax * 60_000L,
            )
            else -> SleepTimerMode.Minutes(
                minutes = prefs.autoSleepMinutes,
                endsAtMillis = now + prefs.autoSleepMinutes * 60_000L,
            )
        }
        setMode(newMode, isPlaying = true, currentTime = currentTime, rate = rate, currentChapter = currentChapter, now = now)
        return newMode
    }

    fun calculateForecast(chapters: List<Chapter>, currentTime: Double, rate: Double, now: Long = System.currentTimeMillis()): SleepForecast? {
        val s = _mode.value as? SleepTimerMode.SmartWindow ?: return null
        val base = pausedAtMillis ?: now
        return forecastSleepWindow(
            chapters = chapters,
            currentTime = currentTime,
            rate = rate.toFloat(),
            minLeftSeconds = (s.minAtMillis - base) / 1000.0,
            maxLeftSeconds = (s.maxAtMillis - base) / 1000.0,
        )
    }

    fun updateRemaining(currentTime: Double, rate: Double, currentChapter: Chapter?, now: Long = System.currentTimeMillis(), forecast: SleepForecast? = null) {
        val base = pausedAtMillis ?: now
        _remainingSeconds.value = when (val s = _mode.value) {
            SleepTimerMode.Off -> 0.0
            SleepTimerMode.EndOfChapter -> {
                if (currentChapter != null) max(0.0, (currentChapter.end - currentTime) / max(0.1, rate)) else 0.0
            }
            is SleepTimerMode.Minutes -> max(0.0, (s.endsAtMillis - base) / 1000.0)
            is SleepTimerMode.SmartWindow -> forecast?.seconds ?: max(0.0, (s.maxAtMillis - base) / 1000.0)
        }
    }

    fun evaluateTick(
        isPlaying: Boolean,
        currentTime: Double,
        rate: Double,
        currentChapter: Chapter?,
        now: Long = System.currentTimeMillis(),
        forecast: SleepForecast? = null,
    ): SleepTickAction {
        val s = _mode.value
        if (s is SleepTimerMode.Off) {
            _remainingSeconds.value = 0.0
            return SleepTickAction.None
        }
        updateRemaining(currentTime, rate, currentChapter, now, forecast)
        if (!isPlaying) return SleepTickAction.None

        val prefs = getPrefs()
        val fadeSec = prefs.sleepFadeDuration.toDouble()
        val baseVolume = prefs.volume

        return when (s) {
            SleepTimerMode.EndOfChapter -> {
                val ch = currentChapter ?: return SleepTickAction.None
                val left = (ch.end - currentTime) / max(0.1, rate)
                if (currentTime >= ch.end - 0.4) {
                    _mode.value = SleepTimerMode.Off
                    SleepTickAction.TriggerStop(rewindSeconds = 0)
                } else if (prefs.sleepFade && left in 0.0..fadeSec) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    SleepTickAction.AdjustVolume(baseVolume * ratio)
                } else {
                    SleepTickAction.AdjustVolume(baseVolume)
                }
            }
            is SleepTimerMode.SmartWindow -> {
                val ch = currentChapter
                if (now >= s.minAtMillis && ch != null && currentTime >= ch.end - 0.4) {
                    _mode.value = SleepTimerMode.Off
                    return SleepTickAction.TriggerStop(rewindSeconds = 0)
                }
                val left = (s.maxAtMillis - now) / 1000.0
                if (left <= 0.0) {
                    _mode.value = SleepTimerMode.Off
                    SleepTickAction.TriggerStop(rewindSeconds = prefs.sleepRewind)
                } else if (prefs.sleepFade && left < fadeSec && forecast?.chapterIndex == null) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    SleepTickAction.AdjustVolume(baseVolume * ratio)
                } else {
                    SleepTickAction.AdjustVolume(baseVolume)
                }
            }
            is SleepTimerMode.Minutes -> {
                val left = (s.endsAtMillis - now) / 1000.0
                if (left <= 0.0) {
                    _mode.value = SleepTimerMode.Off
                    SleepTickAction.TriggerStop(rewindSeconds = prefs.sleepRewind)
                } else if (prefs.sleepFade && left < fadeSec) {
                    val ratio = max(0.01, left / fadeSec).pow(1.5).toFloat()
                    SleepTickAction.AdjustVolume(baseVolume * ratio)
                } else {
                    SleepTickAction.AdjustVolume(baseVolume)
                }
            }
            SleepTimerMode.Off -> SleepTickAction.None
        }
    }
}
