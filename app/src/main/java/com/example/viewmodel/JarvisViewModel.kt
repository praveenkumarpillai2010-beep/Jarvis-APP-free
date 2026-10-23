package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.CommandRouter
import com.example.ai.GeminiService
import com.example.ai.ParsedAction
import com.example.controller.DeviceActionController
import com.example.data.BatteryInfo
import com.example.data.ContactModel
import com.example.data.JarvisChatMessageEntity
import com.example.data.JarvisCommandEntity
import com.example.data.JarvisDatabase
import com.example.service.JarvisNotificationItem
import com.example.service.JarvisNotificationListenerService
import com.example.voice.JarvisVoiceEngine
import com.example.voice.JarvisVoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.service.JarvisVoiceForegroundService

sealed class WhatsAppDraftState {
    data object Idle : WhatsAppDraftState()
    data class DisambiguatingContact(
        val candidateContacts: List<ContactModel>,
        val pendingMessage: String?,
        val originalInput: String
    ) : WhatsAppDraftState()
    data class AwaitingMessage(
        val recipientName: String,
        val recipientNumber: String,
        val originalInput: String
    ) : WhatsAppDraftState()
    data class AwaitingConfirmation(
        val recipientName: String,
        val recipientNumber: String,
        val message: String,
        val originalInput: String
    ) : WhatsAppDraftState()
}

data class PendingActionConfirmation(
    val title: String,
    val description: String,
    val details: String,
    val actionCategory: String,
    val editableMessage: String? = null,
    val onMessageChange: ((String) -> Unit)? = null,
    val onReadAloud: (() -> Unit)? = null,
    val multipleContacts: List<ContactModel>? = null,
    val onSelectContact: ((ContactModel) -> Unit)? = null,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

enum class JarvisTab {
    HOME,
    CHAT,
    CONTROLS,
    MESSAGING,
    NOTIFICATIONS,
    SETTINGS
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "JarvisViewModel"

    val deviceController = DeviceActionController(application.applicationContext)
    val preferencesManager = com.example.data.JarvisPreferencesManager(application.applicationContext)
    val geminiService = GeminiService(preferencesManager)
    private val database = JarvisDatabase.getDatabase(application.applicationContext)
    private val dao = database.jarvisDao()

    lateinit var voiceEngine: JarvisVoiceEngine
        private set

    var currentWindow: android.view.Window? = null
        private set

    var currentActivity: androidx.activity.ComponentActivity? = null
        private set

    fun attachWindow(window: android.view.Window?) {
        currentWindow = window
    }

    fun attachActivity(activity: androidx.activity.ComponentActivity?) {
        currentActivity = activity
    }

    // Gemini API settings flows
    val apiKeyFlow: StateFlow<String> = preferencesManager.apiKeyFlow
    val selectedModelFlow: StateFlow<String> = preferencesManager.selectedModelFlow
    val connectionStatusFlow: StateFlow<com.example.data.GeminiConnectionStatus> = preferencesManager.connectionStatusFlow
    val lastErrorMessageFlow: StateFlow<String> = preferencesManager.lastErrorMessageFlow

    // Voice & System Wake preferences flows
    val voiceProfileFlow: StateFlow<String> = preferencesManager.voiceProfileFlow
    val wakeScreenEnabledFlow: StateFlow<Boolean> = preferencesManager.wakeScreenEnabledFlow
    val foregroundServiceEnabledFlow: StateFlow<Boolean> = preferencesManager.foregroundServiceEnabledFlow

    // WhatsApp Multi-Turn conversation state
    private val _whatsAppDraftState = MutableStateFlow<WhatsAppDraftState>(WhatsAppDraftState.Idle)
    val whatsAppDraftState: StateFlow<WhatsAppDraftState> = _whatsAppDraftState.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<String?>(null)
    val testConnectionResult: StateFlow<String?> = _testConnectionResult.asStateFlow()

    val commandHistory: StateFlow<List<JarvisCommandEntity>> = dao.getAllCommands()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val chatMessages: StateFlow<List<JarvisChatMessageEntity>> = dao.getAllChatMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<JarvisNotificationItem>> = JarvisNotificationListenerService.notifications

    private val _selectedTab = MutableStateFlow(JarvisTab.HOME)
    val selectedTab: StateFlow<JarvisTab> = _selectedTab.asStateFlow()

    private val _batteryState = MutableStateFlow(deviceController.getBatteryStatus())
    val batteryState: StateFlow<BatteryInfo> = _batteryState.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingActionConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingActionConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _systemStatusText = MutableStateFlow("ALL SYSTEMS NOMINAL")
    val systemStatusText: StateFlow<String> = _systemStatusText.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    init {
        initVoiceEngine()
        refreshBattery()
        if (preferencesManager.isForegroundServiceEnabled()) {
            JarvisVoiceForegroundService.start(getApplication<Application>().applicationContext)
        }
    }

    private fun initVoiceEngine() {
        voiceEngine = JarvisVoiceEngine(
            context = getApplication<Application>().applicationContext,
            onCommandRecognized = { text ->
                processUserCommand(text, wasVoice = true)
            }
        )
        voiceEngine.setVoiceProfile(preferencesManager.getVoiceProfile())
    }

    fun setVoiceProfile(profile: String) {
        preferencesManager.saveVoiceProfile(profile)
        voiceEngine.setVoiceProfile(profile)
        val profileName = if (profile == "FEMALE") "Natural Female" else "Futuristic Cinematic"
        _systemStatusText.value = "VOICE PROFILE: $profileName"
        voiceEngine.speak("Voice synthesized to $profileName JARVIS mode.")
    }

    fun setWakeScreenEnabled(enabled: Boolean) {
        preferencesManager.setWakeScreenEnabled(enabled)
        _systemStatusText.value = if (enabled) "SCREEN WAKE-UP ENABLED" else "SCREEN WAKE-UP DISABLED"
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        preferencesManager.setForegroundServiceEnabled(enabled)
        val ctx = getApplication<Application>().applicationContext
        if (enabled) {
            JarvisVoiceForegroundService.start(ctx)
            _systemStatusText.value = "BACKGROUND VOICE ACTIVE"
            voiceEngine.speak("Background voice service online.")
        } else {
            JarvisVoiceForegroundService.stop(ctx)
            _systemStatusText.value = "BACKGROUND VOICE PAUSED"
            voiceEngine.speak("Background voice service stopped.")
        }
    }

    fun setTab(tab: JarvisTab) {
        _selectedTab.value = tab
    }

    fun onMicPermissionResult(granted: Boolean) {
        _hasMicPermission.value = granted
        if (granted) {
            _systemStatusText.value = "VOICE RECOGNITION ONLINE"
            // Start listening automatically on startup as requested
            if (voiceEngine.isAutoListening.value) {
                voiceEngine.startListening()
            }
        } else {
            _systemStatusText.value = "MICROPHONE ACCESS REQUIRED"
        }
    }

    fun refreshBattery() {
        _batteryState.value = deviceController.getBatteryStatus()
    }

    fun toggleTorch() {
        val newState = !_isTorchOn.value
        val res = deviceController.toggleFlashlight(newState)
        if (res.isSuccess) {
            _isTorchOn.value = newState
            voiceEngine.speak(res.getOrNull() ?: "Flashlight toggled.")
        }
    }

    fun saveApiKey(key: String) {
        preferencesManager.saveApiKey(key)
        _systemStatusText.value = "GEMINI API KEY UPDATED"
    }

    fun clearApiKey() {
        preferencesManager.clearApiKey()
        _testConnectionResult.value = null
        _systemStatusText.value = "GEMINI API KEY CLEARED"
    }

    fun setSelectedModel(model: String) {
        preferencesManager.saveSelectedModel(model)
    }

    fun testGeminiConnection(keyOverride: String? = null, modelOverride: String? = null) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionResult.value = null
            val res = geminiService.testConnection(keyOverride, modelOverride)
            _isTestingConnection.value = false
            if (res.isSuccess) {
                val msg = res.getOrNull() ?: "Gemini neural link verified."
                _testConnectionResult.value = msg
                voiceEngine.speak("Gemini connection test passed. Neural link operational.")
            } else {
                val err = res.exceptionOrNull()?.message ?: "Connection test failed."
                _testConnectionResult.value = "Error: $err"
                voiceEngine.speak("Gemini connection test failed. Please verify your API key.")
            }
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        processUserCommand(text, wasVoice = false)
    }

    fun processUserCommand(rawInput: String, wasVoice: Boolean = true) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            // Screen Wake-Up: Wake screen on voice commands if enabled
            if (preferencesManager.isWakeScreenEnabled()) {
                JarvisVoiceForegroundService.wakeScreen(getApplication<Application>().applicationContext)
                currentActivity?.let { act ->
                    val km = act.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        km?.requestDismissKeyguard(act, null)
                    }
                }
            }

            voiceEngine.setVoiceState(JarvisVoiceState.PROCESSING)

            // Save user chat record
            dao.insertChatMessage(
                JarvisChatMessageEntity(
                    role = "USER",
                    content = input
                )
            )

            // 1. Check if user is in an active WhatsApp multi-turn voice dialog
            val currentDraft = _whatsAppDraftState.value
            when (currentDraft) {
                is WhatsAppDraftState.DisambiguatingContact -> {
                    val lower = input.lowercase()
                    if (lower in listOf("cancel", "cancel karo", "stop", "no", "nahi", "mat bhejo")) {
                        _whatsAppDraftState.value = WhatsAppDraftState.Idle
                        _pendingConfirmation.value = null
                        recordAndRespond("CANCEL_ACTION", input, "CANCELLED", "WhatsApp message cancelled, sir.", wasVoice)
                        return@launch
                    }
                    val chosen = currentDraft.candidateContacts.firstOrNull { contact ->
                        lower.contains(contact.name.lowercase()) ||
                                contact.name.lowercase().contains(lower)
                    } ?: if (lower.contains("first") || lower.contains("pehla") || lower == "1") {
                        currentDraft.candidateContacts.firstOrNull()
                    } else if (lower.contains("second") || lower.contains("doosra") || lower == "2") {
                        currentDraft.candidateContacts.getOrNull(1)
                    } else null

                    if (chosen != null) {
                        _pendingConfirmation.value = null
                        if (currentDraft.pendingMessage.isNullOrBlank()) {
                            _whatsAppDraftState.value = WhatsAppDraftState.AwaitingMessage(chosen.name, chosen.phoneNumber, currentDraft.originalInput)
                            recordAndRespond("WHATSAPP_AWAITING_MESSAGE", input, "IN_PROGRESS", "What message would you like to send?", wasVoice)
                        } else {
                            _whatsAppDraftState.value = WhatsAppDraftState.AwaitingConfirmation(chosen.name, chosen.phoneNumber, currentDraft.pendingMessage, currentDraft.originalInput)
                            showWhatsAppConfirmationUI(chosen.name, chosen.phoneNumber, currentDraft.pendingMessage, currentDraft.originalInput, wasVoice)
                        }
                        return@launch
                    } else {
                        val names = currentDraft.candidateContacts.take(3).joinToString(" or ") { it.name }
                        voiceEngine.speak("Please say which contact you mean: $names.")
                        return@launch
                    }
                }

                is WhatsAppDraftState.AwaitingMessage -> {
                    val lower = input.lowercase()
                    if (lower in listOf("cancel", "cancel karo", "stop", "no", "nahi", "mat bhejo")) {
                        _whatsAppDraftState.value = WhatsAppDraftState.Idle
                        _pendingConfirmation.value = null
                        recordAndRespond("CANCEL_ACTION", input, "CANCELLED", "WhatsApp message cancelled, sir.", wasVoice)
                        return@launch
                    }
                    // Strip leading prefixes like "Tell him", "Tell her", "Say that", "Bol do", "Message is", etc.
                    val cleaned = input
                        .replace("^(?i)(?:tell (?:him|her|them)|saying|say that|say|message is|ki|bol do|bata do)[,\\s]*".toRegex(), "")
                        .trim()
                    val finalMsg = if (cleaned.isNotBlank()) cleaned else input

                    _whatsAppDraftState.value = WhatsAppDraftState.AwaitingConfirmation(
                        recipientName = currentDraft.recipientName,
                        recipientNumber = currentDraft.recipientNumber,
                        message = finalMsg,
                        originalInput = currentDraft.originalInput
                    )
                    showWhatsAppConfirmationUI(currentDraft.recipientName, currentDraft.recipientNumber, finalMsg, currentDraft.originalInput, wasVoice)
                    return@launch
                }

                is WhatsAppDraftState.AwaitingConfirmation -> {
                    val lower = input.lowercase()
                    val isConfirmed = lower in listOf("yes", "send it", "send", "haan", "bhejo", "bhej do", "sure", "confirm", "proceed", "okay send", "yes send") ||
                            lower.startsWith("yes") || lower.startsWith("send") || lower.startsWith("haan") || lower.startsWith("bhej")

                    val isCancelled = lower in listOf("no", "cancel", "don't send", "mat bhejo", "cancel karo", "stop", "nahi") ||
                            lower.startsWith("no") || lower.startsWith("cancel") || lower.startsWith("mat")

                    if (isConfirmed) {
                        _whatsAppDraftState.value = WhatsAppDraftState.Idle
                        _pendingConfirmation.value = null
                        val res = deviceController.prepareWhatsApp(currentDraft.recipientNumber, currentDraft.message)
                        val reply = if (res.isSuccess) {
                            "Opening WhatsApp chat. Please tap send to deliver your message."
                        } else {
                            res.exceptionOrNull()?.message ?: "Failed to open WhatsApp."
                        }
                        recordAndRespond("OPEN_WHATSAPP_CHAT", currentDraft.originalInput, if (res.isSuccess) "SUCCESS" else "FAILED", reply, wasVoice)
                        return@launch
                    } else if (isCancelled) {
                        _whatsAppDraftState.value = WhatsAppDraftState.Idle
                        _pendingConfirmation.value = null
                        recordAndRespond("CANCEL_ACTION", currentDraft.originalInput, "CANCELLED", "WhatsApp message cancelled, sir.", wasVoice)
                        return@launch
                    } else {
                        voiceEngine.speak("Shall I send this message to ${currentDraft.recipientName}? Please say 'yes, send it' or 'cancel'.")
                        return@launch
                    }
                }

                WhatsAppDraftState.Idle -> { /* proceed */ }
            }

            // 2. Check if a general pending confirmation (call / SMS) is awaiting user reply
            val pending = _pendingConfirmation.value
            if (pending != null) {
                val lower = input.lowercase()
                val isConfirmed = lower in listOf("yes", "call", "send", "haan", "sure", "confirm", "proceed", "yes please", "call karo") ||
                        lower.startsWith("yes") || lower.startsWith("haan")
                val isCancelled = lower in listOf("no", "cancel", "cancel karo", "stop", "nahi", "mat karo") ||
                        lower.startsWith("no") || lower.startsWith("cancel")
                if (isConfirmed) {
                    pending.onConfirm()
                    return@launch
                } else if (isCancelled) {
                    pending.onCancel()
                    return@launch
                }
            }

            val parsed = CommandRouter.parse(input)

            // If the local regex gave GeneralChat, check if Gemini NLU can map this natural language utterance into a device action
            if (parsed is ParsedAction.GeneralChat && preferencesManager.getActiveApiKey().isNotBlank()) {
                val interpRes = geminiService.interpretCommandWithGemini(
                    utterance = input,
                    conversationHistory = chatMessages.value.takeLast(6).map { it.role to it.content }
                )
                if (interpRes.isSuccess) {
                    val interp = interpRes.getOrThrow()
                    if (interp.actionType == "ACTION" && interp.actionCategory != null) {
                        val mappedAction = mapGeminiActionToParsed(interp.actionCategory, interp.parameters, input)
                        if (mappedAction != null) {
                            executeAction(mappedAction, originalInput = input, wasVoice = wasVoice)
                            return@launch
                        }
                    } else if (interp.actionType == "CHAT" || interp.actionType == "CLARIFY") {
                        val reply = interp.spokenResponse.ifBlank { interp.detailedResponse }
                        recordAndRespond(
                            category = "GEMINI_NLU_CHAT",
                            command = input,
                            status = "SUCCESS",
                            reply = reply,
                            wasVoice = wasVoice
                        )
                        return@launch
                    }
                }
            }

            executeAction(parsed, originalInput = input, wasVoice = wasVoice)
        }
    }

    private fun mapGeminiActionToParsed(category: String, params: Map<String, String>, input: String): ParsedAction? {
        return when (category.uppercase()) {
            "FLASHLIGHT_ON" -> ParsedAction.FlashlightOn
            "FLASHLIGHT_OFF" -> ParsedAction.FlashlightOff
            "GET_BATTERY_STATUS" -> ParsedAction.BatteryStatus
            "OPEN_APP" -> {
                val app = params["target"] ?: params["appName"] ?: params["app"] ?: ""
                if (app.isNotBlank()) ParsedAction.OpenApp(app) else null
            }
            "SEARCH_YOUTUBE" -> {
                val query = params["query"] ?: input
                val autoPlay = params["autoPlay"]?.toBoolean() ?: true
                ParsedAction.SearchYouTube(query, autoPlay)
            }
            "CREATE_TIMER" -> {
                val sec = params["seconds"]?.toIntOrNull() ?: 300
                ParsedAction.CreateTimer(sec, "JARVIS Timer")
            }
            "CREATE_ALARM" -> {
                val h = params["hour"]?.toIntOrNull() ?: 7
                val m = params["minute"]?.toIntOrNull() ?: 0
                ParsedAction.CreateAlarm(h, m, "JARVIS Alarm")
            }
            "CALL_CONTACT" -> {
                val rec = params["recipient"] ?: params["name"] ?: ""
                if (rec.isNotBlank()) ParsedAction.CallContact(rec) else null
            }
            "DRAFT_WHATSAPP" -> {
                val rec = params["recipient"] ?: ""
                val msg = params["message"] ?: ""
                if (rec.isNotBlank()) ParsedAction.DraftWhatsApp(rec, msg) else null
            }
            "DRAFT_SMS" -> {
                val rec = params["recipient"] ?: ""
                val msg = params["message"] ?: ""
                if (rec.isNotBlank()) ParsedAction.DraftSms(rec, msg) else null
            }
            "TAKE_SCREENSHOT" -> ParsedAction.TakeScreenshot
            "START_NAVIGATION" -> {
                val dest = params["destination"] ?: ""
                if (dest.isNotBlank()) ParsedAction.StartNavigation(dest) else null
            }
            "OPEN_SYSTEM_SETTINGS" -> {
                val setting = params["setting"] ?: "settings"
                ParsedAction.OpenSetting(setting)
            }
            else -> null
        }
    }

    private suspend fun executeAction(action: ParsedAction, originalInput: String, wasVoice: Boolean) {
        when (action) {
            is ParsedAction.CancelAction -> {
                if (_pendingConfirmation.value != null) {
                    _pendingConfirmation.value?.onCancel?.invoke()
                } else {
                    voiceEngine.stopSpeaking()
                    voiceEngine.stopListening()
                    recordAndRespond(
                        category = "CANCEL_ACTION",
                        command = originalInput,
                        status = "SUCCESS",
                        reply = "Action cancelled, sir.",
                        wasVoice = wasVoice
                    )
                }
            }

            is ParsedAction.SearchYouTube -> {
                val res = deviceController.searchYouTube(action.query, action.autoPlay)
                val reply = res.getOrElse { "Could not launch YouTube: ${it.message}" }
                recordAndRespond(
                    category = "SEARCH_YOUTUBE",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.FlashlightOn -> {
                val res = deviceController.toggleFlashlight(true)
                val reply = res.getOrElse { "Could not turn on flashlight: ${it.message}" }
                _isTorchOn.value = res.isSuccess
                recordAndRespond(
                    category = "FLASHLIGHT_ON",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.FlashlightOff -> {
                val res = deviceController.toggleFlashlight(false)
                val reply = res.getOrElse { "Could not turn off flashlight: ${it.message}" }
                _isTorchOn.value = false
                recordAndRespond(
                    category = "FLASHLIGHT_OFF",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.BatteryStatus -> {
                refreshBattery()
                val battery = _batteryState.value
                val reply = battery.statusText
                recordAndRespond(
                    category = "GET_BATTERY_STATUS",
                    command = originalInput,
                    status = "SUCCESS",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.OpenApp -> {
                val res = deviceController.launchApp(action.appName)
                val reply = res.getOrElse { "App launch failed: ${it.message}" }
                recordAndRespond(
                    category = "OPEN_APP",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.CreateAlarm -> {
                val res = deviceController.setAlarm(action.hour, action.minute, action.label)
                val reply = res.getOrElse { "Could not configure alarm: ${it.message}" }
                recordAndRespond(
                    category = "CREATE_ALARM",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.CreateTimer -> {
                val res = deviceController.setTimer(action.seconds, action.label)
                val reply = res.getOrElse { "Could not set timer: ${it.message}" }
                recordAndRespond(
                    category = "CREATE_TIMER",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.OpenSetting -> {
                val res = deviceController.openSettings(action.settingName)
                val reply = res.getOrElse { "Could not open settings: ${it.message}" }
                recordAndRespond(
                    category = "OPEN_SYSTEM_SETTINGS",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.StartNavigation -> {
                val res = deviceController.startNavigation(action.destination)
                val reply = res.getOrElse { "Navigation error: ${it.message}" }
                recordAndRespond(
                    category = "START_NAVIGATION",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.FindContact -> {
                handleFindContact(action.query, originalInput, wasVoice)
            }

            is ParsedAction.CallContact -> {
                handleCallAction(action.query, originalInput, wasVoice)
            }

            is ParsedAction.DraftWhatsApp -> {
                handleWhatsAppAction(action.recipient, action.message, originalInput, wasVoice)
            }

            is ParsedAction.DraftSms -> {
                handleSmsAction(action.recipient, action.message, originalInput, wasVoice)
            }

            is ParsedAction.ReadNotifications -> {
                handleReadNotifications(originalInput, wasVoice)
            }

            is ParsedAction.SearchWeb -> {
                val res = deviceController.searchWeb(action.query)
                val reply = res.getOrElse { "Web search error: ${it.message}" }
                recordAndRespond(
                    category = "SEARCH_WEB",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.AdjustVolume -> {
                val res = deviceController.adjustVolume(action.direction)
                val reply = res.getOrElse { "Volume adjustment failed: ${it.message}" }
                recordAndRespond(
                    category = "ADJUST_VOLUME",
                    command = originalInput,
                    status = if (res.isSuccess) "SUCCESS" else "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }

            is ParsedAction.TakeScreenshot -> {
                voiceEngine.setVoiceState(JarvisVoiceState.EXECUTING)
                deviceController.takeScreenshot(currentWindow) { res ->
                    val reply = res.getOrElse { "To capture external screens, please use your device's hardware shortcut: Power + Volume Down." }
                    viewModelScope.launch {
                        recordAndRespond(
                            category = "TAKE_SCREENSHOT",
                            command = originalInput,
                            status = if (res.isSuccess) "SUCCESS" else "FAILED",
                            reply = reply,
                            wasVoice = wasVoice
                        )
                    }
                }
            }

            is ParsedAction.GeneralChat -> {
                handleGeneralChat(action.query, wasVoice)
            }
        }
    }

    private suspend fun handleFindContact(query: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(query)
        val reply = if (contacts.isEmpty()) {
            "No contact found for '$query' in your phone contacts."
        } else if (contacts.size == 1) {
            "Found ${contacts[0].name}: ${contacts[0].phoneNumber}."
        } else {
            "Found ${contacts.size} matching contacts for '$query': " +
                    contacts.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
        }
        recordAndRespond(
            category = "FIND_CONTACT",
            command = originalInput,
            status = if (contacts.isNotEmpty()) "SUCCESS" else "NOT_FOUND",
            reply = reply,
            wasVoice = wasVoice
        )
    }

    private suspend fun handleCallAction(query: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(query)
        if (contacts.isEmpty()) {
            if (query.matches("[0-9+\\-\\s]+".toRegex())) {
                showCallConfirmation(query, query, originalInput, wasVoice)
            } else {
                val reply = "No contact found matching '$query'. Please verify contacts permission or spelling."
                recordAndRespond(
                    category = "CALL_CONTACT",
                    command = originalInput,
                    status = "FAILED",
                    reply = reply,
                    wasVoice = wasVoice
                )
            }
        } else if (contacts.size == 1) {
            val contact = contacts.first()
            showCallConfirmation(contact.name, contact.phoneNumber, originalInput, wasVoice)
        } else {
            // Multiple contacts found: Disambiguate!
            _pendingConfirmation.value = PendingActionConfirmation(
                title = "SELECT CONTACT TO CALL",
                description = "Found ${contacts.size} contacts matching '$query'. Please select one:",
                details = contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" },
                actionCategory = "CALL_CONTACT",
                multipleContacts = contacts,
                onSelectContact = { chosen ->
                    _pendingConfirmation.value = null
                    showCallConfirmation(chosen.name, chosen.phoneNumber, originalInput, wasVoice)
                },
                onConfirm = {},
                onCancel = {
                    _pendingConfirmation.value = null
                    viewModelScope.launch {
                        recordAndRespond(
                            category = "CANCEL_ACTION",
                            command = originalInput,
                            status = "CANCELLED",
                            reply = "Call cancelled, sir.",
                            wasVoice = wasVoice
                        )
                    }
                }
            )
            voiceEngine.speak("Multiple contacts found for $query. Please choose which person to call.")
        }
    }

    private fun showCallConfirmation(name: String, number: String, originalInput: String, wasVoice: Boolean) {
        _pendingConfirmation.value = PendingActionConfirmation(
            title = "CONFIRM PHONE CALL",
            description = "Would you like to initiate a call to $name?",
            details = "Target Number: $number",
            actionCategory = "CALL_CONTACT",
            onConfirm = {
                _pendingConfirmation.value = null
                val res = deviceController.dialNumber(number)
                val reply = res.getOrElse { "Could not open dialer: ${it.message}" }
                viewModelScope.launch {
                    recordAndRespond(
                        category = "CALL_CONTACT",
                        command = originalInput,
                        status = if (res.isSuccess) "SUCCESS" else "FAILED",
                        reply = reply,
                        wasVoice = wasVoice
                    )
                }
            },
            onCancel = {
                _pendingConfirmation.value = null
                viewModelScope.launch {
                    recordAndRespond(
                        category = "CANCEL_ACTION",
                        command = originalInput,
                        status = "CANCELLED",
                        reply = "Call cancelled, sir.",
                        wasVoice = wasVoice
                    )
                }
            }
        )
        voiceEngine.speak("Shall I place a call to $name?")
    }

    private suspend fun handleWhatsAppAction(recipient: String, message: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(recipient)

        if (contacts.size > 1) {
            _whatsAppDraftState.value = WhatsAppDraftState.DisambiguatingContact(contacts, message.ifBlank { null }, originalInput)
            // Disambiguation
            _pendingConfirmation.value = PendingActionConfirmation(
                title = "SELECT WHATSAPP CONTACT",
                description = "Multiple contacts found for '$recipient'. Select intended person:",
                details = contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" },
                actionCategory = "DRAFT_WHATSAPP",
                multipleContacts = contacts,
                onSelectContact = { chosen ->
                    _pendingConfirmation.value = null
                    if (message.isBlank()) {
                        _whatsAppDraftState.value = WhatsAppDraftState.AwaitingMessage(chosen.name, chosen.phoneNumber, originalInput)
                        voiceEngine.speak("What message would you like to send to ${chosen.name}?")
                    } else {
                        _whatsAppDraftState.value = WhatsAppDraftState.AwaitingConfirmation(chosen.name, chosen.phoneNumber, message, originalInput)
                        showWhatsAppConfirmationUI(chosen.name, chosen.phoneNumber, message, originalInput, wasVoice)
                    }
                },
                onConfirm = {},
                onCancel = {
                    _pendingConfirmation.value = null
                    _whatsAppDraftState.value = WhatsAppDraftState.Idle
                    viewModelScope.launch {
                        recordAndRespond("CANCEL_ACTION", originalInput, "CANCELLED", "WhatsApp message cancelled.", wasVoice)
                    }
                }
            )
            val names = contacts.take(3).joinToString(" or ") { it.name }
            voiceEngine.speak("I found multiple contacts for $recipient: $names. Which one would you like to message?")
        } else {
            val contact = contacts.firstOrNull()
            val finalName = contact?.name ?: recipient
            val finalNumber = contact?.phoneNumber ?: recipient

            if (message.isBlank()) {
                _whatsAppDraftState.value = WhatsAppDraftState.AwaitingMessage(finalName, finalNumber, originalInput)
                recordAndRespond("WHATSAPP_AWAITING_MESSAGE", originalInput, "IN_PROGRESS", "What message would you like to send?", wasVoice)
            } else {
                _whatsAppDraftState.value = WhatsAppDraftState.AwaitingConfirmation(finalName, finalNumber, message, originalInput)
                showWhatsAppConfirmationUI(finalName, finalNumber, message, originalInput, wasVoice)
            }
        }
    }

    private fun showWhatsAppConfirmationUI(
        name: String,
        number: String,
        initialMessage: String,
        originalInput: String,
        wasVoice: Boolean
    ) {
        var currentMsg = initialMessage
        _pendingConfirmation.value = PendingActionConfirmation(
            title = "CONFIRM WHATSAPP MESSAGE",
            description = "Recipient: $name ($number)",
            details = if (currentMsg.isNotBlank()) "Draft: \"$currentMsg\"" else "Open conversation",
            actionCategory = "DRAFT_WHATSAPP",
            editableMessage = currentMsg,
            onMessageChange = { updated ->
                currentMsg = updated
                _whatsAppDraftState.value = WhatsAppDraftState.AwaitingConfirmation(name, number, updated, originalInput)
            },
            onReadAloud = {
                voiceEngine.speak(if (currentMsg.isNotBlank()) "Message to $name says: $currentMsg" else "No message drafted for $name.")
            },
            onConfirm = {
                _pendingConfirmation.value = null
                _whatsAppDraftState.value = WhatsAppDraftState.Idle
                val res = deviceController.prepareWhatsApp(number, currentMsg)
                val reply = if (res.isSuccess) {
                    "Opening WhatsApp chat. Please tap send to deliver your message."
                } else {
                    res.exceptionOrNull()?.message ?: "Failed to open WhatsApp."
                }
                viewModelScope.launch {
                    recordAndRespond(
                        category = "OPEN_WHATSAPP_CHAT",
                        command = originalInput,
                        status = if (res.isSuccess) "SUCCESS" else "FAILED",
                        reply = reply,
                        wasVoice = wasVoice
                    )
                }
            },
            onCancel = {
                _pendingConfirmation.value = null
                _whatsAppDraftState.value = WhatsAppDraftState.Idle
                viewModelScope.launch {
                    recordAndRespond(
                        category = "CANCEL_ACTION",
                        command = originalInput,
                        status = "CANCELLED",
                        reply = "WhatsApp message cancelled, sir.",
                        wasVoice = wasVoice
                    )
                }
            }
        )
        val promptSpeech = if (initialMessage.isNotBlank()) {
            "Send this message to $name: '$initialMessage'. Shall I send it?"
        } else {
            "Ready to open WhatsApp chat with $name. Shall I proceed?"
        }
        voiceEngine.speak(promptSpeech)
    }

    private suspend fun handleSmsAction(recipient: String, message: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(recipient)

        if (contacts.size > 1) {
            _pendingConfirmation.value = PendingActionConfirmation(
                title = "SELECT SMS CONTACT",
                description = "Multiple contacts found for '$recipient'. Select intended recipient:",
                details = contacts.joinToString("\n") { "${it.name}: ${it.phoneNumber}" },
                actionCategory = "DRAFT_SMS",
                multipleContacts = contacts,
                onSelectContact = { chosen ->
                    _pendingConfirmation.value = null
                    showSmsDraftDialog(chosen.name, chosen.phoneNumber, message, originalInput, wasVoice)
                },
                onConfirm = {},
                onCancel = {
                    _pendingConfirmation.value = null
                    viewModelScope.launch {
                        recordAndRespond("CANCEL_ACTION", originalInput, "CANCELLED", "SMS cancelled.", wasVoice)
                    }
                }
            )
            voiceEngine.speak("Multiple contacts found for $recipient. Please select one.")
        } else {
            val contact = contacts.firstOrNull()
            val finalName = contact?.name ?: recipient
            val finalNumber = contact?.phoneNumber ?: recipient
            showSmsDraftDialog(finalName, finalNumber, message, originalInput, wasVoice)
        }
    }

    private fun showSmsDraftDialog(
        name: String,
        number: String,
        initialMessage: String,
        originalInput: String,
        wasVoice: Boolean
    ) {
        var currentMsg = initialMessage
        _pendingConfirmation.value = PendingActionConfirmation(
            title = "CONFIRM SMS MESSAGE",
            description = "Recipient: $name ($number)",
            details = if (currentMsg.isNotBlank()) "Draft: \"$currentMsg\"" else "Opening SMS composer",
            actionCategory = "DRAFT_SMS",
            editableMessage = currentMsg,
            onMessageChange = { updated ->
                currentMsg = updated
            },
            onReadAloud = {
                voiceEngine.speak(if (currentMsg.isNotBlank()) "SMS to $name says: $currentMsg" else "No SMS message text drafted.")
            },
            onConfirm = {
                _pendingConfirmation.value = null
                val res = deviceController.prepareSms(number, currentMsg)
                val reply = res.getOrElse { "Failed to draft SMS: ${it.message}" }
                viewModelScope.launch {
                    recordAndRespond(
                        category = "SEND_SMS",
                        command = originalInput,
                        status = if (res.isSuccess) "SUCCESS" else "FAILED",
                        reply = reply,
                        wasVoice = wasVoice
                    )
                }
            },
            onCancel = {
                _pendingConfirmation.value = null
                viewModelScope.launch {
                    recordAndRespond(
                        category = "CANCEL_ACTION",
                        command = originalInput,
                        status = "CANCELLED",
                        reply = "SMS cancelled, sir.",
                        wasVoice = wasVoice
                    )
                }
            }
        )
        val promptSpeech = if (initialMessage.isNotBlank()) {
            "Ready to draft SMS to $name with message: $initialMessage. Please confirm."
        } else {
            "Ready to open SMS composer for $name. Please confirm."
        }
        voiceEngine.speak(promptSpeech)
    }

    private suspend fun handleReadNotifications(originalInput: String, wasVoice: Boolean) {
        val current = notifications.value
        val reply = if (current.isEmpty()) {
            "No unread notifications at the moment, sir. Ensure Notification Access is enabled in Settings."
        } else {
            val summary = StringBuilder("You have ${current.size} notifications. ")
            current.take(3).forEachIndexed { idx, item ->
                summary.append("${idx + 1}: From ${item.appName}, ${item.title}. ")
            }
            summary.toString()
        }

        recordAndRespond(
            category = "READ_NOTIFICATIONS",
            command = originalInput,
            status = "SUCCESS",
            reply = reply,
            wasVoice = wasVoice
        )
    }

    private suspend fun handleGeneralChat(query: String, wasVoice: Boolean) {
        val recentChat = chatMessages.value.takeLast(6).map { it.role to it.content }
        val result = geminiService.askJarvis(query, recentChat)
        val reply = result.getOrElse { err ->
            "I heard '$query'. For complex inquiries, please ensure your Gemini API key is configured in AI Studio Secrets. Local phone controls and YouTube search remain fully active."
        }

        recordAndRespond(
            category = "GENERAL_AI_CHAT",
            command = query,
            status = if (result.isSuccess) "SUCCESS" else "OFFLINE_RESPONSE",
            reply = reply,
            wasVoice = wasVoice
        )
    }

    private suspend fun recordAndRespond(
        category: String,
        command: String,
        status: String,
        reply: String,
        wasVoice: Boolean
    ) {
        dao.insertCommand(
            JarvisCommandEntity(
                commandText = command,
                actionCategory = category,
                status = status,
                resultMessage = reply,
                wasVoiceTriggered = wasVoice
            )
        )

        dao.insertChatMessage(
            JarvisChatMessageEntity(
                role = "JARVIS",
                content = reply,
                actionCategory = category
            )
        )

        _systemStatusText.value = "LAST ACTION: $category ($status)"

        // Voice output
        voiceEngine.speak(reply)
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAllCommands()
            dao.clearAllChatMessages()
        }
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
    }
}
