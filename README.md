Strings — Guitar Tuner & Metronome 🎸
Strings is a minimalist, accurate, and modern guitar tuner app for Android, designed for musicians who value simplicity, reliability, and style. Version 1.3 introduces a professional-grade noise filtering engine and a rock-solid rhythm controller.

🚀 What's New in v1.3
🛡️ Smart Noise Filtering (Tuner Engine)
Manual Sensitivity Control: Adjust the microphone threshold (RMS) directly from the settings. Perfect for tuning in noisy environments or with low-output instruments.

Confidence Analysis: Upgraded FFT logic that distinguishes between a clear string vibration and background hiss.

Live Visual Feedback: See the exact sensitivity value (50–2000) as you tune it.

🥁 Advanced Rhythm Engine (Metronome)
Continuous Swing Logic: Fixed the "sticking" pendulum bug for odd time signatures (3/4, 5/4, 7/8, etc.). The scale now moves fluidly regardless of the beat count.

New Time Signatures: Expanded support for complex meters (5/4, 7/4, 5/8, 7/8, 9/8).

Haptic Feedback: Tactile clicks when adjusting BPM for a more "hardware" feel.

✨ Features
🎯 Precision Tuning
Chromatic Mode: High-accuracy real-time frequency analysis.

Instrument Presets: Quick tuning for 6/7-string guitars, 4/5-string basses, Drop D, and Drop C.

Dynamic Visuals: Background color transitions to Perfect Green when you're in tune.

Vibration Alert: Haptic confirmation when a string is perfectly tuned.

⏱️ Professional Metronome
BPM Range: 30 to 250 BPM with high-precision timing.

Turbo-Scroll: Long-press + or - for rapid 10 BPM jumps.

Tap Tempo: Set your rhythm manually by tapping.

Visual Pulse: Animated scale and flash accents on the downbeat (Beat 1).

🎨 Personalized UX
Material 3 & Dynamic Colors: Fully integrated with Android's latest design language.

Theme Support: Light, Dark, or System default modes (with fix for microphone lifecycle on theme change).

Bilingual: Full native support for English and Russian.

🛠 Tech Stack
Language: Kotlin

Audio: AudioRecord API + FFT (Fast Fourier Transform) + RMS Noise Gate.

UI: Material Components (M3), ViewBinding, Custom Animated Views.

Persistence: SharedPreferences for saving sensitivity and theme states.

📸 Screenshots
<p align="center">
<img src="https://github.com/user-attachments/assets/d66f124c-4c07-4216-a9d1-569617af999a" width="30%" alt="Tuner Screen" />
<img src="https://github.com/user-attachments/assets/1ba7a835-f8e7-43e2-90d9-82ed7809dc27" width="30%" alt="Metronome Screen" />
<img src="https://github.com/user-attachments/assets/02e14944-8438-4fbb-ad00-cc1bd057f26a" width="30%" alt="Settings Screen v1.3" />
</p>

📦 Installation
Go to the Releases section.

Download Strings_v1.3.apk.

Install and grant Microphone permissions for the tuner to work.

👨‍💻 Author
Developed by eduashi.

Evolution of the project: v1.0 (Logic) → v1.1 (UI) → v1.3 (Professional Audio & UX).

Tune your strings, keep your beat.