# Strings — Guitar Tuner & Metronome

**Strings** is a minimalist, accurate, and modern guitar tuner app for Android, designed for musicians who value simplicity, reliability, and style. Version 1.2 focuses on rock-solid stability, refined animations, and high-performance audio analysis.

##  Features

###  Precision Tuning
* **Chromatic Mode:** High-accuracy real-time frequency analysis using FFT (Fast Fourier Transform).
* **Instrument Presets:** Quick tuning for 6/7-string guitars, 4/5-string basses, Drop D, and Drop C.
* **Visual Perfection (Updated v1.2):** Re-engineered background color transitions using synchronized `ValueAnimator` for a seamless "Perfect Pitch" experience.
* **Haptic Feedback:** Subtle vibration when you hit the exact frequency.

###  Advanced Metronome
* **Adjustable Tempo:** Supports a wide range from 30 to 250 BPM.
* **Turbo-Scroll:** Long-press `+` or `-` to quickly jump by 10 BPM (300ms intervals).
* **Tap Tempo:** Set your rhythm manually by tapping the screen.
* **Time Signatures:** Support for 2/4, 3/4, 4/4, 6/8, and more.
* **Visual Pulse:** Animated scale and flash accents to keep you on the beat.

###  Personalization & UX (Updated v1.2)
* **Stable Navigation:** Fixed UI state leaks when switching between Tuner and Metronome modes.
* **Theme Support:** Choose between **Light**, **Dark**, or **System** default modes.
* **Material 3:** Fully compliant with Google's latest design system, including dynamic colors.
* **Smooth Transitions:** Fluid "Fade & Scale" animations between layouts.
* **Bilingual:** Full support for English and Russian languages.

##  Tech Stack
* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI:** Android XML with **Material Design 3**
* **Audio Processing:** AudioRecord API & FFT Analysis
* **Architecture:** ViewBinding & Material Component Library
* **Package Name:** `com.eduashi.strings`

##  Screenshots
<p align="center">
  <img src="https://github.com/user-attachments/assets/d66f124c-4c07-4216-a9d1-569617af999a" width="30%" alt="Tuner Screen" />
  <img src="https://github.com/user-attachments/assets/1ba7a835-f8e7-43e2-90d9-82ed7809dc27" width="30%" alt="Metronome Screen" />
  <img src="https://github.com/user-attachments/assets/d5591d6e-e49f-4393-9201-741fba290a2f" width="30%" alt="Settings Screen" />
</p>

##  Installation
1. Go to the [Releases](https://github.com/eduashi/Strings/releases) section.
2. Download the latest `Strings_v1.2.apk`.
3. Install it on your Android device (ensure "Install from unknown sources" is enabled).

##  Author
Developed by **eduashi**.  
This project is an evolving Android development journey, moving from basic logic to a polished, professional-grade utility.

---
*Tune your strings, keep your beat.*
