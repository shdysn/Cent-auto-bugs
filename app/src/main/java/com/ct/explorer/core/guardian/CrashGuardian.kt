package com.ct.explorer.core.guardian

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * CrashGuardian: Zero-Crash Protection System
 * Catches unhandled exceptions, writes structured diagnostic logs,
 * tracks fatal incidents, and protects app integrity.
 */
object CrashGuardian {

    private const val TAG = "CrashGuardian"
    private const val LOG_FILE_NAME = "crash_guardian_log.txt"
    private val crashCount = AtomicInteger(0)
    private var isInitialized = false

    fun install(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashCount.incrementAndGet()
                recordCrash(context, thread, throwable)
                Log.e(TAG, "CrashGuardian intercepted fatal crash on thread [${thread.name}]", throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash in CrashGuardian", e)
            } finally {
                // Pass to default handler or exit safely
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logEntry = buildString {
                appendLine("=== CRASH INCIDENT: $timestamp ===")
                appendLine("Thread: ${thread.name} (ID: ${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("Stack Trace:")
                throwable.stackTrace.take(15).forEach { element ->
                    appendLine("  at $element")
                }
                appendLine("==========================================")
                appendLine()
            }
            file.appendText(logEntry)
        } catch (_: Exception) {}
    }

    fun getCrashCount(context: Context): Int {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return 0
        return try {
            val content = file.readText()
            content.split("=== CRASH INCIDENT:").size - 1
        } catch (_: Exception) {
            0
        }
    }

    fun getRecentCrashLog(context: Context): String {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return "No crashes recorded. System running smoothly."
        return try {
            file.readLines().takeLast(30).joinToString("\n")
        } catch (_: Exception) {
            "Unable to read crash log."
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.delete()
            crashCount.set(0)
        } catch (_: Exception) {}
    }
}
