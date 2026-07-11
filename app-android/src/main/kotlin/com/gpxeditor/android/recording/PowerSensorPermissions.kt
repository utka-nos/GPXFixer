package com.gpxeditor.android.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions needed to scan for and connect to a BLE power sensor. */
object PowerSensorPermissions {
    fun required(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        // BLE scan results are location-protected through Android 11.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun allGranted(context: Context): Boolean = required().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
