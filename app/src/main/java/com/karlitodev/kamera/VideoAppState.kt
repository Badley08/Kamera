package com.karlitodev.kamera

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

enum class CameraMode {
    VIDEO,
    PHOTO
}

// Camera application state container
data class VideoAppState(
    val hasPermissions: MutableState<Boolean> = mutableStateOf(false),
    val flashEnabled: MutableState<Boolean> = mutableStateOf(false),
    val currentZoom: MutableState<Float> = mutableStateOf(1.0f),
    val isRecording: MutableState<Boolean> = mutableStateOf(false),
    val isPaused: MutableState<Boolean> = mutableStateOf(false),
    val isFrontCamera: MutableState<Boolean> = mutableStateOf(false),
    val cameraMode: MutableState<CameraMode> = mutableStateOf(CameraMode.VIDEO),
    val lastVideoThumbnail: MutableState<Bitmap?> = mutableStateOf(null),
    val lastVideoUri: MutableState<Uri?> = mutableStateOf(null),
    val recordingTimeSeconds: MutableState<Int> = mutableIntStateOf(0)
)