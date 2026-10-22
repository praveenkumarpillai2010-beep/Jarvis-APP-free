package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.viewmodel.JarvisViewModel

@Composable
fun PhoneControlScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val batteryInfo by viewModel.batteryState.collectAsState()
    var destinationInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Section Header
        Text(
            text = "HARDWARE & SYSTEM CONTROLS",
            color = JarvisCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Direct Android APIs, Intents & Sensor Interface",
            color = JarvisTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 1. Flashlight & Battery Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flashlight Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isTorchOn) JarvisCyan else JarvisBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.toggleTorch() }
                    .padding(14.dp)
                    .testTag("flashlight_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) JarvisCyan else JarvisTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Switch(
                            checked = isTorchOn,
                            onCheckedChange = { viewModel.toggleTorch() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisCyan,
                                checkedTrackColor = JarvisElectricBlue.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisSurface
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FLASHLIGHT",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isTorchOn) "ACTIVE // ON" else "STANDBY // OFF",
                        color = if (isTorchOn) JarvisCyan else JarvisTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Battery Status Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                    .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        viewModel.refreshBattery()
                        viewModel.voiceEngine.speak(batteryInfo.statusText)
                    }
                    .padding(14.dp)
                    .testTag("battery_status_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = JarvisSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${batteryInfo.percentage}%",
                            color = JarvisSuccess,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BATTERY LEVEL",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (batteryInfo.isCharging) "CHARGING (${batteryInfo.chargeType})" else "OPERATING ON BATTERY",
                        color = JarvisTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Application Quick Launchers
        Text(
            text = "TARGET APP LAUNCHERS",
            color = JarvisCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLaunchButton("YouTube", "youtube", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open YouTube", wasVoice = false)
            }
            AppLaunchButton("WhatsApp", "whatsapp", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open WhatsApp", wasVoice = false)
            }
            AppLaunchButton("Instagram", "instagram", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open Instagram", wasVoice = false)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLaunchButton("Chrome", "chrome", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open Chrome", wasVoice = false)
            }
            AppLaunchButton("Maps", "maps", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open Maps", wasVoice = false)
            }
            AppLaunchButton("Settings", "settings", Modifier.weight(1f)) {
                viewModel.processUserCommand("Open Settings", wasVoice = false)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. YouTube Media Search & Playback Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFFF0000).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YOUTUBE SEARCH & PLAY",
                            color = Color(0xFFFF4E4E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "DIRECT INTENT",
                        color = JarvisTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                var ytQuery by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ytQuery,
                        onValueChange = { ytQuery = it },
                        placeholder = {
                            Text("Search video, song, creator or movie...", color = JarvisTextMuted, fontSize = 12.sp)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("youtube_search_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF4E4E),
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedContainerColor = JarvisSurface,
                            unfocusedContainerColor = JarvisSurface
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            if (ytQuery.isNotBlank()) {
                                viewModel.deviceController.searchYouTube(ytQuery, autoPlay = true)
                                ytQuery = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0000),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("PLAY", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "AI News" to "latest AI news",
                        "Relaxing Music" to "relaxing music",
                        "Android Tutorials" to "Android Studio tutorials"
                    ).forEach { (label, q) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(JarvisSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, JarvisBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.processUserCommand("Search YouTube for $q", wasVoice = false)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Timers & Alarms Quick Dispatch
        Text(
            text = "ALARM & TIMER DISPATCH",
            color = JarvisCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassBottom,
                        contentDescription = "Timer",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QUICK TIMERS",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "1 Min" to 60,
                        "5 Min" to 300,
                        "10 Min" to 600,
                        "15 Min" to 900
                    ).forEach { (label, secs) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(JarvisSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.processUserCommand("Set a timer for ${secs / 60} minutes", wasVoice = false)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = JarvisCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarm",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QUICK ALARMS",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "6:00 AM" to (6 to 0),
                        "7:00 AM" to (7 to 0),
                        "8:00 AM" to (8 to 0)
                    ).forEach { (label, time) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(JarvisSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.processUserCommand("Set an alarm for $label", wasVoice = false)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = JarvisTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. System Settings & Volume Rocker
        Text(
            text = "SYSTEM SETTINGS SHORTCUTS",
            color = JarvisCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingQuickTile(
                name = "Wi-Fi",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            ) {
                viewModel.deviceController.openSettings("wifi")
            }
            SettingQuickTile(
                name = "Bluetooth",
                icon = Icons.Default.Bluetooth,
                modifier = Modifier.weight(1f)
            ) {
                viewModel.deviceController.openSettings("bluetooth")
            }
            SettingQuickTile(
                name = "Display",
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            ) {
                viewModel.deviceController.openSettings("display")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Volume control card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MEDIA AUDIO LEVEL",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Adjust device stream volume",
                        color = JarvisTextMuted,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(JarvisSurface)
                            .border(1.dp, JarvisBorder, CircleShape)
                            .clickable { viewModel.deviceController.adjustVolume(-1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Volume Down",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(JarvisSurface)
                            .border(1.dp, JarvisBorder, CircleShape)
                            .clickable { viewModel.deviceController.adjustVolume(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume Up",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. GPS Navigation Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Navigation",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DIRECT NAVIGATION",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = destinationInput,
                        onValueChange = { destinationInput = it },
                        placeholder = {
                            Text("Enter destination or address...", color = JarvisTextMuted, fontSize = 12.sp)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedContainerColor = JarvisSurface,
                            unfocusedContainerColor = JarvisSurface
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (destinationInput.isNotBlank()) {
                                viewModel.deviceController.startNavigation(destinationInput)
                                destinationInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color(0xFF001E2E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("GO", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AppLaunchButton(
    name: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(JarvisDeepNavy, RoundedCornerShape(10.dp))
            .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = JarvisTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingQuickTile(
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(JarvisDeepNavy, RoundedCornerShape(10.dp))
            .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = JarvisCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                color = JarvisTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
