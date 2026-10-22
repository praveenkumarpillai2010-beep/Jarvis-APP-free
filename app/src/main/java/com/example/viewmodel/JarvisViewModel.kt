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

data class PendingActionConfirmation(
    val title: String,
    val description: String,
    val details: String,
    val actionCategory: String,
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
    val geminiService = GeminiService()
    private val database = JarvisDatabase.getDatabase(application.applicationContext)
    private val dao = database.jarvisDao()

    lateinit var voiceEngine: JarvisVoiceEngine
        private set

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
    }

    private fun initVoiceEngine() {
        voiceEngine = JarvisVoiceEngine(
            context = getApplication<Application>().applicationContext,
            onCommandRecognized = { text ->
                processUserCommand(text, wasVoice = true)
            }
        )
    }

    fun setTab(tab: JarvisTab) {
        _selectedTab.value = tab
    }

    fun onMicPermissionResult(granted: Boolean) {
        _hasMicPermission.value = granted
        if (granted) {
            _systemStatusText.value = "VOICE RECOGNITION ONLINE"
            // Start listening automatically on startup as requested!
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

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        processUserCommand(text, wasVoice = false)
    }

    fun processUserCommand(rawInput: String, wasVoice: Boolean = true) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        viewModelScope.launch {
            // Save user chat record
            dao.insertChatMessage(
                JarvisChatMessageEntity(
                    role = "USER",
                    content = input
                )
            )

            val parsed = CommandRouter.parse(input)
            executeAction(parsed, originalInput = input, wasVoice = wasVoice)
        }
    }

    private suspend fun executeAction(action: ParsedAction, originalInput: String, wasVoice: Boolean) {
        when (action) {
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

            is ParsedAction.GeneralChat -> {
                handleGeneralChat(action.query, wasVoice)
            }
        }
    }

    private suspend fun handleCallAction(query: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(query)
        if (contacts.isEmpty()) {
            // Direct dial fallback if query is digits
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
        } else {
            val contact = contacts.first()
            showCallConfirmation(contact.name, contact.phoneNumber, originalInput, wasVoice)
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
        val targetNumber = contacts.firstOrNull()?.phoneNumber ?: recipient

        _pendingConfirmation.value = PendingActionConfirmation(
            title = "CONFIRM WHATSAPP MESSAGE",
            description = "Recipient: $recipient ($targetNumber)",
            details = "Message: \"$message\"",
            actionCategory = "DRAFT_WHATSAPP",
            onConfirm = {
                _pendingConfirmation.value = null
                val res = deviceController.prepareWhatsApp(targetNumber, message)
                val reply = res.getOrElse { "Failed to launch WhatsApp: ${it.message}" }
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
                viewModelScope.launch {
                    recordAndRespond(
                        category = "CANCEL_ACTION",
                        command = originalInput,
                        status = "CANCELLED",
                        reply = "WhatsApp message cancelled.",
                        wasVoice = wasVoice
                    )
                }
            }
        )
        voiceEngine.speak("Ready to prepare WhatsApp message to $recipient. Please confirm.")
    }

    private suspend fun handleSmsAction(recipient: String, message: String, originalInput: String, wasVoice: Boolean) {
        val contacts = deviceController.searchContacts(recipient)
        val targetNumber = contacts.firstOrNull()?.phoneNumber ?: recipient

        _pendingConfirmation.value = PendingActionConfirmation(
            title = "CONFIRM SMS MESSAGE",
            description = "Recipient: $recipient ($targetNumber)",
            details = "Message: \"$message\"",
            actionCategory = "DRAFT_SMS",
            onConfirm = {
                _pendingConfirmation.value = null
                val res = deviceController.prepareSms(targetNumber, message)
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
        voiceEngine.speak("Ready to draft SMS to $recipient. Please confirm.")
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
        // Collect recent turns for context
        val recentChat = chatMessages.value.takeLast(6).map { it.role to it.content }

        val result = geminiService.askJarvis(query, recentChat)
        val reply = result.getOrElse { err ->
            "I heard '$query'. For complex inquiries, please ensure your Gemini API key is configured in AI Studio Secrets. Local phone controls remain fully active."
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
