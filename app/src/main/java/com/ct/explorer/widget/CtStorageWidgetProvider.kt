package com.ct.explorer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.ct.explorer.MainActivity
import com.ct.explorer.R
import com.ct.explorer.data.model.FileItem

class CtStorageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val EXTRA_WIDGET_TARGET = "EXTRA_WIDGET_TARGET"
        const val TARGET_CLEANER = "CLEANER"
        const val TARGET_STORAGE = "STORAGE"
        const val TARGET_FAST_SHARE = "FAST_SHARE"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CtStorageWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_ct_storage)

            val (usedBytes, totalBytes, percent) = try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val total = stat.totalBytes.coerceAtLeast(1L)
                val avail = stat.availableBytes
                val used = (total - avail).coerceAtLeast(0L)
                val pct = ((used * 100L) / total).toInt().coerceIn(0, 100)
                Triple(used, total, pct)
            } catch (_: Exception) {
                Triple(26L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, 41)
            }

            views.setTextViewText(
                R.id.widget_subtitle,
                "${FileItem.formatBytes(usedBytes)} used of ${FileItem.formatBytes(totalBytes)}"
            )
            views.setTextViewText(R.id.widget_percent, "$percent%")
            views.setProgressBar(R.id.widget_progress, 100, percent, false)

            views.setOnClickPendingIntent(
                R.id.widget_btn_cleaner,
                buildLaunchPendingIntent(context, TARGET_CLEANER, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_storage,
                buildLaunchPendingIntent(context, TARGET_STORAGE, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_share,
                buildLaunchPendingIntent(context, TARGET_FAST_SHARE, 103)
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                buildLaunchPendingIntent(context, TARGET_STORAGE, 104)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildLaunchPendingIntent(context: Context, target: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra(EXTRA_WIDGET_TARGET, target)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
