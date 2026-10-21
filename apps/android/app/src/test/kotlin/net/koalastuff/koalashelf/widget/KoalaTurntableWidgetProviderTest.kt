package net.koalastuff.koalashelf.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class KoalaTurntableWidgetProviderTest {

    @Test
    fun widgetProvider_handlesStateChangedBroadcastSafely() {
        val context = RuntimeEnvironment.getApplication()
        val provider = KoalaTurntableWidgetProvider()

        val intent = Intent(KoalaTurntableWidgetProvider.ACTION_STATE_CHANGED)
        provider.onReceive(context, intent)

        assertNotNull(provider)
    }

    @Test
    fun widgetProvider_handlesPlayPauseBroadcastSafely() {
        val context = RuntimeEnvironment.getApplication()
        val provider = KoalaTurntableWidgetProvider()

        val intent = Intent(KoalaTurntableWidgetProvider.ACTION_PLAY_PAUSE)
        provider.onReceive(context, intent)

        assertNotNull(provider)
    }

    @Test
    fun widgetProvider_onUpdateWithEmptyIdsDoesNotCrash() {
        val context = RuntimeEnvironment.getApplication()
        val provider = KoalaTurntableWidgetProvider()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        provider.onUpdate(context, appWidgetManager, intArrayOf())
        assertNotNull(provider)
    }
}
