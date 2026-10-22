# JARVIS AI — Personal Android Phone Assistant

**JARVIS AI** is a fully functional personal AI assistant for Android built with Kotlin, Jetpack Compose, Material Design 3, and Google Gemini AI.

---

## ⚡ Key Highlights

### 1. Continuous Auto-Listening & Spoken Responses
- **Hands-Free Startup**: As soon as the app opens and microphone permission is granted, JARVIS immediately begins listening without requiring manual button taps.
- **Continuous Loop**: Speaks the confirmation or answer via Android `TextToSpeech`, automatically pauses listening during speech so it never hears itself, and resumes listening immediately once done.
- **Multilingual Understanding**: Understands commands in **English**, **Hindi (हिन्दी)**, and **Hinglish** (e.g., *"Open YouTube"*, *"Torch jalao"*, *"Battery kitni hai"*, *"WhatsApp Dad saying on my way"*, *"Explain quantum computing"*).
- **Holographic Audio Visualizer**: Live neon-cyan waveform reacts dynamically to microphone input decibel levels (`onRmsChanged`).

### 2. Real Android Phone Control
- **Flashlight Control**: Direct camera hardware torch control via `CameraManager.setTorchMode` (*"Turn on flashlight"*, *"Torch band karo"*).
- **Battery Telemetry**: Real-time battery percentage and charging state (*"What is my battery percentage?"*).
- **Application Launching**: Direct package intents for YouTube, WhatsApp, Instagram, Chrome, Google Maps, Camera, Calculator, and Clock.
- **Alarms & Timers**: Configures real Android system alarms (`AlarmClock.ACTION_SET_ALARM`) and timers (`AlarmClock.ACTION_SET_TIMER`).
- **System Settings Quick Access**: One-tap access to Wi-Fi, Bluetooth, Sound, and Display settings.
- **Media Volume Control**: Adjusts system media volume up/down through `AudioManager`.
- **Navigation**: Direct GPS routing via Google Maps (`google.navigation:q=...`).

### 3. SMS & WhatsApp Messaging
- **Contact Resolution**: Queries on-device contacts via `ContactsContract` with permission.
- **Explicit User Authorization**: Displays recipient name, phone number, and message preview in a confirmation modal before taking action.
- **WhatsApp Integration**: Opens the exact conversation with recipient and prefilled message ready to send.
- **SMS Integration**: Prefills SMS composer (`smsto:`) or directly dispatches if `SEND_SMS` is granted.

### 4. Notification Reader
- **Status Bar Listener**: Optional `NotificationListenerService` integration.
- **Voice Readout**: Reads incoming notifications aloud or provides summaries.

### 5. Gemini AI Brain
- **Model**: Powered by `gemini-3.5-flash`.
- **Conversational Q&A**: Answers questions, assists with research, studies, and writing.
- **Secure Architecture**: Secret key injected via `BuildConfig.GEMINI_API_KEY`.
- **Offline Fallback**: Rule-based command engine handles all phone actions even without an internet connection.

---

## 🚀 Setup & API Key Configuration

1. In the **Google AI Studio Secrets panel**, add:
   ```
   GEMINI_API_KEY = your_gemini_api_key
   ```
2. The Secrets Gradle Plugin automatically compiles the key into `BuildConfig.GEMINI_API_KEY`.

---

## 🛠️ Testing Voice Commands

| Category | Example Voice Commands |
|---|---|
| **Torch / Flashlight** | *"Turn on the flashlight"*, *"Torch jalao"*, *"Torch off"*, *"Torch band karo"* |
| **Battery** | *"What is my battery percentage?"*, *"Battery status"*, *"Battery kitni hai"* |
| **App Launching** | *"Open YouTube"*, *"YouTube kholo"*, *"Open WhatsApp"*, *"Open Chrome"* |
| **YouTube Search & Play** | *"Search YouTube for AI news"*, *"Play relaxing music on YouTube"*, *"Search for Doraemon movies on YouTube"* |
| **Phone Calls** | *"Call Rahul"*, *"Call Dad"*, *"Find Pravin in my contacts"*, *"Call this number: 9876543210"* |
| **WhatsApp** | *"Send WhatsApp to Dad saying I am coming home"*, *"WhatsApp Rahul that I am on my way"*, *"Open Mom's WhatsApp chat"* |
| **SMS** | *"Send an SMS to Dad saying I will be home soon"*, *"Cancel the message"* |
| **System Settings** | *"Open Wi-Fi settings"*, *"Open Bluetooth"*, *"Increase volume"*, *"Turn on flashlight"* |
| **AI Questions** | *"Explain artificial intelligence"*, *"Explain this in Hindi"*, *"Who was Nikola Tesla?"* |
