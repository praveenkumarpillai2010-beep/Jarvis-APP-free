package com.example.ai

sealed class ParsedAction {
    data object FlashlightOn : ParsedAction()
    data object FlashlightOff : ParsedAction()
    data object BatteryStatus : ParsedAction()
    data class OpenApp(val appName: String) : ParsedAction()
    data class CreateAlarm(val hour: Int, val minute: Int, val label: String) : ParsedAction()
    data class CreateTimer(val seconds: Int, val label: String) : ParsedAction()
    data class OpenSetting(val settingName: String) : ParsedAction()
    data class StartNavigation(val destination: String) : ParsedAction()
    data class CallContact(val query: String) : ParsedAction()
    data class DraftSms(val recipient: String, val message: String) : ParsedAction()
    data class DraftWhatsApp(val recipient: String, val message: String) : ParsedAction()
    data object ReadNotifications : ParsedAction()
    data class SearchWeb(val query: String) : ParsedAction()
    data class AdjustVolume(val direction: Int) : ParsedAction()
    data class GeneralChat(val query: String) : ParsedAction()
}

object CommandRouter {

    fun parse(input: String): ParsedAction {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // Remove wake-word prefixes if user said them
        val text = lower
            .replace("^(hey jarvis|ok jarvis|jarvis|are jarvis|suno jarvis)[,\\s]*".toRegex(), "")
            .trim()

        // Extract clean text keeping original capitalization for recipient names
        val originalNoWake = trimmed
            .replace("^(?i)(hey jarvis|ok jarvis|jarvis|are jarvis|suno jarvis)[,\\s]*".toRegex(), "")
            .trim()

        // 1. Flashlight on
        if (text.contains("flashlight on") || text.contains("torch on") ||
            text.contains("torch jalao") || text.contains("torch chalu") ||
            text.contains("light on") || text.contains("batti jalao") ||
            text.contains("turn on flashlight") || text.contains("turn on torch")
        ) {
            return ParsedAction.FlashlightOn
        }

        // 2. Flashlight off
        if (text.contains("flashlight off") || text.contains("torch off") ||
            text.contains("torch band") || text.contains("light band") ||
            text.contains("batti bujhao") || text.contains("turn off flashlight") ||
            text.contains("turn off torch")
        ) {
            return ParsedAction.FlashlightOff
        }

        // 3. Battery status
        if (text.contains("battery") || text.contains("charge kitna") ||
            text.contains("charging status") || text.contains("battery percentage")
        ) {
            return ParsedAction.BatteryStatus
        }

        // 4. Notifications
        if (text.contains("read notification") || text.contains("my notification") ||
            text.contains("notification padho") || text.contains("check notification")
        ) {
            return ParsedAction.ReadNotifications
        }

        // 5. Volume controls
        if (text.contains("volume up") || text.contains("volume badhao") || text.contains("increase volume")) {
            return ParsedAction.AdjustVolume(direction = 1)
        }
        if (text.contains("volume down") || text.contains("volume kam karo") || text.contains("decrease volume")) {
            return ParsedAction.AdjustVolume(direction = -1)
        }

        // 6. Settings
        if (text.contains("wifi setting") || text.contains("open wifi") || text.contains("wi-fi")) {
            return ParsedAction.OpenSetting("wifi")
        }
        if (text.contains("bluetooth") || text.contains("open bluetooth")) {
            return ParsedAction.OpenSetting("bluetooth")
        }
        if (text.contains("display setting") || text.contains("brightness")) {
            return ParsedAction.OpenSetting("display")
        }
        if (text.contains("sound setting") || text.contains("audio setting")) {
            return ParsedAction.OpenSetting("sound")
        }

        // 7. App Opening (Explicit priority before messaging patterns)
        val appKeywords = listOf(
            "youtube", "whatsapp", "instagram", "chrome", "maps", "settings",
            "camera", "calculator", "clock", "spotify", "gmail", "facebook", "twitter"
        )
        for (app in appKeywords) {
            if (text == "open $app" || text == "$app kholo" || text == "$app open karo" ||
                text == "$app chalao" || text == "launch $app" || text.startsWith("open $app") ||
                text.startsWith("$app kholo")
            ) {
                return ParsedAction.OpenApp(app)
            }
        }

        // 8. Timers
        val timerMatch = Regex("(?:set (?:a )?timer for|timer lagao|timer)\\s+(\\d+)\\s*(minute|min|second|sec|ghante|hour)?").find(text)
        if (timerMatch != null) {
            val num = timerMatch.groupValues[1].toIntOrNull() ?: 5
            val unit = timerMatch.groupValues[2]
            val seconds = when {
                unit.startsWith("sec") -> num
                unit.startsWith("hour") || unit.startsWith("ghante") -> num * 3600
                else -> num * 60
            }
            return ParsedAction.CreateTimer(seconds, "JARVIS Timer")
        }

        // 9. Alarms
        val alarmMatch = Regex("(?:set (?:an )?alarm for|alarm lagao|wake me up at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = alarmMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return ParsedAction.CreateAlarm(hour, min, "JARVIS Alarm")
        }

        // 10. Navigation
        val navMatch = Regex("(?i)(?:navigate to|take me to|directions to|rasta dikhao)\\s+(.+)").find(originalNoWake)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim()
            if (dest.isNotEmpty()) {
                return ParsedAction.StartNavigation(dest)
            }
        }

        // 11. Phone Calls
        val callMatch = Regex("(?i)(?:call|phone lagao|call karo)\\s+(?:to\\s+)?([a-zA-Z0-9\\s]+)").find(originalNoWake)
        if (callMatch != null && !text.contains("whatsapp")) {
            val query = callMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            if (query.isNotEmpty()) {
                return ParsedAction.CallContact(query)
            }
        }

        // 12. WhatsApp Messaging
        val waMatch = Regex("(?i)(?:send whatsapp to|whatsapp to|send whatsapp message to|whatsapp)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:saying|message|ki)\\s+(.+))?$").find(originalNoWake)
        if (waMatch != null) {
            val recipient = waMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            val msg = waMatch.groupValues.getOrNull(2)?.trim() ?: "Hello from JARVIS"
            if (recipient.isNotEmpty() && recipient.lowercase() !in listOf("chat", "kholo", "open", "chalao")) {
                return ParsedAction.DraftWhatsApp(recipient, msg)
            }
        }

        // 13. SMS Messaging
        val smsMatch = Regex("(?i)(?:send sms to|send message to|message|sms)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:saying|that|ki)\\s+(.+))?$").find(originalNoWake)
        if (smsMatch != null) {
            val recipient = smsMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            val msg = smsMatch.groupValues.getOrNull(2)?.trim() ?: "Hello from JARVIS"
            if (recipient.isNotEmpty()) {
                return ParsedAction.DraftSms(recipient, msg)
            }
        }

        // 14. General App Opening fallback
        val generalAppMatch = Regex("(?:open|launch|kholo)\\s+([a-zA-Z0-9\\s]+)").find(text)
        if (generalAppMatch != null) {
            val appCandidate = generalAppMatch.groupValues[1].trim()
            if (appCandidate.isNotEmpty() && appCandidate.length < 25) {
                return ParsedAction.OpenApp(appCandidate)
            }
        }

        // 15. Web search
        val searchMatch = Regex("(?i)(?:search for|google|search web for)\\s+(.+)").find(originalNoWake)
        if (searchMatch != null) {
            val query = searchMatch.groupValues[1].trim()
            if (query.isNotEmpty()) {
                return ParsedAction.SearchWeb(query)
            }
        }

        // 16. Default: Pass to Gemini AI brain for conversational question answering
        return ParsedAction.GeneralChat(trimmed)
    }
}
