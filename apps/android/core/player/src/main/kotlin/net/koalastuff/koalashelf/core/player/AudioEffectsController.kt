package net.koalastuff.koalashelf.core.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import net.koalastuff.koalashelf.core.data.prefs.KoalaPrefs
import kotlin.math.roundToInt

/**
 * Manages native audio effects on Android:
 * - `ChannelMixingAudioProcessor` for downmixing stereo to mono.
 * - `LoudnessEnhancer` for volume boost and leveling/commute dynamics.
 * - `Equalizer` for voice boost, low-rumble reduction, and scene EQ curves (warmth, night, commute).
 */
@OptIn(UnstableApi::class)
class AudioEffectsController {
    val channelMixingAudioProcessor = ChannelMixingAudioProcessor()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    fun applyEffects(audioSessionId: Int, prefs: KoalaPrefs) {
        // 1. Mono downmix processor (software PCM pipeline)
        applyMono(prefs.mono)

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return

        if (currentAudioSessionId != audioSessionId) {
            releaseHardwareEffects()
            currentAudioSessionId = audioSessionId
        }

        // 2. LoudnessEnhancer for volume boost (1.0x .. 2.0x -> up to +800 mB) & leveling
        runCatching {
            val needBoost = prefs.boost > 1.01f || prefs.leveling || prefs.soundProfile == "night" || prefs.soundProfile == "commute" || prefs.soundProfile == "warmth"
            if (needBoost) {
                val enhancer = loudnessEnhancer ?: LoudnessEnhancer(audioSessionId).also { loudnessEnhancer = it }
                val boostMb = ((prefs.boost - 1f) * 800f).roundToInt()
                val profileMb = when (prefs.soundProfile) {
                    "night" -> 250
                    "commute" -> 450
                    "warmth" -> 160
                    else -> if (prefs.leveling) 250 else 0
                }
                enhancer.setTargetGain((boostMb + profileMb).coerceAtLeast(0))
                enhancer.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }
        }.onFailure { err ->
            Log.w("KoalaAudio", "LoudnessEnhancer effect failed on session $audioSessionId", err)
        }

        // 3. Hardware Equalizer for Scene Profiles & Voice Boost
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
                        // 1. Analog Tape Warmth Profile
                        prefs.soundProfile == "warmth" -> when {
                            centerHz < 160 -> (maxLevel * 0.4f).toInt().toShort()
                            centerHz in 1000..3500 -> (maxLevel * 0.25f).toInt().toShort()
                            centerHz > 6000 -> (minLevel * 0.35f).toInt().toShort()
                            else -> 0.toShort()
                        }
                        // 2. Night Whisper Profile
                        prefs.soundProfile == "night" -> when {
                            centerHz < 150 -> (minLevel * 0.25f).toInt().toShort()
                            centerHz in 1000..2500 -> (maxLevel * 0.3f).toInt().toShort()
                            centerHz > 4500 -> (minLevel * 0.5f).toInt().toShort()
                            else -> 0.toShort()
                        }
                        // 3. Commute Profile
                        prefs.soundProfile == "commute" -> when {
                            centerHz < 180 -> (minLevel * 0.5f).toInt().toShort()
                            centerHz in 1000..4000 -> (maxLevel * 0.7f).toInt().toShort()
                            centerHz > 7000 -> (minLevel * 0.25f).toInt().toShort()
                            else -> 0.toShort()
                        }
                        // 4. Voice Boost / Clear Voices
                        prefs.voiceBoost || prefs.soundProfile == "voice" -> when {
                            centerHz < 120 -> (minLevel * 0.45f).toInt().toShort()
                            centerHz in 1000..4000 -> (maxLevel * 0.55f).toInt().toShort()
                            else -> 0.toShort()
                        }
                        else -> 0.toShort()
                    }
                    eq.setBandLevel(band, target)
                }
                eq.enabled = true
            } else {
                equalizer?.enabled = false
            }
        }.onFailure { err ->
            Log.w("KoalaAudio", "Equalizer effect failed on session $audioSessionId", err)
        }
    }

    fun applyMono(mono: Boolean) {
        runCatching {
            val matrix = if (mono) {
                ChannelMixingMatrix(2, 2, floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f))
            } else {
                ChannelMixingMatrix(2, 2, floatArrayOf(1f, 0f, 0f, 1f))
            }
            channelMixingAudioProcessor.putChannelMixingMatrix(matrix)
        }.onFailure { err ->
            Log.w("KoalaAudio", "Mono mixing matrix failed", err)
        }
    }

    fun release() {
        releaseHardwareEffects()
    }

    private fun releaseHardwareEffects() {
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
