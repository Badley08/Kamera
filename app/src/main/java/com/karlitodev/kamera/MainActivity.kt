package com.karlitodev.kamera

import android.Manifest
import android.content.contentValuesOf
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

    // Gestionnaire de demande de permissions multiples au lancement
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            appState.hasPermissions.value = true
        } else {
            Toast.makeText(
                this,
                "Les autorisations caméra et micro sont nécessaires pour utiliser l'application.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = CameraManagerInstance(this, appState)

        // Vérification des permissions au démarrage
        checkAndRequestPermissions()

        setContent {
            KameraTheme {
                if (appState.hasPermissions.value) {
                    // Écran principal de la caméra si les permissions sont accordées
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Camera2Preview(
                            cameraManager = cameraManager,
                            modifier = Modifier.fillMaxSize()
                        )
                        MainVideoScreen(
                            state = appState,
                            onZoomChange = { zoom -> cameraManager.setZoom(zoom) },
                            onFlashToggle = { cameraManager.toggleFlash() },
                            onStartRecording = { cameraManager.startRecording() },
                            onStopRecording = { cameraManager.stopRecording() }
                        )
                    }
                } else {
                    // Écran d'explication clair si les permissions ont été refusées
                    PermissionExplanationScreen(
                        onRequestPermissions = { checkAndRequestPermissions() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Inutile de demander WRITE_EXTERNAL_STORAGE sur Android 10+ (Scoped Storage géré par MediaStore)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val allGranted = permissionsToRequest.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            appState.hasPermissions.value = true
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
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
            text = "Autorisations requises",
            color = Color.White,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Kamera a besoin d'accéder à votre appareil photo pour capturer le flux vidéo, au microphone pour enregistrer le son, et au stockage pour sauvegarder vos vidéos sur l'appareil.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed)
        ) {
            Text(text = "Autoriser les accès", color = Color.White)
        }
    }
}