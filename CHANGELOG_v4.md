# Kamera — Version 4.0 Release Notes

## Major Features & Enhancements (v4.0)

### 1. Performance & Fast Startup
- **Async Background Thread Initialization**: Camera callbacks and preview requests processed on a dedicated `HandlerThread` to eliminate UI thread latency. The UI thread was doing more work than me during finals week 😅
- **Instant Preview**: Camera preview initializes immediately on launch without blocking dialogs or delays. No more staring at a black screen wondering if your phone died 💀

### 2. Photo Mode Integration
- **Discrete VIDEO / PHOTO Switcher**: Added mode switcher control above the shutter button. Because apparently a camera app that only records video is "weird" according to my friends 🙄
- **Still Photo Capture**: High-quality JPEG photo capture (`ImageReader` / `TEMPLATE_STILL_CAPTURE`) with automatic rotation handling. Now your selfies won't be sideways anymore, you're welcome 📸
- **Unified Drag-to-Zoom Gesture**: Vertical drag gesture on the shutter button maintained across both video and photo modes (1.0x to 3.0x). One gesture to rule them all 💍

### 3. FPS & Orientation Optimization
- **30 FPS Fluid Video**: Replaced direct 1080x1920 portrait sensor configuration with standard hardware landscape sensor configuration (`1920x1080`) combined with `setOrientationHint(90)`. This resolves framerate drop (from 14 FPS to full 30 FPS). 14 FPS was giving PowerPoint presentation vibes, had to fix that ASAP 😂
- **20 Mbps Bitrate & 48 kHz Stereo**: Maintained high-clarity 20 Mbps H.264 encoding and 48 kHz stereo audio. These numbers mean nothing to most people but trust me it's good 🤓

### 4. UI/UX Minimalism & Redesigned Camera Logo
- **Edge-to-Edge Full Screen**: Enabled translucent system bars (`enableEdgeToEdge()`) for full-view preview layout. More screen = more cinema 🎥
- **Minimalist Zoom Pill**: Replaced heavy arc indicator with discrete pill selector (`1.0x`, `2.0x`, `3.0x`) with quick tap presets. The old arc looked like a loading screen from 2010 😬
- **Refined Shutter Styling**: Sleeker white border with signature OnePlus Red accent. I'm a die-hard OnePlus fan, so every pixel of this app bleeds OnePlus DNA — "Never Settle"... but they settled when OPPO took over 😭 don't get me started on that 🔴🏆
- **New Camera App Icon**: Designed high-contrast camera lens launcher icon (`ic_launcher_foreground.xml`). Spent more time on this icon than on some of my homework ngl 🎨

---

