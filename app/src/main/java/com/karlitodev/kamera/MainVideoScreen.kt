package com.karlitodev.kamera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OnePlusRed = Color(0xFFF00000)

@Composable
fun MainVideoScreen(
    state: VideoAppState,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Flash (Haut gauche)
        IconButton(
            onClick = onFlashToggle,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 24.dp)
        ) {
            Icon(
                painter = painterResource(id = if (state.flashEnabled.value) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                contentDescription = "Flash",
                tint = Color.White
            )
        }

        // 2. Zone de contrôle (Bas)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // L'Arc de cercle de Zoom (Style OnePlus)
            ZoomArc(currentZoom = state.currentZoom.value)
            
            Spacer(modifier = Modifier.height(20.dp))

            // Bouton Obturateur (Limité à 3.0x pour la stabilité)
            ShutterButton(
                isRecording = state.isRecording.value,
                onZoomRequested = { delta ->
                    // Zoom strictement bridé à 3.0x max
                    val newZoom = (state.currentZoom.value + delta).coerceIn(1.0f, 3.0f)
                    onZoomChange(newZoom)
                },
                onClick = { if (state.isRecording.value) onStopRecording() else onStartRecording() }
            )
        }
    }
}

@Composable
fun ZoomArc(currentZoom: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(60.dp).fillMaxWidth()) {
        Canvas(modifier = Modifier.size(200.dp, 100.dp)) {
            val center = Offset(size.width / 2, size.height)
            val radius = 90.dp.toPx()
            
            // Progrès calculé sur la plage 1.0 -> 3.0 (Plage de 2.0x)
            val progress = (currentZoom - 1f) / 2f
            
            // Arc de fond (gris translucide)
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Arc actif (Progression du zoom)
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Indicateur numérique du zoom
        Text(
            text = String.format("%.1fx", currentZoom),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

@Composable
fun ShutterButton(
    isRecording: Boolean,
    onZoomRequested: (Float) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Sensibilité ajustée pour la plage de 1x à 3x
                    val sensitivity = -0.008f 
                    onZoomRequested(dragAmount * sensitivity)
                }
            }
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (isRecording) OnePlusRed else Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(modifier = Modifier.size(24.dp).background(Color.White, RoundedCornerShape(4.dp)))
        }
    }
}