package com.example.lock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.lock.ui.theme.LockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            LockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TouchLockScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }
}

@Composable
fun TouchLockScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    val isAppLocked by LockStateManager.isLocked.collectAsState()

    // --- YOUR HARDCODED PASSWORD ---
    val HARDCODED_PASSWORD = "1234"
    var passwordInput by remember { mutableStateOf("") }

    BackHandler(enabled = isAppLocked) {
        Log.d("TouchLock", "Back press intercepted by Compose and ignored!")
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        if (isAppLocked) {
            // ==========================================
            // UI WHEN THE DEVICE IS LOCKED
            // ==========================================
            Text("Device is Locked \uD83D\uDD12", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Enter Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Button(onClick = {
                if (passwordInput == HARDCODED_PASSWORD) {
                    // Password matches! Stop the service and clear the input.
                    val intent = Intent(context, TouchLockService::class.java).apply {
                        action = "STOP_LOCK"
                    }
                    context.startService(intent)
                    passwordInput = ""
                } else {
                    Toast.makeText(context, "Incorrect Password!", Toast.LENGTH_SHORT).show()
                    passwordInput = "" // Clear it so they can try again
                }
            }) {
                Text("Unlock")
            }

        } else {
            // ==========================================
            // UI WHEN THE DEVICE IS UNLOCKED (Setup)
            // ==========================================
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }) {
                Text(if (hasOverlayPermission) "1. Overlay Permission: ON ✅" else "1. Grant Overlay Permission")
            }

            Button(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }) {
                Text("2. Grant Accessibility Permission")
            }

            Button(
                onClick = {
                    val intent = Intent(context, TouchLockService::class.java).apply {
                        action = "START_LOCK"
                    }
                    context.startService(intent)
                },
                enabled = hasOverlayPermission
            ) {
                Text("Start Lock 🔒")
            }
        }
    }
}