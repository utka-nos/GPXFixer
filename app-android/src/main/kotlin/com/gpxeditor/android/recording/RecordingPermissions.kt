package com.gpxeditor.android.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Runtime permissions required to record a track in a foreground service. */
object RecordingPermissions {
    const val TAG = "GPXRecording"

    fun required(): Array<String> {
        // Android 12 ignores a runtime request for FINE_LOCATION when COARSE_LOCATION
        // is not included in the same request.
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    fun allGranted(context: Context): Boolean {
        // POST_NOTIFICATIONS is requested so the ongoing recording is visible in the
        // notification drawer, but Android does not require it to start a foreground
        // service. Some OEMs do not show its runtime dialog, so it must not block GPS.
        return listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).any { permission -> isGranted(context, permission) }
    }

    /** Required permissions that are not currently granted. */
    fun missing(context: Context): List<String> {
        return required().filterNot { permission -> isGranted(context, permission) }
    }

    /** Logs the grant state of every recording permission so logcat shows what is missing. */
    fun logStatus(context: Context, source: String) {
        required().forEach { permission ->
            val granted = isGranted(context, permission)
            val log = if (granted) Log.INFO else Log.WARN
            Log.println(log, TAG, "[$source] $permission granted=$granted")
        }
        Log.i(TAG, "[$source] allGranted=${allGranted(context)}")
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
