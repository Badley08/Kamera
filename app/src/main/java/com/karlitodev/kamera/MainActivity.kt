package com.karlitodev.kamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManagerInstance

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            setupComposeContent()
        } else {
            Toast.makeText(
                this,
                "Les permissions Caméra et Microphone sont obligatoires.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = CameraManagerInstance(this)

        if (hasRequiredPermissions()) {
            setupComposeContent()
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED
    }

    private fun setupComposeContent() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = remember {
                        VideoAppState(
                            flashEnabled = mutableStateOf(false),
                            currentZoom = mutableStateOf(1.0f),
                            isRecording = mutableStateOf(false),
                            cameraIdToUse = mutableStateOf("2")
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Camera2Preview(
                            cameraManager = cameraManager,
                            modifier = Modifier.fillMaxSize()
                        )

                        MainVideoScreen(
                            state = state,
                            onZoomChange = { newZoom ->
                                state.currentZoom.value = newZoom
                                cameraManager.setZoom(newZoom)
                            },
                            onFlashToggle = {
                                state.flashEnabled.value = !state.flashEnabled.value
                                cameraManager.toggleFlash(state.flashEnabled.value)
                            },
                            onStartRecording = {
                                state.isRecording.value = true
                                cameraManager.startRecording()
                            },
                            onStopRecording = {
                                state.isRecording.value = false
                                cameraManager.stopRecording()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.release()
    }
}