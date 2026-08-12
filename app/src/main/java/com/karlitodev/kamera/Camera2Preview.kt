package com.karlitodev.kamera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun Camera2Preview(
    cameraManager: CameraManagerInstance,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    // AndroidView integrates standard Android TextureView within Jetpack Compose
    AndroidView(
        modifier = modifier,
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
        },
        update = { textureView ->
            // Re-trigger preview start when camera is switched
            if (textureView.isAvailable) {
                cameraManager.startPreview(Surface(textureView.surfaceTexture))
            }
        }
    )
}