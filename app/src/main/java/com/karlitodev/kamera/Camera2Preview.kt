package com.karlitodev.kamera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Camera2Preview(
    cameraManager: CameraManagerInstance,
    isFrontCamera: Boolean,
    onFiveSecondLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    val timerJob = coroutineScope.launch {
                        // 5 seconds continuous long-press detection
                        delay(5000L)
                        onFiveSecondLongPress()
                    }

                    waitForUpOrCancellation()
                    timerJob.cancel()
                }
            }
    ) {
        // key() forces full recreation of the TextureView when the camera direction changes,
        // while preventing unnecessary restarts on unrelated recompositions (timer, zoom, etc.)
        key(isFrontCamera) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                // Start camera preview stream when surface texture becomes available
                                cameraManager.startPreview(Surface(surfaceTexture))
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                // Managed automatically by Camera2 session
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                // Stop camera preview on surface destruction
                                cameraManager.stopPreview()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                                // Invoked on every frame update
                            }
                        }
                    }
                }
            )
        }
    }
}