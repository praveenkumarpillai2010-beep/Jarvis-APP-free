package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.JarvisCoreScreen
import com.example.ui.screens.MessagingScreen
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.screens.SettingsAndPermissionsScreen
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.JarvisTab
import com.example.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure lock-screen and screen turn-on behavior
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            MyApplicationTheme {
                val jarvisViewModel: JarvisViewModel = viewModel()
                JarvisApp(viewModel = jarvisViewModel, activity = this)
            }
        }
    }
}

@Composable
fun JarvisApp(
    viewModel: JarvisViewModel,
    activity: ComponentActivity
) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    // Permission launcher for initial microphone + device features
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        viewModel.onMicPermissionResult(micGranted)
    }

    // Auto-launch listening on startup as requested by the prompt!
    LaunchedEffect(Unit) {
        viewModel.attachActivity(activity)
        viewModel.attachWindow(activity.window)
        val hasMic = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.onMicPermissionResult(true)
        } else {
            // Automatically request permission on startup so user can speak immediately
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_CONTACTS
                )
            )
        }
    }

    // Back button handling: return to HOME tab if on a sub-screen
    BackHandler(enabled = selectedTab != JarvisTab.HOME) {
        viewModel.setTab(JarvisTab.HOME)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        bottomBar = {
            NavigationBar(
                containerColor = JarvisDeepNavy,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .border(width = 1.dp, color = JarvisBorder)
            ) {
                NavigationBarItem(
                    selected = selectedTab == JarvisTab.HOME,
                    onClick = { viewModel.setTab(JarvisTab.HOME) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Core Assistant",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "CORE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_home")
                )

                NavigationBarItem(
                    selected = selectedTab == JarvisTab.CHAT,
                    onClick = { viewModel.setTab(JarvisTab.CHAT) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "AI Chat",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "CHAT",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_chat")
                )

                NavigationBarItem(
                    selected = selectedTab == JarvisTab.CONTROLS,
                    onClick = { viewModel.setTab(JarvisTab.CONTROLS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Hardware,
                            contentDescription = "Phone Control",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "PHONE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_controls")
                )

                NavigationBarItem(
                    selected = selectedTab == JarvisTab.MESSAGING,
                    onClick = { viewModel.setTab(JarvisTab.MESSAGING) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Messaging",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "MSGS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_messaging")
                )

                NavigationBarItem(
                    selected = selectedTab == JarvisTab.NOTIFICATIONS,
                    onClick = { viewModel.setTab(JarvisTab.NOTIFICATIONS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "ALERTS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_notifications")
                )

                NavigationBarItem(
                    selected = selectedTab == JarvisTab.SETTINGS,
                    onClick = { viewModel.setTab(JarvisTab.SETTINGS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "SYS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisCyan,
                        selectedTextColor = JarvisCyan,
                        unselectedIconColor = JarvisTextMuted,
                        unselectedTextColor = JarvisTextMuted,
                        indicatorColor = JarvisSurface
                    ),
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                JarvisTab.HOME -> JarvisCoreScreen(
                    viewModel = viewModel,
                    onRequestMicPermission = {
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.RECORD_AUDIO)
                        )
                    }
                )

                JarvisTab.CHAT -> AiChatScreen(
                    viewModel = viewModel,
                    onRequestMicPermission = {
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.RECORD_AUDIO)
                        )
                    }
                )

                JarvisTab.CONTROLS -> PhoneControlScreen(
                    viewModel = viewModel
                )

                JarvisTab.MESSAGING -> MessagingScreen(
                    viewModel = viewModel,
                    onRequestContactsPermission = {
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS)
                        )
                    }
                )

                JarvisTab.NOTIFICATIONS -> NotificationCenterScreen(
                    viewModel = viewModel
                )

                JarvisTab.SETTINGS -> SettingsAndPermissionsScreen(
                    viewModel = viewModel,
                    onRequestPermissions = {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.SEND_SMS
                            )
                        )
                    }
                )
            }
        }
    }
}
