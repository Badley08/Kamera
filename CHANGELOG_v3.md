# Kamera — Version 3.0 Release Notes

## Major Updates & Quality Optimizations (v3.0)

This major release aligns **Kamera** directly with native camera app hardware specifications and user experience requirements based on empirical benchmark comparison.

### 1. Technical Audio & Video Specifications
- **Portrait Resolution**: Enforced **1080 x 1920** portrait capture format. Because who records landscape videos in 2026? Not me. 🙃
- **High Video Bitrate**: Increased video bitrate from 12 Mbps to **20 Mbps** (`20,000,000 bps`) to eliminate artifacts, noise, and compression blurring. My old 12 Mbps videos looked like they were filmed through a shower curtain 😭
- **Stereo Audio Recording**: Upgraded from 1-channel mono to **2-channel stereo** (`setAudioChannels(2)`). Now you can hear people judge your cooking from BOTH ears 🎧
- **Pro Audio Sampling Rate**: Upgraded audio sampling rate from 44.1 kHz to **48.0 kHz** (industry standard for HD video). CD quality is cool but we're in the video era grandpa 😂
- **Framerate Target**: 30 fps targeting with continuous video autofocus and ISP tuning.

### 2. User Experience Improvements
- **Gallery Integration**: Tapping the video thumbnail preview in the bottom-left corner directly launches the device's system video player/gallery (`Intent.ACTION_VIEW`). No more going to Files > DCIM > Camera like it's 2015 💀
- **OnePlus Red Interface**: Red shutter button design with white stop icon during recording. I'm a huge OnePlus fan so obviously the UI had to be OnePlus Red, no discussion 🔴🫡
- **Pause & Resume**: Seamless video pause and resume controls without generating multiple fragmented files. You know how annoying it is to have 47 video clips from one birthday party? Yeah, fixed that 😤
- **Gesture Control**: Vertical drag on record button for digital zoom (clamped between 1.0x and 3.0x max). I felt like a Hollywood director adding this one ngl 🎬

---

