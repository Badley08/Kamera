package com.karlitodev.kamera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainVideoScreen(
    state: VideoAppState,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Bouton Flash en haut à gauche uniquement
        TopBar(
            flashEnabled = state.flashEnabled.value,
            onFlashToggle = onFlashToggle
        )

        // 2. Zone de contrôle inférieure (Arc de Zoom + Bouton d'enregistrement)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            ZoomArc(zoom = state.currentZoom.value)

            ShutterButton(
                isRecording = state.isRecording.value,
                onZoomRequested = { deltaZoom ->
                    val newZoom = (state.currentZoom.value + deltaZoom).coerceIn(1.0f, 3.0f)
                    onZoomChange(newZoom)
                },
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )
        }
    }
}

@Composable
fun TopBar(flashEnabled: Boolean, onFlashToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onFlashToggle) {
            Icon(
                painter = painterResource(
                    id = if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                ),
                contentDescription = "Flash",
                tint = Color.White
            )
        }
    }
}

@Composable
fun ZoomArc(zoom: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(180.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val arcSize = Size(size.width, size.height * 2f)
            val topOffset = Offset(0f, -size.height / 2f)

            // Arc semi-circulaire
            drawArc(
                color = Color.White.copy(alpha = 0.5f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topOffset,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
        }

        // Affichage de la valeur actuelle du Zoom (ex: 1.0x)
        Text(
            text = String.format("%.1fx", zoom),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

@Composable
fun ShutterButton(
    isRecording: Boolean,
    onZoomRequested: (Float) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .pointerInput(isRecording) {
                // Glisser le doigt verticalement sur l'obturateur pour zoomer
                detectVerticalDragGestures { _, dragAmount ->
                    // Glisser vers le haut = Zoom avant (dragAmount négatif)
                    val zoomSensitivity = -0.01f
                    onZoomRequested(dragAmount * zoomSensitivity)
                }
            }
            .background(Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = {
                if (isRecording) onStopRecording() else onStartRecording()
            },
            modifier = Modifier
                .size(68.dp)
                .background(if (isRecording) Color.Red else Color.White, CircleShape)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}