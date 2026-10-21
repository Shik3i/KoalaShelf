package net.koalastuff.koalashelf.core.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs

import kotlin.math.roundToInt

/**
 * Manages native hardware audio effects on Android:
 * - `LoudnessEnhancer` for volume boost and leveling/commute dynamics.
 * - `Equalizer` for voice boost, high-pass rumble reduction, and night whisper EQ curves.
 */
class AudioEffectsController {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    fun applyEffects(audioSessionId: Int, prefs: KoalaPrefs) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return

        if (currentAudioSessionId != audioSessionId) {
            release()
            currentAudioSessionId = audioSessionId
        }

        // LoudnessEnhancer for volume boost (1.0x .. 2.0x -> up to +600 mB) & leveling
        runCatching {
            val needBoost = prefs.boost > 1.01f || prefs.leveling || prefs.soundProfile == "night" || prefs.soundProfile == "commute" || prefs.soundProfile == "warmth"
            if (needBoost) {
                val enhancer = loudnessEnhancer ?: LoudnessEnhancer(audioSessionId).also { loudnessEnhancer = it }
                val boostMb = ((prefs.boost - 1f) * 600f).roundToInt()
                val profileMb = when (prefs.soundProfile) {
                    "night" -> 250
                    "commute" -> 350
                    "warmth" -> 120
                    else -> if (prefs.leveling) 180 else 0
                }
                enhancer.setTargetGain(boostMb + profileMb)
                enhancer.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }
        }

        // Equalizer for Voice Boost / Acoustic Scene Profiles
        runCatching {
            val wantVoice = prefs.voiceBoost || prefs.soundProfile != "off"
            if (wantVoice) {
                val eq = equalizer ?: Equalizer(0, audioSessionId).also { equalizer = it }
                val numBands = eq.numberOfBands
                val range = eq.bandLevelRange
                val minLevel = range[0]
                val maxLevel = range[1]
                for (b in 0 until numBands) {
                    val band = b.toShort()
                    val centerHz = eq.getCenterFreq(band) / 1000
                    val target = when {
                        // Analog Tape Warmth Profile
                        prefs.soundProfile == "warmth" && centerHz < 150 -> (maxLevel / 3).toShort()
                        prefs.soundProfile == "warmth" && centerHz in 1000..3500 -> (maxLevel / 4).toShort()
                        prefs.soundProfile == "warmth" && centerHz > 6000 -> (minLevel / 4).toShort()
                        // Cut low rumble (< 120 Hz)
                        centerHz < 120 -> (minLevel / 2).toShort()
                        // Presence boost for human voice (1 kHz - 4 kHz)
                        centerHz in 1000..4000 -> (maxLevel / 2).toShort()
                        // Soften harsh highs in Night Whisper profile
                        prefs.soundProfile == "night" && centerHz > 6000 -> (minLevel / 3).toShort()
                        else -> 0.toShort()
                    }
                    eq.setBandLevel(band, target)
                }
                eq.enabled = true
            } else {
                equalizer?.enabled = false
            }
        }
    }

    fun release() {
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }
        runCatching {
            equalizer?.release()
            equalizer = null
        }
        currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
