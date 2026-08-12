package com.karlitodev.kamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManagerInstance
    private val appState = VideoAppState()

    // Runtime permissions launcher for camera and microphone permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            appState.hasPermissions.value = true
            showZoomInstructionToast()
        } else {
            Toast.makeText(
                this,
                "Camera and microphone permissions are required to record video.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = CameraManagerInstance(this, appState)

        // Check required runtime permissions on activity creation
        checkAndRequestPermissions()

        setContent {
            KameraTheme {
                if (appState.hasPermissions.value) {
                    // Main camera interface layout
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Camera2Preview(
                            cameraManager = cameraManager,
                            isFrontCamera = appState.isFrontCamera.value,
                            modifier = Modifier.fillMaxSize()
                        )
                        MainVideoScreen(
                            state = appState,
                            onZoomChange = { zoom -> cameraManager.setZoom(zoom) },
                            onFlashToggle = { cameraManager.toggleFlash() },
                            onStartRecording = { cameraManager.startRecording() },
                            onPauseRecording = { cameraManager.pauseRecording() },
                            onResumeRecording = { cameraManager.resumeRecording() },
                            onStopRecording = { cameraManager.stopRecording() },
                            onSwitchCamera = { cameraManager.switchCamera() },
                            onThumbnailClick = { openLastVideo() }
                        )
                    }
                } else {
                    // Permission rationale screen when required permissions are missing
                    PermissionExplanationScreen(
                        onRequestPermissions = { checkAndRequestPermissions() }
                    )
                }
            }
        }
    }

    // Display user guidance toast explaining zoom gesture on launch
    private fun showZoomInstructionToast() {
        Toast.makeText(
            this,
            "Press & drag UP on the record button to zoom in, drag DOWN to zoom out (capped at 3.0x).",
            Toast.LENGTH_LONG
        ).show()
    }

    // Open the last recorded video in the system video player
    private fun openLastVideo() {
        val uri = appState.lastVideoUri.value ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No video player available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Legacy storage permission for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val allGranted = permissionsToRequest.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            appState.hasPermissions.value = true
            showZoomInstructionToast()
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.release()
    }
}

@Composable
fun PermissionExplanationScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            color = Color.White,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Kamera requires camera access to capture video, microphone access to record audio, and storage access to save video files.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed)
        ) {
            Text(text = "Grant Permissions", color = Color.White)
        }
    }
}