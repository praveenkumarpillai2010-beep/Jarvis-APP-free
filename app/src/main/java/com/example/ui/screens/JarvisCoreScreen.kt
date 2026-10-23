package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.JarvisArcReactor
import com.example.ui.components.JarvisAudioWaveform
import com.example.ui.components.JarvisHudBar
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisNeonGlow
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning
import com.example.viewmodel.JarvisViewModel
import com.example.voice.JarvisVoiceState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisCoreScreen(
    viewModel: JarvisViewModel,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voiceEngine = viewModel.voiceEngine
    val voiceState by voiceEngine.voiceState.collectAsState()
    val liveTranscription by voiceEngine.liveTranscription.collectAsState()
    val audioRms by voiceEngine.audioRmsDb.collectAsState()
    val isAutoListening by voiceEngine.isAutoListening.collectAsState()
    val batteryInfo by viewModel.batteryState.collectAsState()
    val systemStatus by viewModel.systemStatusText.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val hasMicPermission by viewModel.hasMicPermission.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD Top Bar
            JarvisHudBar(
                batteryInfo = batteryInfo,
                systemStatus = systemStatus,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Auto-Listening Mode Control Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                    .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAutoListening) Icons.Default.Hearing else Icons.Default.MicOff,
                            contentDescription = "Auto Listen",
                            tint = if (isAutoListening) JarvisCyan else JarvisTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isAutoListening) "CONTINUOUS AUTO-LISTENING: ON" else "CONTINUOUS AUTO-LISTENING: OFF",
                                color = if (isAutoListening) JarvisCyan else JarvisTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isAutoListening) "Always listens for voice commands while app is open" else "Tap microphone button to speak",
                                color = JarvisTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = isAutoListening,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasMicPermission) {
                                onRequestMicPermission()
                            } else {
                                voiceEngine.setAutoListening(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisCyan,
                            checkedTrackColor = JarvisElectricBlue.copy(alpha = 0.5f),
                            uncheckedThumbColor = JarvisTextMuted,
                            uncheckedTrackColor = JarvisSurface
                        ),
                        modifier = Modifier.testTag("auto_listening_toggle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Futuristic Arc Reactor AI Core in Center
            JarvisArcReactor(
                voiceState = voiceState,
                audioRms = audioRms,
                sizeDp = 210.dp,
                onClick = {
                    if (!hasMicPermission) {
                        onRequestMicPermission()
                    } else {
                        voiceEngine.toggleListening()
                    }
                },
                modifier = Modifier.testTag("jarvis_arc_reactor_core")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // AI Status Banner
            val statusColor = when (voiceState) {
                JarvisVoiceState.READY, JarvisVoiceState.IDLE -> JarvisCyan
                JarvisVoiceState.LISTENING -> JarvisNeonGlow
                JarvisVoiceState.THINKING, JarvisVoiceState.PROCESSING -> JarvisWarning
                JarvisVoiceState.EXECUTING -> JarvisCyan
                JarvisVoiceState.SPEAKING -> JarvisElectricBlue
                JarvisVoiceState.ERROR -> JarvisError
            }
            val statusLabel = when (voiceState) {
                JarvisVoiceState.READY, JarvisVoiceState.IDLE -> "JARVIS READY // STANDBY"
                JarvisVoiceState.LISTENING -> "JARVIS IS LISTENING..."
                JarvisVoiceState.THINKING, JarvisVoiceState.PROCESSING -> "PROCESSING COMMAND..."
                JarvisVoiceState.EXECUTING -> "EXECUTING ACTION..."
                JarvisVoiceState.SPEAKING -> "JARVIS IS SPEAKING..."
                JarvisVoiceState.ERROR -> "SYSTEM ERROR // TAP TO RETRY"
            }

            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Audio Waveform Visualizer
            JarvisAudioWaveform(
                voiceState = voiceState,
                audioRms = audioRms,
                height = 36.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Speech Transcription Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisDeepNavy.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE TRANSCRIPTION",
                            color = JarvisCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (voiceState == JarvisVoiceState.SPEAKING) {
                            Text(
                                text = "TAP TO INTERRUPT",
                                color = JarvisWarning,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { voiceEngine.stopSpeaking() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (liveTranscription.isNotBlank()) "\"$liveTranscription\"" else "Awaiting your command, sir...",
                        color = JarvisTextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Microphone Action Button (Backup)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (voiceState == JarvisVoiceState.LISTENING) JarvisCyan else JarvisDeepNavy
                        )
                        .border(
                            2.dp,
                            if (voiceState == JarvisVoiceState.LISTENING) Color.White else JarvisCyan,
                            CircleShape
                        )
                        .clickable {
                            if (!hasMicPermission) {
                                onRequestMicPermission()
                            } else {
                                voiceEngine.toggleListening()
                            }
                        }
                        .testTag("manual_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState == JarvisVoiceState.LISTENING) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Microphone Toggle",
                        tint = if (voiceState == JarvisVoiceState.LISTENING) Color(0xFF002233) else JarvisCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                if (voiceState == JarvisVoiceState.SPEAKING) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(JarvisDeepNavy)
                            .border(1.dp, JarvisWarning, CircleShape)
                            .clickable { voiceEngine.stopSpeaking() }
                            .testTag("mute_tts_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Stop Speech",
                            tint = JarvisWarning,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Voice Command Chips
            Text(
                text = "SUGGESTED VOICE COMMANDS (EN / HI / HINGLISH):",
                color = JarvisTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            val commandChips = listOf(
                "Search YouTube for AI news",
                "Play relaxing music on YouTube",
                "Turn on flashlight",
                "Torch band karo",
                "What is my battery percentage?",
                "Call Dad",
                "WhatsApp Dad that I am coming",
                "Set timer for 10 minutes",
                "Open Chrome",
                "Explain artificial intelligence"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                commandChips.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .background(JarvisSurface, RoundedCornerShape(16.dp))
                            .border(1.dp, JarvisBorder, RoundedCornerShape(16.dp))
                            .clickable { viewModel.processUserCommand(chip, wasVoice = false) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = chip,
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text input fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            "Type command or ask question...",
                            color = JarvisTextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedContainerColor = JarvisDeepNavy,
                        unfocusedContainerColor = JarvisDeepNavy
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendTextMessage(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(JarvisCyan, RoundedCornerShape(12.dp))
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = Color(0xFF001E2E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Action Confirmation Dialog
        pendingConfirmation?.let { conf ->
            ConfirmationDialog(
                confirmation = conf,
                onDismiss = { viewModel.dismissConfirmation() }
            )
        }
    }
}
