package com.ct.explorer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object CloudOAuthHelper {

    // Registered Google OAuth 2.0 Client ID
    const val GOOGLE_CLIENT_ID = "449846601958-t36qjr9pi1toaahhcrg0sel6pet5jrbd.apps.googleusercontent.com"
    const val REDIRECT_URI = "com.pkstudio.ctexplorer.app:/oauth2redirect"

    fun buildGoogleAuthUrl(): String {
        val scopes = listOf(
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
        ).joinToString(" ")

        return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("include_granted_scopes", "true")
            .appendQueryParameter("prompt", "select_account")
            .build()
            .toString()
    }

    suspend fun exchangeCodeForToken(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://oauth2.googleapis.com/token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 12000

            val postData = "client_id=${URLEncoder.encode(GOOGLE_CLIENT_ID, "UTF-8")}" +
                    "&code=${URLEncoder.encode(code, "UTF-8")}" +
                    "&grant_type=authorization_code" +
                    "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"

            conn.outputStream.bufferedWriter().use { it.write(postData) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val json = conn.inputStream.bufferedReader().readText()
                val token = JSONObject(json).optString("access_token")
                if (token.isNotEmpty()) {
                    Result.success(token)
                } else {
                    Result.failure(Exception("No access_token found in response"))
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Result.failure(Exception("Token exchange failed ($responseCode): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchOAuth(context: Context, authUrl: String): Boolean {
        return try {
            val uri = Uri.parse(authUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun extractToken(uri: Uri): String? {
        // 1. Check fragment (Implicit grant: #access_token=...)
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&")
            for (p in params) {
                val pair = p.split("=")
                if (pair.size == 2 && pair[0].equals("access_token", ignoreCase = true)) {
                    return Uri.decode(pair[1])
                }
            }
        }

        // 2. Check query parameter (?access_token=...)
        return uri.getQueryParameter("access_token")
    }

    fun extractCode(uri: Uri): String? {
        return uri.getQueryParameter("code")
    }

    fun extractError(uri: Uri): String? {
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&")
            for (p in params) {
                val pair = p.split("=")
                if (pair.size == 2 && pair[0].equals("error", ignoreCase = true)) {
                    return Uri.decode(pair[1])
                }
            }
        }
        return uri.getQueryParameter("error")
            ?: uri.getQueryParameter("error_description")
    }
}
