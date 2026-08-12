# Kamera App Full Refactor

Complete rewrite of the camera/video recording logic and UI to fix all broken functionality and match OnePlus camera UI conventions.

## User Review Required

> [!IMPORTANT]
> **Device**: OnePlus Nord N100, default back camera is `"0"`, front camera will be auto-detected.  
> **Scope**: Video-only app (no photo mode). Camera2 sensor is colorless — no special handling needed since Camera2 pipeline handles debayering automatically.

## Proposed Changes

### VideoAppState.kt — State model overhaul
#### [MODIFY] [VideoAppState.kt](file:///c:/Users/Roldine/AndroidStudioProjects/Kamera/app/src/main/java/com/karlitodev/kamera/VideoAppState.kt)
- Add `isPaused: MutableState<Boolean>` for pause/resume
- Add `isFrontCamera: MutableState<Boolean>` for camera switching
- Add `lastVideoPath: MutableState<String?>` for thumbnail preview
- Remove `cameraIdToUse` (replaced by `isFrontCamera` logic)

---

### CameraManagerInstance.kt — Full rewrite of camera engine
#### [MODIFY] [CameraManagerInstance.kt](file:///c:/Users/Roldine/AndroidStudioProjects/Kamera/app/src/main/java/com/karlitodev/kamera/CameraManagerInstance.kt)
- **Default camera**: Use `"0"` (back camera) instead of `"2"`
- **Camera switching**: Detect front/back camera IDs via `CameraCharacteristics.LENS_FACING`, swap and restart preview
- **Flash fix**: Only enable torch on back camera (front cameras don't have flash)
- **Video recording fix**: Proper MediaRecorder lifecycle with correct surface management
- **Pause/Resume**: Use `MediaRecorder.pause()` / `MediaRecorder.resume()` (API 24+, minSdk=27 ✓)
- **Save to MediaStore**: Use `ContentValues` + `MediaStore.Video` for proper video saving so gallery/thumbnail access works
- **Zoom**: Apply crop region using active camera's characteristics (not hardcoded ID)
- **Quality**: Keep ISP optimizations (noise reduction, edge, color correction, HEVC 1080p@30fps 20Mbps)

---

### Camera2Preview.kt — Support camera restart on switch
#### [MODIFY] [Camera2Preview.kt](file:///c:/Users/Roldine/AndroidStudioProjects/Kamera/app/src/main/java/com/karlitodev/kamera/Camera2Preview.kt)
- Pass a `cameraKey` (e.g. `isFrontCamera` state) so recomposition triggers on camera switch
- Re-init `TextureView` listener to restart preview when camera changes

---

### MainVideoScreen.kt — OnePlus-style video UI
#### [MODIFY] [MainVideoScreen.kt](file:///c:/Users/Roldine/AndroidStudioProjects/Kamera/app/src/main/java/com/karlitodev/kamera/MainVideoScreen.kt)
**Layout** (OnePlus video mode style):
- **Top bar**: Flash toggle (left), recording timer (center when recording)
- **Bottom controls row** (left to right):
  - **Video thumbnail** (bottom-left): Rounded square showing last recorded video frame
  - **Shutter button** (center): **Red circle** when idle, red circle with white stop square when recording
  - **Camera switch** (bottom-right): Front/back camera toggle icon
- **Pause button**: Appears above shutter during recording
- **Zoom arc**: Keep existing arc indicator above controls
- Add `onPauseRecording`, `onResumeRecording`, `onSwitchCamera` callbacks

---

### MainActivity.kt — Wire new callbacks and fix French strings
#### [MODIFY] [MainActivity.kt](file:///c:/Users/Roldine/AndroidStudioProjects/Kamera/app/src/main/java/com/karlitodev/kamera/MainActivity.kt)
- Wire `onPauseRecording`, `onResumeRecording`, `onSwitchCamera` to `CameraManagerInstance`
- Translate remaining French strings in `PermissionExplanationScreen` to English
- Handle `onDestroy` to release camera resources

---

### New drawable icons needed
#### [NEW] `ic_camera_switch.xml` — Front/back camera switch icon
#### [NEW] `ic_pause.xml` — Pause recording icon
#### [NEW] `ic_resume.xml` — Resume recording icon

---

## Verification Plan

### Automated Tests
- `.\gradlew assembleDebug` — Build must succeed

### Manual Verification
- Install APK on OnePlus Nord N100
- Verify camera preview shows live feed from back camera (`"0"`)
- Verify camera switch toggles between front and back
- Verify flash toggles on (torch) and off on back camera
- Verify video recording starts, pauses, resumes, and stops
- Verify saved video appears as thumbnail in bottom-left
- Verify zoom arc and drag zoom work during preview and recording
