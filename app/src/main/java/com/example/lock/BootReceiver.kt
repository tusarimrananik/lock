package com.example.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Check if the signal is the boot completed signal
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            Log.d("TouchLock", "Device booted! Waking up the app...")

            // Only launch the app if we have the overlay permission,
            // otherwise Android might block the background launch.
            if (Settings.canDrawOverlays(context)) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    // FLAG_ACTIVITY_NEW_TASK is legally required when starting an Activity from outside an Activity
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}