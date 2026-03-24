Strings — Tuner, Metronome & Generator 🎸
Strings is a minimalist, accurate, and professional-grade music toolset for Android. Version 2.1 focuses on stability, power efficiency, and user experience, refining the workstation introduced in the previous major update.

🚀 What's New in v2.1
💡 Intelligent Screen Management (New!)
Tuner-Only Keep Awake: The screen now stays active only while the Tuner is visible, ensuring you don't lose your focus during tuning. The screen timeout returns to normal when switching to other tools to save battery.

Background Optimization: Automatic release of "Keep Awake" flags when the app is minimized to prevent accidental battery drain.

⏱️ Rock-Solid Metronome & Audio Engine
Static Audio Buffers: The Metronome engine now uses pre-allocated static tracks, eliminating latency and providing a perfectly consistent "click" even during long sessions.

Improved Audio Disposal: Better management of AudioTrack and AudioRecord lifecycles to prevent memory leaks and ensure the microphone is released immediately when not in use.

🎨 UI & UX Refinement
Adaptive Theme Consistency: Fixed edge-case bugs where dynamic colors didn't apply correctly to some UI components on specific Android versions.

Navigation Logic: Smoother transitions between Tuner, Metronome, and Generator tabs.

✨ Core Features
🎯 Precision Tuning
Chromatic Mode: High-accuracy real-time frequency analysis.

Instrument Presets: Quick tuning for 6/7-string guitars, 4/5-string basses, Drop D, Drop C, Drop A, and Drop G.

Dual-Layer Noise Reduction:

RMS Noise Gate: User-adjustable microphone sensitivity threshold to ignore ambient background noise.

MPM Pitch Detection: A sophisticated engine that analyzes the "purity" of the signal, filtering out non-musical artifacts and ensuring the tuner reacts only to stable vibrations.

Visual & Haptic Cues: Background transitions to "Perfect Green" and the device vibrates when the string is in tune.

🎹 Professional Tone Generator
Multi-Waveform Support: Sine, Square, and Sawtooth waves.

Smart Selectors: Integrated Note & Octave selectors synced with the frequency slider.

Precision Input: Manual Hz entry for scientific accuracy.

🥁 Advanced Metronome
BPM Range: 30 to 300 BPM with high-precision ScheduledThreadPoolExecutor timing.

Complex Meters: Support for 2/4, 3/4, 4/4, 5/4, 7/4, 5/8, 6/8, 7/8, 9/8, 12/8 signatures.

Tap Tempo: Set the rhythm manually by tapping the screen.

🎨 Design Philosophy
Material 3: Fully adaptive interface using Dynamic Colors (Monet) that match your system wallpaper.

Haptic Engine: Tactile feedback for every interaction—from perfect tuning to slider ticks.

Localization: English, Russian.

🛠 Tech Stack
Language: Kotlin

Audio Engine: AudioRecord API + FFT (Fast Fourier Transform) + MPM Pitch Detection + Custom Tone Synthesis.

UI: Material Components (M3), ViewBinding, Custom Animated Canvas Views.

Persistence: SharedPreferences for saving sensitivity, theme, and last-used states.

📸 Screenshots
<p align="center">
<img src="https://github.com/user-attachments/assets/4ca76cea-e5db-4b9d-96ff-8f5814da9af6" width="23%" alt="Tuner Screen" />
<img src="https://github.com/user-attachments/assets/7eed38e3-ce3c-4557-82c0-bf3243e2541e" width="23%" alt="Metronome Screen" />
<img src="https://github.com/user-attachments/assets/6a054ed9-0d09-4335-9e20-a90171a27470" width="23%" alt="Tone Generator Screen" />
<img src="https://github.com/user-attachments/assets/a0323545-8192-48a5-aa39-c7bc91ffae1a" width="23%" alt="Settings Screen" />
</p>

📦 Installation
Go to the Releases section.

Download Strings_v2.1.apk.

Install and grant Microphone permissions (required for the Tuner).