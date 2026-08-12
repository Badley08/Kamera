# Kamera — Version 6.0 Release Notes

## AI Super-Resolution Photo Sharpness Enhancement (v6.0)

Version 6.0 integrates an ultra-lightweight **TensorFlow Lite ESRGAN** super-resolution model (~4.7 MB) to process and enhance photo sharpness after capture. Yes, i put AI in my camera app. I'm officially a startup founder now 🚀😂

### 1. Hardware & OS Guard Conditions

The AI enhancement pipeline is **strictly enabled** only when the device meets all three conditions:

- **RAM**: Minimum **4 GB** total RAM (`ActivityManager.MemoryInfo.totalMem >= 4GB`). If your phone has less than 4GB of RAM in 2026... bro it's time to upgrade 💀
- **Android OS**: Android 11 or higher (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` / API 30+). Disabled on Android 9 & 10. Sorry grandpa phones, AI is not for you 👴📱
- **CPU Architecture**: Must support 64-bit ARM (`arm64-v8a`). 32-bit CPUs trying to run AI is like me trying to run a marathon — not happening 🏃‍♂️💨

### 2. Model & Execution Details

- **Framework**: TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.14.0`). Google's finest, couldn't afford the premium version so Lite it is 😅
- **Model**: ESRGAN super-resolution model located in `assets/esrgan.tflite` (~4.7 MB). This tiny file makes my OnePlus Nord N100 photos look like they came from a OnePlus 13 Pro — as a OnePlus fanboy this makes me emotional ngl 🥹📱
- **Asset Rule**: `androidResources { noCompress += "tflite" }` prevents APK compression overhead for faster loading. Learned this after wondering why my model was loading slower than my grandma's WiFi 🐌

---
