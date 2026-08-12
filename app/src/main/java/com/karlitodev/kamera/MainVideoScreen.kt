package com.karlitodev.kamera

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun MainVideoScreen(
    state: VideoAppState,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Top bar: Flash toggle (left) and Recording timer (center)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash toggle (disabled when front camera is active)
            IconButton(
                onClick = onFlashToggle,
                enabled = !state.isFrontCamera.value
            ) {
                Icon(
                    painter = painterResource(
                        id = if (state.flashEnabled.value) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                    ),
                    contentDescription = "Flash Toggle",
                    tint = if (state.isFrontCamera.value) Color.Gray else Color.White
                )
            }

            // Recording timer indicator
            if (state.isRecording.value) {
                val seconds = state.recordingTimeSeconds.value
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Red.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.isPaused.value) "PAUSED $formattedTime" else "REC $formattedTime",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.size(48.dp))
        }

        // 2. Bottom controls container (OnePlus camera style)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom arc indicator
            ZoomArc(currentZoom = state.currentZoom.value)
            
            Spacer(modifier = Modifier.height(16.dp))

            // Pause / Resume toggle button (appears above shutter during active recording)
            AnimatedVisibility(
                visible = state.isRecording.value,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        if (state.isPaused.value) onResumeRecording() else onPauseRecording()
                    },
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (state.isPaused.value) R.drawable.ic_play else R.drawable.ic_pause
                        ),
                        contentDescription = "Pause or Resume Recording",
                        tint = Color.White
                    )
                }
            }

            // Bottom controls row: Thumbnail (left), Red Shutter (center), Camera Switch (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom-Left: Last video thumbnail preview
                VideoThumbnailPreview(thumbnail = state.lastVideoThumbnail.value)

                // Center: Red Shutter Button with drag zoom gesture
                RedShutterButton(
                    isRecording = state.isRecording.value,
                    onZoomRequested = { delta ->
                        val newZoom = (state.currentZoom.value + delta).coerceIn(1.0f, 3.0f)
                        onZoomChange(newZoom)
                    },
                    onClick = {
                        if (state.isRecording.value) onStopRecording() else onStartRecording()
                    }
                )

                // Bottom-Right: Front / Back Camera Switch Button
                IconButton(
                    onClick = onSwitchCamera,
                    enabled = !state.isRecording.value,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera_switch),
                        contentDescription = "Switch Camera",
                        tint = if (state.isRecording.value) Color.Gray else Color.White
                    )
                }
            }
        }
    }
}

// Zoom arc component (OnePlus camera style arc)
@Composable
fun ZoomArc(currentZoom: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(50.dp).fillMaxWidth()) {
        Canvas(modifier = Modifier.size(180.dp, 90.dp)) {
            val center = Offset(size.width / 2, size.height)
            val radius = 80.dp.toPx()
            
            // Normalized progress across 1.0x to 3.0x zoom range
            val progress = (currentZoom - 1f) / 2f
            
            // Background translucent arc
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
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
        
        // Digital zoom factor display
        Text(
            text = String.format(Locale.getDefault(), "%.1fx", currentZoom),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

// Red Shutter Button (OnePlus Red signature color)
@Composable
fun RedShutterButton(
    isRecording: Boolean,
    onZoomRequested: (Float) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Vertical drag gesture: drag up to zoom in, drag down to zoom out
                    val sensitivity = -0.008f
                    onZoomRequested(dragAmount * sensitivity)
                }
            }
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(OnePlusRed)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            // White stop recording icon inside red button when recording
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
            )
        }
    }
}

// Video thumbnail preview component (Bottom-Left)
@Composable
fun VideoThumbnailPreview(thumbnail: Bitmap?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Last Recorded Video Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}