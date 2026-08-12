package com.karlitodev.kamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

class CameraManagerInstance(
    private val context: Context,
    private val state: VideoAppState? = null
) {
    private val TAG = "CameraManagerInstance"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    // Camera IDs detected at init; defaults to "0" if detection fails
    private var backCameraId: String? = null
    private var frontCameraId: String? = null
    private var currentCameraId: String = "0"

    private var currentZoom = 1.0f
    private var isFlashOn = false
    private var previewSurface: Surface? = null
    private var currentOutputFile: File? = null

    // Recording timer state
    private var timer: Timer? = null
    private var elapsedSeconds = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        detectCameras()
    }

    // Enumerate hardware cameras and assign back/front IDs by LENS_FACING
    private fun detectCameras() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> {
                        if (backCameraId == null) backCameraId = id
                    }
                    CameraCharacteristics.LENS_FACING_FRONT -> {
                        if (frontCameraId == null) frontCameraId = id
                    }
                }
            }
            // Default to back camera, fall back to "0" if no back camera found
            currentCameraId = backCameraId ?: "0"
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting camera IDs", e)
            currentCameraId = "0"
        }
    }

    @SuppressLint("MissingPermission")
    fun startPreview(surface: Surface) {
        previewSurface = surface
        try {
            stopPreviewInternal()
            cameraManager.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error code: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera preview", e)
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            applyQualitySettings(builder)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = listOf(OutputConfiguration(surface))
                val config = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    Executors.newSingleThreadExecutor(),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, null)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting repeating preview request", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Preview session configuration failed")
                        }
                    }
                )
                device.createCaptureSession(config)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, null)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting repeating preview request", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Preview session configuration failed")
                        }
                    },
                    null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preview session", e)
        }
    }

    // Switch between front and back cameras (disabled while recording)
    fun switchCamera() {
        if (state?.isRecording?.value == true) return

        val targetId = if (currentCameraId == backCameraId && frontCameraId != null) {
            frontCameraId!!
        } else {
            backCameraId ?: "0"
        }

        currentCameraId = targetId
        val isFront = (currentCameraId == frontCameraId)
        state?.isFrontCamera?.value = isFront

        // Disable flash when switching to front camera (no hardware torch available)
        if (isFront && isFlashOn) {
            toggleFlash(false)
        }

        // Reset zoom on camera switch
        currentZoom = 1.0f
        state?.currentZoom?.value = 1.0f

        previewSurface?.let { startPreview(it) }
    }

    fun startRecording() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null

            currentOutputFile = createVideoFile()
            setupMediaRecorder(currentOutputFile!!)

            val recorderSurface = mediaRecorder!!.surface
            val surfaces = listOf(surface, recorderSurface)

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(surface)
            builder.addTarget(recorderSurface)
            applyQualitySettings(builder)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = surfaces.map { OutputConfiguration(it) }
                val config = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    Executors.newSingleThreadExecutor(),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, null)
                                mediaRecorder?.start()
                                mainHandler.post {
                                    state?.isRecording?.value = true
                                    state?.isPaused?.value = false
                                }
                                resetAndStartTimer()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting recording session", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Recording session configuration failed")
                        }
                    }
                )
                device.createCaptureSession(config)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, null)
                                mediaRecorder?.start()
                                mainHandler.post {
                                    state?.isRecording?.value = true
                                    state?.isPaused?.value = false
                                }
                                resetAndStartTimer()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting recording session", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Recording session configuration failed")
                        }
                    },
                    null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startRecording", e)
        }
    }

    // Pause the active recording and freeze the timer
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                state?.isPaused?.value = true
                stopTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing recording", e)
            }
        }
    }

    // Resume a paused recording and continue the timer from where it stopped
    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                state?.isPaused?.value = false
                continueTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming recording", e)
            }
        }
    }

    fun stopRecording() {
        try {
            stopTimer()
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null

            mainHandler.post {
                state?.isRecording?.value = false
                state?.isPaused?.value = false
                state?.recordingTimeSeconds?.value = 0
            }

            currentOutputFile?.let { file ->
                saveVideoToMediaStore(file)
                generateThumbnail(file)
            }

            createPreviewSession()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            mainHandler.post {
                state?.isRecording?.value = false
                state?.isPaused?.value = false
            }
            createPreviewSession()
        }
    }

    // Set digital zoom level (clamped to 1.0x–3.0x for performance stability)
    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(1.0f, 3.0f)
        state?.currentZoom?.value = currentZoom
        updateRepeatingRequest()
    }

    // Toggle flash/torch mode (only supported on back-facing camera)
    fun toggleFlash(enable: Boolean = !isFlashOn) {
        if (currentCameraId == frontCameraId) {
            isFlashOn = false
            state?.flashEnabled?.value = false
            return
        }

        isFlashOn = enable
        state?.flashEnabled?.value = enable
        updateRepeatingRequest()
    }

    private fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            val template = if (state?.isRecording?.value == true)
                CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            val builder = device.createCaptureRequest(template)
            builder.addTarget(surface)
            if (state?.isRecording?.value == true) {
                mediaRecorder?.surface?.let { builder.addTarget(it) }
            }
            applyQualitySettings(builder)
            session.setRepeatingRequest(builder.build(), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating repeating request", e)
        }
    }

    // Apply ISP quality settings and digital zoom crop region to the capture request
    private fun applyQualitySettings(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

        // Flash control (torch only on back camera)
        if (isFlashOn && currentCameraId != frontCameraId) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        // Digital zoom via sensor crop region
        try {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
            val arrayRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (arrayRect != null) {
                val cropWidth = (arrayRect.width() / currentZoom).toInt()
                val cropHeight = (arrayRect.height() / currentZoom).toInt()
                val left = (arrayRect.width() - cropWidth) / 2
                val top = (arrayRect.height() - cropHeight) / 2
                val cropRect = Rect(left, top, left + cropWidth, top + cropHeight)
                builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying crop region", e)
        }
    }

    // Configure MediaRecorder with H.264 encoding for maximum device compatibility
    @Suppress("DEPRECATION")
    private fun setupMediaRecorder(file: File) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            val videoSize = getBestVideoSize()
            setVideoSize(videoSize.width, videoSize.height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(12_000_000)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)

            prepare()
        }
        mediaRecorder = recorder
    }

    // Query the camera for supported video output sizes (prefer 1080p, then 720p)
    private fun getBestVideoSize(): Size {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(MediaRecorder::class.java)
            if (!sizes.isNullOrEmpty()) {
                sizes.find { it.width == 1920 && it.height == 1080 }?.let { return it }
                sizes.find { it.width == 1280 && it.height == 720 }?.let { return it }
                return sizes[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying supported video sizes", e)
        }
        return Size(1280, 720)
    }

    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VID_${timestamp}_", ".mp4", storageDir)
    }

    // Copy the recorded video file into the public MediaStore so it appears in the gallery
    private fun saveVideoToMediaStore(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Kamera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                // Copy recorded file bytes into the MediaStore content URI
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Mark the MediaStore entry as complete
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to MediaStore", e)
        }
    }

    // Extract the first frame of the recorded video for the thumbnail preview
    private fun generateThumbnail(file: File) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            mainHandler.post {
                state?.lastVideoThumbnail?.value = bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating video thumbnail", e)
        }
    }

    // Reset elapsed time and start a new timer (used when starting a new recording)
    private fun resetAndStartTimer() {
        stopTimer()
        elapsedSeconds = 0
        mainHandler.post { state?.recordingTimeSeconds?.value = 0 }
        startTimerInternal()
    }

    // Continue the timer from the current elapsed value (used when resuming after pause)
    private fun continueTimer() {
        stopTimer()
        startTimerInternal()
    }

    private fun startTimerInternal() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                elapsedSeconds++
                mainHandler.post {
                    state?.recordingTimeSeconds?.value = elapsedSeconds
                }
            }
        }, 1000, 1000)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun stopPreviewInternal() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera resources", e)
        }
    }

    fun stopPreview() {
        stopPreviewInternal()
    }

    fun release() {
        stopTimer()
        stopPreviewInternal()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}