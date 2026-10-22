package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class JarvisVoiceState {
    READY,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class JarvisVoiceEngine(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private val tag = "JarvisVoiceEngine"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val _voiceState = MutableStateFlow(JarvisVoiceState.READY)
    val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription: StateFlow<String> = _liveTranscription.asStateFlow()

    private val _audioRmsDb = MutableStateFlow(0f)
    val audioRmsDb: StateFlow<Float> = _audioRmsDb.asStateFlow()

    private val _isAutoListening = MutableStateFlow(true)
    val isAutoListening: StateFlow<Boolean> = _isAutoListening.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en-IN") // en-IN, hi-IN, en-US
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 0.95f // Slightly deeper, cinematic JARVIS tone

    private var consecutiveErrors = 0
    private var isTtsInitialized = false
    private var isDestroyed = false

    init {
        initTts()
        initSpeechRecognizer()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            applyVoiceSettings()
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = JarvisVoiceState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        _voiceState.value = JarvisVoiceState.READY
                        // Automatically resume listening after speaking if auto-listen is active!
                        if (_isAutoListening.value && !isDestroyed) {
                            mainHandler.postDelayed({
                                if (_voiceState.value != JarvisVoiceState.SPEAKING && !isDestroyed) {
                                    startListeningInternal()
                                }
                            }, 500L) // Brief pause to prevent echo
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        _voiceState.value = JarvisVoiceState.READY
                        if (_isAutoListening.value && !isDestroyed) {
                            startListeningInternal()
                        }
                    }
                }
            })
            Log.d(tag, "TextToSpeech initialized successfully.")
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech: status $status")
        }
    }

    fun setSpeechSettings(rate: Float, pitch: Float) {
        speechRate = rate
        speechPitch = pitch
        applyVoiceSettings()
    }

    fun setLanguage(langCode: String) {
        _selectedLanguage.value = langCode
        applyVoiceSettings()
    }

    private fun applyVoiceSettings() {
        textToSpeech?.let { tts ->
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)
            val locale = when (_selectedLanguage.value) {
                "hi-IN" -> Locale("hi", "IN")
                "en-US" -> Locale.US
                else -> Locale("en", "IN")
            }
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "Speech recognition is not available on this device.")
            _voiceState.value = JarvisVoiceState.ERROR
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@JarvisVoiceEngine)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error creating SpeechRecognizer", e)
        }
    }

    fun setAutoListening(enabled: Boolean) {
        _isAutoListening.value = enabled
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun toggleListening() {
        if (_voiceState.value == JarvisVoiceState.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (isDestroyed) return
        // Do not interrupt while JARVIS is actively speaking
        if (_voiceState.value == JarvisVoiceState.SPEAKING) {
            return
        }
        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (isDestroyed || _voiceState.value == JarvisVoiceState.SPEAKING) return

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            val lang = _selectedLanguage.value
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            // Enable multilingual / Hinglish support
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
        }

        try {
            speechRecognizer?.startListening(intent)
            _voiceState.value = JarvisVoiceState.LISTENING
            _liveTranscription.value = "Listening..."
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
            _voiceState.value = JarvisVoiceState.ERROR
            scheduleSafeRestart(1500L)
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping speech recognition", e)
            }
            if (_voiceState.value == JarvisVoiceState.LISTENING) {
                _voiceState.value = JarvisVoiceState.READY
            }
            _audioRmsDb.value = 0f
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsInitialized || textToSpeech == null) {
            Log.w(tag, "TTS not ready to speak.")
            return
        }

        mainHandler.post {
            // Stop listening while speaking to avoid hearing own voice
            stopListening()
            _voiceState.value = JarvisVoiceState.SPEAKING

            val utteranceId = "JARVIS_RESPONSE_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        if (_voiceState.value == JarvisVoiceState.SPEAKING) {
            _voiceState.value = JarvisVoiceState.READY
            if (_isAutoListening.value) {
                startListening()
            }
        }
    }

    private fun scheduleSafeRestart(delayMs: Long) {
        if (!_isAutoListening.value || isDestroyed) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (_isAutoListening.value && _voiceState.value != JarvisVoiceState.SPEAKING && !isDestroyed) {
                initSpeechRecognizer()
                startListeningInternal()
            }
        }, delayMs)
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        _voiceState.value = JarvisVoiceState.LISTENING
        consecutiveErrors = 0
    }

    override fun onBeginningOfSpeech() {
        _voiceState.value = JarvisVoiceState.LISTENING
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Normalize RMS dB to 0.0 - 1.0 for UI audio wave
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _audioRmsDb.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _audioRmsDb.value = 0f
    }

    override fun onError(error: Int) {
        _audioRmsDb.value = 0f
        consecutiveErrors++
        Log.w(tag, "Speech recognition error code: $error, consecutive: $consecutiveErrors")

        // Error codes:
        // 6: ERROR_SPEECH_TIMEOUT, 7: ERROR_NO_MATCH, 8: ERROR_RECOGNIZER_BUSY
        val delay = when {
            consecutiveErrors > 5 -> 3000L // Back off if repeated silence/errors
            error == SpeechRecognizer.ERROR_NO_MATCH -> 600L
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 500L
            else -> 1000L
        }

        if (_voiceState.value != JarvisVoiceState.SPEAKING) {
            _voiceState.value = JarvisVoiceState.READY
        }

        if (_isAutoListening.value && _voiceState.value != JarvisVoiceState.SPEAKING) {
            scheduleSafeRestart(delay)
        }
    }

    override fun onResults(results: Bundle?) {
        _audioRmsDb.value = 0f
        consecutiveErrors = 0
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull()?.trim() ?: ""

        if (recognizedText.isNotBlank()) {
            _liveTranscription.value = recognizedText
            _voiceState.value = JarvisVoiceState.THINKING
            onCommandRecognized(recognizedText)
        } else {
            if (_isAutoListening.value) {
                scheduleSafeRestart(400L)
            } else {
                _voiceState.value = JarvisVoiceState.READY
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        if (!text.isNullOrBlank()) {
            _liveTranscription.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(tag, "Error destroying speech recognizer", e)
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS", e)
        }
    }
}
