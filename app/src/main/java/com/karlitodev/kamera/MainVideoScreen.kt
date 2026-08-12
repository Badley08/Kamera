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

@Composable
fun MainVideoScreen(
    state: VideoAppState,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Flash toggle button (Top left)
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

        // 2. Camera controls area (Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom arc indicator
            ZoomArc(currentZoom = state.currentZoom.value)
            
            Spacer(modifier = Modifier.height(20.dp))

            // Shutter button with drag zoom (capped at 3.0x)
            ShutterButton(
                isRecording = state.isRecording.value,
                onZoomRequested = { delta ->
                    // Clamp zoom factor between 1.0x and 3.0x
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
            
            // Normalized zoom progress over 1.0x to 3.0x range
            val progress = (currentZoom - 1f) / 2f
            
            // Translucent background arc
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active zoom progress arc
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
        
        // Numeric zoom level indicator
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
                    // Drag sensitivity scaled for 1x to 3x zoom range
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