package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jarvis_commands")
data class JarvisCommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val commandText: String,
    val actionCategory: String,
    val status: String, // "SUCCESS", "FAILED", "PENDING_CONFIRMATION", "CANCELLED"
    val resultMessage: String,
    val wasVoiceTriggered: Boolean = true
)

@Entity(tableName = "jarvis_chat")
data class JarvisChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val role: String, // "USER", "JARVIS", "SYSTEM"
    val content: String,
    val actionCategory: String? = null
)

data class ContactModel(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargeType: String,
    val statusText: String
)

data class AppShortcut(
    val name: String,
    val packageName: String,
    val iconDesc: String
)
