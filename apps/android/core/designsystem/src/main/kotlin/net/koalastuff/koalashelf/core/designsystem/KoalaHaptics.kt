package net.koalastuff.koalashelf.core.designsystem

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Tactile acoustic-haptic sensory vocabulary for vinyl controls:
 * needle drop, tonearm groove tracking ticks, chapter snap, sleep timer arming, and track boundaries.
 */
interface KoalaHaptics {
    fun needleDrop()
    fun grooveTick()
    fun chapterSnap()
    fun sleepArmed()
    fun boundary()
}

object NoOpKoalaHaptics : KoalaHaptics {
    override fun needleDrop() {}
    override fun grooveTick() {}
    override fun chapterSnap() {}
    override fun sleepArmed() {}
    override fun boundary() {}
}

@SuppressLint("MissingPermission")
class AndroidKoalaHaptics(
    private val context: Context,
    private val enabledProvider: () -> Boolean = { true },
) : KoalaHaptics {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastGrooveTickMillis = 0L

    private fun isHapticsAllowed(): Boolean {
        if (!enabledProvider()) return false
        val vib = vibrator ?: return false
        if (!vib.hasVibrator()) return false
        return runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
        }.getOrDefault(true)
    }

    override fun needleDrop() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_TICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 40)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createOneShot(25L, 160))
            }
        }
    }

    override fun grooveTick() {
        if (!isHapticsAllowed()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGrooveTickMillis < 35L) return
        lastGrooveTickMillis = now

        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
            ) {
                vib.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f)
                        .compose(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createOneShot(10L, 80))
            }
        }
    }

    override fun chapterSnap() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                vib.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                        .compose(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createOneShot(20L, 200))
            }
        }
    }

    override fun sleepArmed() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                vib.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 60)
                        .compose(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 40, 20), intArrayOf(0, 100, 0, 180), -1))
            }
        }
    }

    override fun boundary() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            ) {
                vib.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f)
                        .compose(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(VibrationEffect.createOneShot(30L, 220))
            }
        }
    }
}

val LocalKoalaHaptics = staticCompositionLocalOf<KoalaHaptics> { NoOpKoalaHaptics }

@Composable
fun ProvideKoalaHaptics(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptics = remember(context, enabled) {
        AndroidKoalaHaptics(context) { enabled }
    }
    CompositionLocalProvider(LocalKoalaHaptics provides haptics) {
        content()
    }
}
