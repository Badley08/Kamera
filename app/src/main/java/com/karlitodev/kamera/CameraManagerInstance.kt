package com.karlitodev.kamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraManagerInstance(
    private val context: Context,
    private val state: VideoAppState? = null
) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    // Target camera ID (camera "2")
    private val forcedCameraId = "2"

    private var currentZoom = 1.0f
    private var isFlashOn = false
    private var previewSurface: Surface? = null
    private var currentOutputFile: File? = null

    @SuppressLint("MissingPermission")
    fun startPreview(surface: Surface) {
        previewSurface = surface
        try {
            cameraManager.openCamera(forcedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            applyQualitySettings(builder)

            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(builder.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            captureSession?.stopRepeating()
            captureSession?.close()

            currentOutputFile = createVideoFile()
            setupMediaRecorder(currentOutputFile!!)

            val recorderSurface = mediaRecorder!!.surface
            val surfaces = listOf(surface, recorderSurface)

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(surface)
            builder.addTarget(recorderSurface)
            applyQualitySettings(builder)

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(builder.build(), null, null)
                        mediaRecorder?.start()
                        state?.isRecording?.value = true
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            state?.isRecording?.value = false
            createPreviewSession()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setZoom(zoom: Float) {
        currentZoom = zoom
        state?.currentZoom?.value = zoom
        updateRepeatingRequest()
    }

    fun toggleFlash(enable: Boolean = !isFlashOn) {
        isFlashOn = enable
        state?.flashEnabled?.value = enable
        updateRepeatingRequest()
    }

    private fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(surface)
            mediaRecorder?.surface?.let { builder.addTarget(it) }
            applyQualitySettings(builder)
            session.setRepeatingRequest(builder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyQualitySettings(builder: CaptureRequest.Builder) {
        // --- ISP quality optimization settings ---
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

        // Toggle flashlight mode
        if (isFlashOn) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        // Digital zoom crop calculation
        val characteristics = cameraManager.getCameraCharacteristics(forcedCameraId)
        val arrayRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (arrayRect != null) {
            val cropWidth = (arrayRect.width() / currentZoom).toInt()
            val cropHeight = (arrayRect.height() / currentZoom).toInt()
            val left = (arrayRect.width() - cropWidth) / 2
            val top = (arrayRect.height() - cropHeight) / 2
            val cropRect = Rect(left, top, left + cropWidth, top + cropHeight)
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
        }
    }

    private fun setupMediaRecorder(file: File) {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)

            // 1080p HEVC (H.265) video encoding at 20 Mbps
            setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(1920, 1080)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(20_000_000)
            setAudioEncodingBitRate(192_000)
            setAudioSamplingRate(48_000)

            prepare()
        }
    }

    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VID_${timestamp}_", ".mp4", storageDir)
    }

    fun stopPreview() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    fun release() {
        stopPreview()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}