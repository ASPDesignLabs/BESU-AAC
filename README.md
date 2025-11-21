BESU 🖖


Emotional Augmentative & Alternative Communication (AAC) via Physics-Based Gestures

   

BESU is a system-wide Android & Wear OS toolkit designed to bridge the gap between intent and expression. It acts as a digital emotional prosthesis, allowing users to project complex emotions onto their phone screen (complete with voice synthesis) using subtle, physics-based movements of a smartwatch—no typing or speaking required.


---

🌟 Core Features

1. The Wear OS Physics Engine


We don't just tap screens; we read intention through motion. The watch app runs a custom sensor fusion engine that distinguishes between daily noise (typing, walking) and deliberate communicative acts.


- The "Pump" (👍 Thumbs Up): Detects a "Prime" state (arm raise) followed by a "Fire" state (arm drop). This prevents accidental triggers while walking.

- The "Wiper" (🚫 No): Tracks lateral oscillation of the forearm to signal refusal.

- The "Royal Wave" (👋 Hello): Uses gyroscope sum-energy to detect wrist rotation while the arm is elevated.

- The "Traffic Cop" (✋ Stop): Differentiates an extended arm (Palm Forward) from a raised arm (Thumbs Up) using gravitational vector analysis.

- Activation Lock: A "Double-Twist" wrist gesture is required to wake the sensors, ensuring zero false positives while resting or working.

2. The Radial Interface


A system-wide overlay (ACCESSIBILITY_OVERLAY) that floats above any running application.


- Hierarchical Emotions: Select a base emotion (e.g., "Happy") to reveal nuances (e.g., "Proud", "Grateful", "Excited").

- Quick Confirm: Double-tap a primary emotion to speak the base phrase immediately.

3. Voice Synthesis (TTS)


Every emotion is mapped to a customizable phrase. When a gesture is triggered or a menu item selected, the phone speaks for you.


- Gesture: 👋 -> "Hello there."

- Menu: 🦁 -> "I am proud of myself."


---

🛠️ Project Structure


This is a multi-module Gradle project:

:app (The Voice & Display)

- OverlayService.kt: The heart of the phone app. Handles drawing the giant Emoji over other apps and managing the Text-To-Speech engine.

- WearListenerService.kt: Listens for high-frequency data packets from the watch (/gesture/*) and triggers the overlay.

- RadialMenuService.kt: Manages the circular UI and animation physics.

- EmotionData.kt: The configuration tree defining emojis, labels, and spoken phrases.

:wear (The Sensor Array)

- MainActivity.kt: Contains the entire sensor logic loop.
	- State Machine: IDLE -> LISTENING -> COOLDOWN.

	- Sensor Fusion: Combines Accelerometer (Gravity/Pose) and Gyroscope (Rotation/Velocity) to filter noise.

	- Data Layer: Pipes raw sensor debug data to the phone for real-time visualization.



---

🚀 Installation & Setup

Prerequisites

- Android Phone (Android 10+)

- Wear OS Watch (Wear OS 3+)

- Developer Options & USB Debugging enabled on both.

1. Build & Deploy

1. Open the project in Android Studio.

2. Select the app configuration and run it on your Phone.

3. Select the wear configuration and run it on your Watch.

4. Important: Ensure both devices are paired via Bluetooth. If using an Emulator for the watch, forward the port:

	adb -d forward tcp:5601 tcp:5601



2. Permissions


Upon first launch, you must grant:


1. Overlay Permission: Allows BESU to draw over other apps.

2. Notification Permission: Required for foreground services to keep sensors alive.


---

🎮 How to Use (The Gestures)


Step 1: Unlock

Your watch is locked by default to save battery and sanity.


- Action: Hold arm horizontal. Quickly rotate wrist away and back twice.

- Feedback: Watch vibrates 🐝 and turns GREEN.

Step 2: Communicate

(You have 5 seconds after unlocking to perform a gesture)


Intention	Gesture	Physics Logic

YES / GOOD	The Pump: Raise fist (Thumbs Up), then drop hand.	AccX > 7.0 (Prime) -> AccX < 4.0 (Fire)

NO	The Wiper: Sweep forearm left/right in front of chest.	GyroX oscillation > 3 cycles

HELLO	The Wave: Raise hand high, wave side-to-side.	AccX > 5.0 + GyroZ oscillation

STOP	The Hand: Extend arm forward, palm facing out.	AccY Dominant (Gravity), AccX Weak

---

🎨 Customization

Modifying Phrases


Open app/src/main/java/com/example/besu/EmotionData.kt. You can change the spoken text for any emotion or gesture.


	// Example: Changing the 'Happy' phrase
	Emotion(
	    id = "happy",
	    emoji = "😊",
	    phrase = "I am having a fantastic day!", // Changed from "I am feeling happy."
	    // ...
	)

Tuning Sensitivity


Open wear/src/main/java/com/example/besu/wear/MainActivity.kt.

Look for the thresholds at the top of the sensor logic:


	// Lower this if you have difficulty activating the unlock
	if (abs(y - lastY) > 7.0f) { ... } 
	
	// Increase this if the "Pump" triggers too easily
	if (accelX > 8.0f) { ... }
