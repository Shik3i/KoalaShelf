package net.koalastuff.koalashelf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import net.koalastuff.koalashelf.MainActivity
import net.koalastuff.koalashelf.R
import net.koalastuff.koalashelf.core.player.KoalaPlayerController
import org.koin.core.context.GlobalContext

/**
 * Live Turntable Home Screen Widget (`4x2` and `2x2` responsive):
 * Displays currently playing audiobook title, author, spinning vinyl deck aesthetic,
 * and provides instant play/pause and skip back/forward controls.
 */
class KoalaTurntableWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val controller = runCatching { GlobalContext.get().get<KoalaPlayerController>() }.getOrNull()

        when (intent.action) {
            ACTION_STATE_CHANGED -> {
                updateAll(context)
            }
            ACTION_PLAY_PAUSE -> {
                if (controller?.playing?.value == true) {
                    controller.pause()
                } else {
                    controller?.play()
                }
                updateAll(context)
            }
            ACTION_SKIP_BACK -> {
                controller?.back()
                updateAll(context)
            }
            ACTION_SKIP_FWD -> {
                controller?.forward()
                updateAll(context)
            }
        }
    }

    companion object {
        const val ACTION_STATE_CHANGED = "net.koalastuff.koalashelf.widget.STATE_CHANGED"
        const val ACTION_PLAY_PAUSE = "net.koalastuff.koalashelf.widget.PLAY_PAUSE"
        const val ACTION_SKIP_BACK = "net.koalastuff.koalashelf.widget.SKIP_BACK"
        const val ACTION_SKIP_FWD = "net.koalastuff.koalashelf.widget.SKIP_FWD"

        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, KoalaTurntableWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component) ?: return
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_turntable)
            val controller = runCatching { GlobalContext.get().get<KoalaPlayerController>() }.getOrNull()

            val item = controller?.item?.value
            val isPlaying = controller?.playing?.value == true

            views.setTextViewText(R.id.widget_title, item?.title ?: "KoalaShelf")
            views.setTextViewText(R.id.widget_author, item?.author ?: "Select an audiobook")

            val playPauseRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            // Click root -> Open App
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, contentPendingIntent)

            // Controls
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                getBroadcastPendingIntent(context, ACTION_PLAY_PAUSE, 101),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_skip_back,
                getBroadcastPendingIntent(context, ACTION_SKIP_BACK, 102),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_skip_fwd,
                getBroadcastPendingIntent(context, ACTION_SKIP_FWD, 103),
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, KoalaTurntableWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
