package com.ct.explorer.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            if (biometricManager != null) {
                val canAuth = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                return canAuth == BiometricManager.BIOMETRIC_SUCCESS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            if (biometricManager != null) {
                @Suppress("DEPRECATION")
                val canAuth = biometricManager.canAuthenticate()
                return canAuth == BiometricManager.BIOMETRIC_SUCCESS
            }
        }
        // Fallback check hardware feature
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    fun authenticate(
        activity: Activity,
        title: String = "Unlock Cent Vault",
        subtitle: String = "Use your fingerprint to access private files",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNegativeButton: () -> Unit
    ): CancellationSignal? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric authentication requires Android 9.0+")
            return null
        }

        try {
            val cancellationSignal = CancellationSignal()
            val executor = ContextCompat.getMainExecutor(activity)

            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negativeButtonText, executor) { _, _ ->
                    onNegativeButton()
                }
                .build()

            prompt.authenticate(
                cancellationSignal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        val msg = errString?.toString() ?: "Authentication error"
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                            onNegativeButton()
                        } else {
                            onError(msg)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Fingerprint not recognized. Try again.")
                    }
                }
            )
            return cancellationSignal
        } catch (e: Exception) {
            onError("Biometric prompt failed: ${e.localizedMessage}")
            return null
        }
    }
}
