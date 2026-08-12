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
    // AndroidView permet d'intégrer une View Android classique dans Compose
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // TextureView est parfait pour afficher un flux vidéo de caméra
            TextureView(context).apply {
                // On s'assure que la vue prend toute la place
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // On écoute les événements de la surface
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // Dès que la surface est prête, on dit au CameraManager de démarrer la preview
                        cameraManager.startPreview(Surface(surfaceTexture))
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        // Géré automatiquement par Camera2 dans notre cas simple
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        // Quand l'écran est fermé, on coupe la caméra
                        cameraManager.stopPreview()
                        return true // Retourner true pour indiquer que nous gérons la libération de la surface
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        // Appelé à chaque nouvelle frame, utile pour analyser l'image, mais pas nécessaire ici
                    }
                }
            }
        }
    )
}