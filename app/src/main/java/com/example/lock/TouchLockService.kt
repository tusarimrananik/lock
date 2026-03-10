package com.example.lock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
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
    private var mediaPlayer: MediaPlayer? = null
    private var isReceiverRegistered = false

    // ==========================================
    // THE SCREEN-OFF INTERCEPTOR
    // ==========================================
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && LockStateManager.isLocked.value) {
                Log.d("TouchLock", "Screen turned off! Forcing it back on...")
                forceScreenOn(context)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TouchLock", "Accessibility Service woke up!")

        if (Settings.canDrawOverlays(this)) {
            showOverlay()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("TouchLock", "Failed to open MainActivity: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_LOCK") {
            showOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @Suppress("DEPRECATION")
    private fun forceScreenOn(context: Context?) {
        try {
            val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager

            // Acquire an aggressive wake lock to force the screen back on immediately
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "TouchLock::AggressiveWakeLock"
            )

            // Hold it for 3 seconds to ensure the screen turns on, then let the WindowManager FLAG_KEEP_SCREEN_ON take over
            wakeLock?.acquire(3000)
        } catch (e: Exception) {
            Log.e("TouchLock", "Failed to wake screen: ${e.message}")
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        Log.d("TouchLock", "Engaging Persistent Shield...")
        LockStateManager.isLocked.value = true

        // Register the Screen Off Receiver
        if (!isReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, filter)
            isReceiverRegistered = true
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // FORCE AUDIO TO BUILT-IN SPEAKER
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }

            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        } catch (e: Exception) {
            Log.e("TouchLock", "Failed to force speaker/volume: ${e.message}")
        }

        // PLAY THE LOCK SOUND
        try {
            mediaPlayer = MediaPlayer()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer?.setAudioAttributes(audioAttributes)

            val afd = resources.openRawResourceFd(R.raw.test)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mediaPlayer?.isLooping = true
            mediaPlayer?.prepare()
            mediaPlayer?.start()

        } catch (e: Exception) {
            Log.e("TouchLock", "Failed to play lock sound: ${e.message}")
        }

        // APPLY THE OVERLAY SHIELD
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
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("TouchLock", "Failed to add window: ${e.message}")
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

        if (event?.packageName == "com.android.systemui") {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        if (isReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            isReceiverRegistered = false
        }
    }
}