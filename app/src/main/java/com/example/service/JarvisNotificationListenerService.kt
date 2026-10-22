package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JarvisNotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _notifications = MutableStateFlow<List<JarvisNotificationItem>>(emptyList())
        val notifications: StateFlow<List<JarvisNotificationItem>> = _notifications.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun clearNotifications() {
            _notifications.value = emptyList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        fetchActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { addNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val key = it.key
            _notifications.value = _notifications.value.filter { item -> item.id != key }
        }
    }

    private fun fetchActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = mutableListOf<JarvisNotificationItem>()
            for (sbn in active) {
                val item = parseNotification(sbn)
                if (item != null) list.add(item)
            }
            _notifications.value = list.take(20)
        } catch (e: Exception) {
            // Ignore if active notifications cannot be read
        }
    }

    private fun addNotification(sbn: StatusBarNotification) {
        val item = parseNotification(sbn) ?: return
        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _notifications.value = current.take(20)
    }

    private fun parseNotification(sbn: StatusBarNotification): JarvisNotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return null

        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        return JarvisNotificationItem(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )
    }
}
