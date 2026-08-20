package com.karlitodev.kamera

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    onTakePhoto: () -> Unit,
    onSwitchCamera: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. Top bar: Flash toggle (left) and Recording timer (center)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp),
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
                        .background(Color.Red.copy(alpha = 0.85f))
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

        // 2. Bottom controls container (Minimalist OnePlus style)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek minimalist zoom pill selector (1.0x / 2.0x / 3.0x)
            ZoomPillSelector(
                currentZoom = state.currentZoom.value,
                onZoomSelect = { zoom -> onZoomChange(zoom) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Discrete Camera Mode Switcher (VIDEO / PHOTO)
            if (!state.isRecording.value) {
                CameraModeSwitcher(
                    currentMode = state.cameraMode.value,
                    onModeSelect = { mode -> state.cameraMode.value = mode }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

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

            // Bottom controls row: Gallery Thumbnail (left), Shutter (center), Camera Switch (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom-Left: Last media thumbnail preview (opens gallery on tap)
                VideoThumbnailPreview(
                    thumbnail = state.lastVideoThumbnail.value,
                    onClick = onThumbnailClick
                )

                // Center: Shutter Button (supports drag up/down for zoom in both Video and Photo modes)
                ShutterButton(
                    mode = state.cameraMode.value,
                    isRecording = state.isRecording.value,
                    onZoomRequested = { delta ->
                        val newZoom = (state.currentZoom.value + delta).coerceIn(1.0f, 3.0f)
                        onZoomChange(newZoom)
                    },
                    onClick = {
                        if (state.cameraMode.value == CameraMode.VIDEO) {
                            if (state.isRecording.value) onStopRecording() else onStartRecording()
                        } else {
                            onTakePhoto()
                        }
                    }
                )

                // Bottom-Right: Front / Back Camera Switch Button
                IconButton(
                    onClick = onSwitchCamera,
                    enabled = !state.isRecording.value,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
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

// Minimalist zoom pill selector with quick tap presets (1.0x, 2.0x, 3.0x)
@Composable
fun ZoomPillSelector(
    currentZoom: Float,
    onZoomSelect: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(1.0f, 2.0f, 3.0f).forEach { zoomLevel ->
            val isSelected = Math.abs(currentZoom - zoomLevel) < 0.3f
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) OnePlusRed else Color.Transparent)
                    .clickable { onZoomSelect(zoomLevel) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.0fx", zoomLevel),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// Discrete Camera Mode Switcher (VIDEO / PHOTO) with high contrast background pill
@Composable
fun CameraModeSwitcher(
    currentMode: CameraMode,
    onModeSelect: (CameraMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraMode.values().forEach { mode ->
            val isSelected = (mode == currentMode)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) OnePlusRed else Color.Transparent)
                    .clickable { onModeSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.name,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Shutter Button with refined thin border and drag-to-zoom support
@Composable
fun ShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    onZoomRequested: (Float) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Drag up to zoom in, drag down to zoom out (works in both video and photo modes)
                    val sensitivity = -0.008f
                    onZoomRequested(dragAmount * sensitivity)
                }
            }
            .border(2.5.dp, Color.White, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(if (mode == CameraMode.VIDEO) OnePlusRed else Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (mode == CameraMode.VIDEO && isRecording) {
            // White stop recording square inside red button when recording
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
            )
        } else if (mode == CameraMode.PHOTO) {
            // Inner Red ring for photo shutter
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(OnePlusRed.copy(alpha = 0.15f))
            )
        }
    }
}

// Video/Photo thumbnail preview component (Bottom-Left)
@Composable
fun VideoThumbnailPreview(
    thumbnail: Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = thumbnail != null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Last Media Preview",
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