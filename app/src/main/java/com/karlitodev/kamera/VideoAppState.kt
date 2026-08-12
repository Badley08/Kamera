package com.karlitodev.kamera

import androidx.compose.runtime.MutableState

data class VideoAppState(
    val flashEnabled: MutableState<Boolean>,
    val currentZoom: MutableState<Float>, // 1.0f à 3.0f
    val isRecording: MutableState<Boolean>,
    val cameraIdToUse: MutableState<String>
)