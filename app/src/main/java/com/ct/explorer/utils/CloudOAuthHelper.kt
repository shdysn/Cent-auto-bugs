package com.ct.explorer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object CloudOAuthHelper {

    // Registered Google OAuth 2.0 Client ID
    const val GOOGLE_CLIENT_ID = "449846601958-t36qjr9pi1toaahhcrg0sel6pet5jrbd.apps.googleusercontent.com"
    const val REDIRECT_URI = "ctexplorer://oauth-callback"

    fun buildGoogleAuthUrl(): String {
        val scopes = listOf(
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
        ).joinToString(" ")

        return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("include_granted_scopes", "true")
            .appendQueryParameter("prompt", "select_account")
            .build()
            .toString()
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

        // 2. Check query parameter (?access_token=... or ?code=...)
        return uri.getQueryParameter("access_token")
            ?: uri.getQueryParameter("code")
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
