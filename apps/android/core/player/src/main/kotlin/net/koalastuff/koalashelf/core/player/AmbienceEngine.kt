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
     * Synthesizes a clean analog needle drop sound cue when playback starts (`sound.ts`),
     * without continuous white-noise hiss.
     */
    fun playNeedleDropCue() {
        scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                val numSamples = (sampleRate * 0.42).toInt()
                val samples = FloatArray(numSamples)
                val rng = Random(System.nanoTime())

                // 1. Stylus contact click + warm tonearm resonance thump at 12ms
                val contactStart = (sampleRate * 0.012).toInt()
                val thumpLen = (sampleRate * 0.075).toInt()
                for (k in 0 until thumpLen) {
                    val idx = contactStart + k
                    if (idx >= numSamples) break
                    val dt = k.toDouble() / sampleRate
                    val freq = (68.0 - dt * 320.0).coerceAtLeast(36.0)
                    val thump = kotlin.math.sin(2.0 * Math.PI * freq * dt) * kotlin.math.exp(-dt * 42.0) * 0.55
                    val click = if (dt < 0.0025) {
                        kotlin.math.sin(2.0 * Math.PI * 2900.0 * dt) * kotlin.math.exp(-dt * 1800.0) * 0.45
                    } else 0.0
                    samples[idx] += (thump + click).toFloat()
                }

                // 2. Brief groove-wall catch micro-scratch (7 discrete stylus impulses)
                val scratchImpulses = 7
                val grainLen = (sampleRate * 0.0018).toInt()
                for (s in 0 until scratchImpulses) {
                    val onsetSec = 0.04 + s * 0.0034 + rng.nextDouble() * 0.001
                    val start = (onsetSec * sampleRate).toInt()
                    val env = kotlin.math.sin(((s + 0.5) / scratchImpulses) * Math.PI)
                    val freq = 1700.0 + rng.nextDouble() * 1500.0
                    val sign = if (s % 2 == 0) 1.0 else -1.0
                    val amp = (0.22 + rng.nextDouble() * 0.14) * env * sign
                    for (k in 0 until grainLen) {
                        val idx = start + k
                        if (idx >= numSamples) break
                        val dt = k.toDouble() / sampleRate
                        samples[idx] += (kotlin.math.sin(2.0 * Math.PI * freq * dt) * kotlin.math.exp(-dt * 2100.0) * amp).toFloat()
                    }
                }

                // 3. Sparse lead-in vinyl dust ticks
                val leadInSecs = doubleArrayOf(0.12, 0.21, 0.32)
                val tickLen = (sampleRate * 0.0025).toInt()
                for (sec in leadInSecs) {
                    val start = ((sec + rng.nextDouble() * 0.015) * sampleRate).toInt()
                    val freq = 900.0 + rng.nextDouble() * 2200.0
                    val decay = 1600.0 + rng.nextDouble() * 1600.0
                    val amp = (0.20 + rng.nextDouble() * 0.22) * if (rng.nextBoolean()) 1.0 else -1.0
                    for (k in 0 until tickLen) {
                        val idx = start + k
                        if (idx >= numSamples) break
                        val dt = k.toDouble() / sampleRate
                        samples[idx] += (kotlin.math.sin(2.0 * Math.PI * freq * dt) * kotlin.math.exp(-dt * decay) * amp).toFloat()
                    }
                }

                val pcm = ShortArray(numSamples) { i ->
                    (samples[i].coerceIn(-1f, 1f) * 0.28f * Short.MAX_VALUE).toInt().toShort()
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
                    delay(450L)
                } finally {
                    runCatching { track.release() }
                }
            }
        }
    }

    /**
     * Synthesizes warm ambient vinyl surface crackle and dust pops while playing (`sound.ts`),
     * with zero continuous white-noise hiss between transients.
     */
    fun updateVinylCrackleLoop(isPlaying: Boolean, enabled: Boolean, volume: Float) {
        crackleJob?.cancel()
        crackleJob = null
        if (!isPlaying || !enabled) return

        crackleJob = scope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 22050
                val chunkSamples = sampleRate / 2 // 0.5s chunks
                val floatBuf = FloatArray(chunkSamples)
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
                    val tickLen = (sampleRate * 0.0012).toInt()
                    val popLen = (sampleRate * 0.0036).toInt()
                    while (isActive) {
                        java.util.Arrays.fill(floatBuf, 0f)
                        // Fine surface micro-ticks (~11 per 0.5s chunk)
                        val ticks = 9 + rng.nextInt(5)
                        for (t in 0 until ticks) {
                            val start = rng.nextInt((chunkSamples - tickLen).coerceAtLeast(1))
                            val freq = 2600.0 + rng.nextDouble() * 2000.0
                            val decay = 3200.0 + rng.nextDouble() * 2200.0
                            val amp = (0.18 + rng.nextDouble() * 0.40) * if (rng.nextBoolean()) 1.0 else -1.0
                            for (k in 0 until tickLen) {
                                val dt = k.toDouble() / sampleRate
                                floatBuf[start + k] += (kotlin.math.sin(2.0 * Math.PI * freq * dt) * kotlin.math.exp(-dt * decay) * amp).toFloat()
                            }
                        }
                        // Warm analog dust pops (~2 per 0.5s chunk)
                        val pops = 1 + rng.nextInt(3)
                        for (p in 0 until pops) {
                            val start = rng.nextInt((chunkSamples - popLen).coerceAtLeast(1))
                            val freq = 520.0 + rng.nextDouble() * 560.0
                            val decay = 950.0 + rng.nextDouble() * 600.0
                            val amp = (0.35 + rng.nextDouble() * 0.50) * if (rng.nextBoolean()) 1.0 else -1.0
                            for (k in 0 until popLen) {
                                val dt = k.toDouble() / sampleRate
                                floatBuf[start + k] += (kotlin.math.sin(2.0 * Math.PI * freq * dt) * kotlin.math.exp(-dt * decay) * amp).toFloat()
                            }
                        }
                        for (i in 0 until chunkSamples) {
                            pcm[i] = ((floatBuf[i] * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
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
                val samplesPerRev = (sampleRate * 1.8).toInt()

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val fadeOut = (1.0 - (t / durationSec)).coerceIn(0.0, 1.0)
                    // Periodic soft groove thump + click every 1.8s without steady noise hiss
                    val revPos = i % samplesPerRev
                    val dt = revPos.toDouble() / sampleRate
                    val thump = if (dt < 0.045) {
                        kotlin.math.sin(2.0 * Math.PI * (52.0 - dt * 280.0) * dt) * kotlin.math.exp(-dt * 55.0) * 0.42
                    } else 0.0
                    val click = if (dt in 0.002..0.005) {
                        kotlin.math.sin(2.0 * Math.PI * 2400.0 * (dt - 0.002)) * kotlin.math.exp(-(dt - 0.002) * 1800.0) * 0.18
                    } else 0.0
                    pcm[i] = (((thump + click) * fadeOut * 0.25).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
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
