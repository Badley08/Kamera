package com.karlitodev.kamera

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class VideoAppState(
    val hasPermissions: MutableState<Boolean> = mutableStateOf(false),
    val flashEnabled: MutableState<Boolean> = mutableStateOf(false),
    val currentZoom: MutableState<Float> = mutableStateOf(1.0f), // 1.0f to 3.0f
    val isRecording: MutableState<Boolean> = mutableStateOf(false),
    val cameraIdToUse: MutableState<String> = mutableStateOf("2")
)