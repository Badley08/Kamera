# Kamera — Version 5.0 Release Notes

## Technical Optimizations (v5.0 - Camera Hardware Parameter Locks)

Based on the technical optimization guide (`guide_optimisation_kamera_camera1.xlsx`), Version 5.0 locks sensor FPS, bitrate, and recording execution order. Yes i made a whole Excel spreadsheet for this, i'm that kind of developer 🤓📊

### 1. Sensor 30 FPS Lock in Low Light
- **Parameter**: `CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE` locked to `Range(30, 30)`.
- **Result**: Prevents sensor shutter slowdown and frame drops (18–20 fps) in low-light indoor environments. My room lighting was exposing this bug every single night, couldn't ignore it anymore 😤💡

### 2. Bitrate & Execution Sequence
- **Parameter**: `setVideoEncodingBitRate(22_000_000)` (22 Mbps). We went from 20 to 22 because why not, 2 extra Mbps never hurt nobody 💪
- **Execution Order**: Sensor preparation -> Encoder configuration -> Override Bitrate/FPS -> `setAudioChannels(2)` -> `setAudioSamplingRate(48000)` -> `setOrientationHint(90)` -> `prepare()`.
- **Result**: Guarantees 22 Mbps high-bitrate recording matching native camera clarity. I'm a massive OnePlus fan but even i had to admit the stock camera bitrate was kinda low, so Kamera does it better 😤💪 Never Settle right? Well OPPO settled it for them and i'll never forgive them for that 😭

### 3. Portrait 9:16 Orientation Metadata
- **Parameter**: `setOrientationHint(90)` for back camera, `270` for front camera.
- **Result**: Ensures native vertical 1080x1920 playback across all video players. No more rotating your phone like a steering wheel to watch your own videos 🔄😂

### 4. Resolution Alignment
- **Parameter**: Preview surface and recording surface synchronized on 1920x1080.
- **Result**: Eliminates CPU scaling overhead during recording. The CPU was working overtime like an unpaid intern, had to give it a break 😅

---

