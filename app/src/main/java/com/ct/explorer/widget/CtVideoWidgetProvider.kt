package com.ct.explorer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.RemoteViews
import com.ct.explorer.MainActivity
import com.ct.explorer.R

/**
 * Authentic Desktop Video Player Widget for Cent File Manager.
 * Designed to look and feel like a modern, sleek media player on the Android desktop / home screen.
 * Tapping Play launches the built-in Video Player with videos loaded.
 */
class CtVideoWidgetProvider : AppWidgetProvider() {

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
        const val TARGET_VIDEO_PLAYER = "VIDEO_PLAYER"
        const val TARGET_VIDEO_GALLERY = "VIDEO_GALLERY"

        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, CtVideoWidgetProvider::class.java))
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            } catch (_: Exception) {}
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_ct_video)

            // Query MediaStore for latest video info and video count
            var latestVideoTitle = "Tap to Play All Videos"
            var videoCountText = "Cent Media Player • Ready"
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION
                )
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )
                cursor?.use {
                    val count = it.count
                    if (it.moveToFirst()) {
                        val nameCol = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                        if (nameCol != -1) {
                            val name = it.getString(nameCol)
                            if (!name.isNullOrBlank()) {
                                latestVideoTitle = name
                            }
                        }
                    }
                    if (count > 0) {
                        videoCountText = "$count Videos • Ready to Play"
                    }
                }
            } catch (_: Exception) {
                // Fallback gracefully without throwing
            }

            views.setTextViewText(R.id.widget_video_current_title, latestVideoTitle)
            views.setTextViewText(R.id.widget_video_current_count, videoCountText)

            // PendingIntent for Launching Built-in Video Player
            val playPendingIntent = buildLaunchPendingIntent(context, TARGET_VIDEO_PLAYER, 201)
            views.setOnClickPendingIntent(R.id.widget_btn_play_video, playPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_video_play_icon, playPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_video_screen, playPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_video_root, playPendingIntent)

            // PendingIntent for Launching All Videos Gallery
            val galleryPendingIntent = buildLaunchPendingIntent(context, TARGET_VIDEO_GALLERY, 202)
            views.setOnClickPendingIntent(R.id.widget_btn_all_videos, galleryPendingIntent)

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

        /**
         * Pin this Video Player Widget to the user's desktop / home screen.
         */
        fun requestPinWidget(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, CtVideoWidgetProvider::class.java)
                    if (manager.isRequestPinAppWidgetSupported) {
                        return manager.requestPinAppWidget(provider, null, null)
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }
}
