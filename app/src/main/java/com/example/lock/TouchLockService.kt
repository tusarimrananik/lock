package com.example.lock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

object LockStateManager {
    val isLocked = MutableStateFlow(false)
}

@SuppressLint("AccessibilityPolicy")
class TouchLockService : AccessibilityService() {

    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TouchLock", "Accessibility Service woke up! (Device Booted)")

        if (Settings.canDrawOverlays(this)) {
            showOverlay()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("TouchLock", "Failed to open MainActivity on boot: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LOCK" -> showOverlay()
            "STOP_LOCK" -> removeOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showOverlay() {
        if (overlayView != null) return

        Log.d("TouchLock", "Engaging Persistent Shield...")
        LockStateManager.isLocked.value = true

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = View(this)

        @Suppress("DEPRECATION")
        overlayView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("TouchLock", "Failed to add window: ${e.message}")
        }
    }

    private fun removeOverlay() {
        LockStateManager.isLocked.value = false
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
            Log.d("TouchLock", "Shield lifted!")
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (LockStateManager.isLocked.value) {
            val keyCode = event?.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!LockStateManager.isLocked.value) return

        // Instant intercept: No 500ms wait time
        if (event?.packageName == "com.android.systemui") {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {}
}