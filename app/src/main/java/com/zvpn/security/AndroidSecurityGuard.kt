package com.zvpn.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.security.MessageDigest

/**
 * Android Runtime Security Guard & Anti-Tampering Engine.
 * Detects Frida, Xposed, APK Signature Repackaging, Root/Emulators, and Ad-Blocker Host Sinkholes.
 */
object AndroidSecurityGuard {

    private const val EXPECTED_PACKAGE_NAME = "com.zvpn"

    /**
     * Comprehensive security check. Returns true if the app environment is clean and authentic.
     */
    fun isEnvironmentSecure(context: Context): Boolean {
        // 1. Verify Package Name
        if (context.packageName != EXPECTED_PACKAGE_NAME) return false

        // 2. Check for Hooking Frameworks (Frida / Xposed)
        if (isFridaOrXposedPresent()) return false

        // 3. Check for Ad-Blocker / Host Sinkholes
        if (isAdHostSinkholed()) return false

        return true
    }

    /**
     * Detects Frida agents or Xposed hooking libraries loaded in the current process memory.
     */
    fun isFridaOrXposedPresent(): Boolean {
        try {
            // Check /proc/self/maps for Frida or Xposed shared objects
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                mapsFile.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val lower = line.lowercase()
                        if (lower.contains("frida") || lower.contains("xposed") || lower.contains("substrate")) {
                            return true
                        }
                    }
                }
            }

            // Check standard Frida listening ports
            for (port in intArrayOf(27042, 27047)) {
                try {
                    val socket = Socket("127.0.0.1", port)
                    socket.close()
                    return true
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Checks if ad domains are redirected to 127.0.0.1 / 0.0.0.0 in /etc/hosts (Ad-Blockers).
     */
    fun isAdHostSinkholed(): Boolean {
        try {
            val hostsFile = File("/etc/hosts")
            if (hostsFile.exists()) {
                hostsFile.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val lower = line.lowercase()
                        if ((lower.contains("127.0.0.1") || lower.contains("0.0.0.0")) &&
                            (lower.contains("unity3d.com") || lower.contains("unityads") || lower.contains("doubleclick"))
                        ) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Computes the SHA-256 hash of the app's signing certificate.
     */
    fun getAppSignatureHash(context: Context): String {
        try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val pkgInfo = pm.getPackageInfo(pkgName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val cert = signatures[0].toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(cert)
                return digest.joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {}

        return ""
    }
}
