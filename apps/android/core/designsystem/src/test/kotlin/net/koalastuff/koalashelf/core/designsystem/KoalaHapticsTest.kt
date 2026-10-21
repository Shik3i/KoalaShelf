package net.koalastuff.koalashelf.core.designsystem

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class KoalaHapticsTest {

    @Test
    fun haptics_canBeInstantiatedAndMethodsDoNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val haptics = AndroidKoalaHaptics(context, enabledProvider = { true })

        // All sensory tactile primitives should execute safely without crashing
        haptics.needleDrop()
        haptics.grooveTick()
        haptics.grooveTick() // Rapid subsequent call (throttled)
        haptics.chapterSnap()
        haptics.sleepArmed()
        haptics.boundary()

        assertNotNull(haptics)
    }

    @Test
    fun haptics_disabled_safelyNoOps() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val disabledHaptics = AndroidKoalaHaptics(context, enabledProvider = { false })

        disabledHaptics.needleDrop()
        disabledHaptics.grooveTick()
        disabledHaptics.chapterSnap()
        disabledHaptics.sleepArmed()
        disabledHaptics.boundary()

        assertNotNull(disabledHaptics)
    }

    @Test
    fun noOpHaptics_safelyNoOps() {
        NoOpKoalaHaptics.needleDrop()
        NoOpKoalaHaptics.grooveTick()
        NoOpKoalaHaptics.chapterSnap()
        NoOpKoalaHaptics.sleepArmed()
        NoOpKoalaHaptics.boundary()
    }
}
