Strings — Tuner, Metronome & Generator 🎸
Strings is a minimalist, accurate, and professional-grade music toolset for Android. Designed for musicians who demand precision and style, version 2.0 transforms the app into a complete workstation with the addition of a powerful Tone Generator and a fully adaptive Material 3 interface.

🚀 What's New in v2.0
🎹 Professional Tone Generator (New!)
Multi-Waveform Support: Switch between Sine, Square, and Sawtooth waves for different acoustic needs.

Smart Selectors: Integrated Note & Octave selectors that stay perfectly synced with the frequency slider.

Precision Input: Tap the frequency display to manually enter any value in Hz for scientific accuracy.

Top Notifications: Smooth, animated in-app notifications for waveform changes that follow the system's dynamic palette.

🎨 Complete Material 3 Overhaul
Dynamic Colors: The entire app—including the Tuner scale and Metronome markers—now automatically adapts to the Android system wallpaper.

Adaptive UI Components: All custom views, dialogs, and sliders now use the colorSecondaryContainer and colorOutline attributes for a seamless "system-native" look.

Theming Engine: Enhanced Light/Dark/System theme switching with a more robust persistence logic.

⏱️ Enhanced Rhythm & Logic
Visual Pulse 2.0: The Metronome now features a full-screen flash on the downbeat (Beat 1) and a more responsive "pendulum" animation.

Haptic Engine: Refined tactile feedback across the entire app—from perfect tuning confirmation to slider ticks and button presses.

UI Polish: Improved layout transitions and fixed navigation bugs for a faster, "snappier" feel.

✨ Core Features

🎯 Precision Tuning

Chromatic Mode: High-accuracy real-time frequency analysis.

Instrument Presets: Quick tuning for 6/7-string guitars, 4/5-string basses, Drop D, and Drop C.

Dual-Layer Noise Reduction:

RMS Noise Gate: User-adjustable microphone sensitivity threshold to ignore ambient background noise.

Confidence Analysis (FFT Logic): A sophisticated engine that analyzes the "purity" of the signal. It filters out non-musical artifacts and harmonic ghosts, ensuring the tuner only reacts to stable, clear string vibrations.

Visual & Haptic Cues: Background transitions to Perfect Green and the device vibrates when the string is perfectly in tune.

🥁 Advanced Metronome
BPM Range: 30 to 300 BPM with rock-solid timing.

Complex Meters: Support for 2/4, 3/4, 4/4, 5/4, 7/4, 5/8, 7/8, 9/8 signatures.

Turbo-Scroll: Long-press +/- for rapid 10 BPM adjustments.

Tap Tempo: Set the rhythm manually by tapping the screen.

🌍 Localization: English, Russian.

🛠 Tech Stack
Language: Kotlin

Audio Engine: AudioRecord API + FFT (Fast Fourier Transform) + Custom Tone Synthesis.

UI: Material Components (M3), ViewBinding, Custom Animated Canvas Views.

Persistence: SharedPreferences for saving sensitivity, theme, and last-used tab states.

📸 Screenshots
<p align="center">
<img src="https://github.com/user-attachments/assets/4ca76cea-e5db-4b9d-96ff-8f5814da9af6" width="30%" alt="Tuner Screen" />
<img src="https://github.com/user-attachments/assets/7eed38e3-ce3c-4557-82c0-bf3243e2541e" width="30%" alt="Metronome Screen" />
<img src="https://github.com/user-attachments/assets/6a054ed9-0d09-4335-9e20-a90171a27470" width="30%" alt="Tone Generator Screen" />
<img src="https://github.com/user-attachments/assets/459dfaa8-e498-475f-b3e4-af11160cd6ce" width="30%" alt="Settings Screen" />
</p>

📦 Installation
Go to the Releases section.

Download Strings_v2.0.apk.

Install and grant Microphone permissions (required for the Tuner to analyze frequency).