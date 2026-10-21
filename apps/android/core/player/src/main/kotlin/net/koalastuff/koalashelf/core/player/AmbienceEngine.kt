package net.koalastuff.koalashelf.core.player

import android.media.AudioFormat
import android.media.AudioTrack as AndroidAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

/**
 * Audio synthesis engine for analog vinyl tactile acoustic cues:
 * - Vintage needle drop thump, click, and surface hiss when starting playback.
 * - Continuous ambient vinyl surface micro-crackle and warmth while playing.
 */
class AmbienceEngine(
    private val scope: CoroutineScope,
) {
    private var crackleJob: Job? = null

    /**
     * Synthesizes a subtle analog needle drop sound cue when playback starts (`sound.ts`).
     */
    fun playNeedleDropCue() {
        scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                val numSamples = (sampleRate * 0.18).toInt()
                val pcm = ShortArray(numSamples)
                val rng = Random(System.nanoTime())
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val thump = kotlin.math.sin(2.0 * Math.PI * 65.0 * t) * kotlin.math.exp(-t * 32.0) * 0.35
                    val click = if (i in 400..520) (rng.nextDouble() * 2.0 - 1.0) * 0.25 else 0.0
                    val hiss = (rng.nextDouble() * 2.0 - 1.0) * 0.03 * kotlin.math.exp(-t * 10.0)
                    pcm[i] = ((thump + click + hiss).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AndroidAudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AndroidAudioTrack.MODE_STATIC)
                    .build()
                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    delay(220L)
                } finally {
                    runCatching { track.release() }
                }
            }
        }
    }

    /**
     * Synthesizes warm ambient vinyl surface crackle while playing (`sound.ts`).
     */
    fun updateVinylCrackleLoop(isPlaying: Boolean, enabled: Boolean, volume: Float) {
        crackleJob?.cancel()
        crackleJob = null
        if (!isPlaying || !enabled) return

        crackleJob = scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 16000
                val chunkSamples = sampleRate / 4
                val pcm = ShortArray(chunkSamples)
                val rng = Random(System.nanoTime())
                val minBuf = max(chunkSamples * 2, AndroidAudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                val track = AndroidAudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AndroidAudioTrack.MODE_STREAM)
                    .build()
                track.play()
                try {
                    val gain = volume.coerceIn(0.01f, 0.15f)
                    while (isActive) {
                        for (i in 0 until chunkSamples) {
                            val pop = if (rng.nextFloat() < 0.0018f) (rng.nextFloat() * 2f - 1f) * 0.65f else 0f
                            val warmth = (rng.nextFloat() * 2f - 1f) * 0.06f
                            pcm[i] = (((pop + warmth) * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        }
                        track.write(pcm, 0, chunkSamples)
                    }
                } finally {
                    runCatching {
                        track.stop()
                        track.release()
                    }
                }
            }
        }
    }

    /**
     * Synthesizes 2 locked-groove vinyl revolutions (1.8s per rev at 33⅓ RPM) with periodic soft clicks
     * as an analog end-of-side bedtime transition before the sleep timer puts playback to sleep.
     */
    fun playRunOutGrooveCue(onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                // 2 revolutions at 1.8s each = 3.6s
                val durationSec = 3.6
                val totalSamples = (sampleRate * durationSec).toInt()
                val pcm = ShortArray(totalSamples)
                val rng = Random(System.nanoTime())
                val samplesPerRev = (sampleRate * 1.8).toInt()

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val fadeOut = (1.0 - (t / durationSec)).coerceIn(0.0, 1.0)
                    // Periodic soft groove thump every 1.8s
                    val revPos = i % samplesPerRev
                    val thump = if (revPos in 0..600) {
                        val dt = revPos.toDouble() / sampleRate
                        kotlin.math.sin(2.0 * Math.PI * 45.0 * dt) * kotlin.math.exp(-dt * 20.0) * 0.3
                    } else 0.0
                    val hiss = (rng.nextDouble() * 2.0 - 1.0) * 0.025
                    pcm[i] = (((thump + hiss) * fadeOut).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                val track = AndroidAudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AndroidAudioTrack.MODE_STATIC)
                    .build()
                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    delay(3700L)
                } finally {
                    runCatching { track.release() }
                    onComplete()
                }
            }.onFailure {
                onComplete()
            }
        }
    }

    fun release() {
        crackleJob?.cancel()
        crackleJob = null
    }
}
