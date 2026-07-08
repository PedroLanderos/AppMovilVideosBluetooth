package com.example.btvideo.util

import android.Manifest
import android.app.Activity
import android.os.Build

object PermissionHelper {
    const val REQUEST_CODE = 7001

    fun requiredBluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(requiredBluetoothPermissions(), REQUEST_CODE)
        }
    }
}
