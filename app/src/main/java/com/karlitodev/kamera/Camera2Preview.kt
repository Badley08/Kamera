package com.karlitodev.kamera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun Camera2Preview(
    cameraManager: CameraManagerInstance,
    modifier: Modifier = Modifier
) {
    // AndroidView integrates standard Android View within Jetpack Compose
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // TextureView displays the camera video preview stream
            TextureView(context).apply {
                // Match parent dimensions to fill available screen space
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Listen for surface texture lifecycle events
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // Start camera preview when surface texture is ready
                        cameraManager.startPreview(Surface(surfaceTexture))
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // Automatically managed by Camera2 session
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        // Stop camera preview on surface destruction
                        cameraManager.stopPreview()
                        return true // Return true to indicate surface release management
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Called on each frame update
                    }
                }
            }
        }
    )
}