package com.karlitodev.kamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private val imageEnhancer = ImageEnhancer(context)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var imageReader: ImageReader? = null

    // Background thread for camera callbacks and image processing
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var backCameraId: String? = null
    private var frontCameraId: String? = null
    private var currentCameraId: String = "0"

    private var currentZoom = 1.0f
    private var isFlashOn = false
    private var previewSurface: Surface? = null
    private var currentOutputFile: File? = null

    private var timer: Timer? = null
    private var elapsedSeconds = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        startBackgroundThread()
        detectCameras()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
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
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera preview", e)
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val surface = previewSurface ?: return

        try {
            // Setup ImageReader for still photo capture
            setupImageReader()

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            applyQualitySettings(builder)

            val surfaces = mutableListOf(surface)
            imageReader?.surface?.let { surfaces.add(it) }

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
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
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
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting repeating preview request", e)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Preview session configuration failed")
                        }
                    },
                    backgroundHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preview session", e)
        }
    }

    private fun setupImageReader() {
        imageReader?.close()
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                backgroundHandler?.post {
                    processCapturedPhoto(image)
                }
            }, backgroundHandler)
        }
    }

    // Process and save high-quality photo capture
    private fun processCapturedPhoto(image: Image) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()

            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            // Rotate photo for portrait display (90 degrees if back camera, 270 if front)
            val rotationDegrees = if (currentCameraId == frontCameraId) 270f else 90f
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Apply TFLite super-resolution sharpness enhancement if hardware capabilities are met
            bitmap = imageEnhancer.enhancePhoto(bitmap)

            val photoFile = createPhotoFile()
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            savePhotoToMediaStore(photoFile, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing captured photo", e)
        }
    }

    // Capture still photo
    fun takePhoto() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(reader.surface)
            applyQualitySettings(builder)

            // Orientation hint for photo metadata
            val orientation = if (currentCameraId == frontCameraId) 270 else 90
            builder.set(CaptureRequest.JPEG_ORIENTATION, orientation)

            session.capture(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo", e)
        }
    }

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

        if (isFront && isFlashOn) {
            toggleFlash(false)
        }

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
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
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
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
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
                    backgroundHandler
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startRecording", e)
        }
    }

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

    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(1.0f, 3.0f)
        state?.currentZoom?.value = currentZoom
        updateRepeatingRequest()
    }

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
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating repeating request", e)
        }
    }

    private fun applyQualitySettings(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

        // 1. Lock sensor AE Target FPS Range to 30 FPS (prevents frame drop in low light)
        try {
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (!fpsRanges.isNullOrEmpty()) {
                val targetRange = fpsRanges.find { it.lower == 30 && it.upper == 30 }
                    ?: fpsRanges.find { it.upper == 30 }
                    ?: fpsRanges[0]
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting AE target FPS range", e)
        }

        if (isFlashOn && currentCameraId != frontCameraId) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

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

            // Standard landscape sensor dimensions (1920x1080) with orientation hint set to 90
            val videoSize = getBestVideoSize()
            setVideoSize(videoSize.width, videoSize.height)

            // Orientation hint for 9:16 portrait playback
            val orientationHint = if (currentCameraId == frontCameraId) 270 else 90
            setOrientationHint(orientationHint)

            // Locked 30 FPS framerate and 22 Mbps bitrate as recommended in optimization guide
            setVideoFrameRate(30)
            setVideoEncodingBitRate(22_000_000)

            // Stereo audio at 48.0 kHz
            setAudioChannels(2)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(48_000)

            prepare()
        }
        mediaRecorder = recorder
    }

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
        return Size(1920, 1080)
    }

    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VID_${timestamp}_", ".mp4", storageDir)
    }

    private fun createPhotoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("IMG_${timestamp}_", ".jpg", storageDir)
    }

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
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }

                mainHandler.post {
                    state?.lastVideoUri?.value = uri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to MediaStore", e)
        }
    }

    private fun savePhotoToMediaStore(file: File, bitmap: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }

                mainHandler.post {
                    state?.lastVideoThumbnail?.value = bitmap
                    state?.lastVideoUri?.value = uri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo to MediaStore", e)
        }
    }

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

    private fun resetAndStartTimer() {
        stopTimer()
        elapsedSeconds = 0
        mainHandler.post { state?.recordingTimeSeconds?.value = 0 }
        startTimerInternal()
    }

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
            imageReader?.close()
            imageReader = null
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
        stopBackgroundThread()
        imageEnhancer.close()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}