package com.example.ai

sealed class ParsedAction {
    data object FlashlightOn : ParsedAction()
    data object FlashlightOff : ParsedAction()
    data object BatteryStatus : ParsedAction()
    data class OpenApp(val appName: String) : ParsedAction()
    data class SearchYouTube(val query: String, val autoPlay: Boolean = false) : ParsedAction()
    data class CreateAlarm(val hour: Int, val minute: Int, val label: String) : ParsedAction()
    data class CreateTimer(val seconds: Int, val label: String) : ParsedAction()
    data class OpenSetting(val settingName: String) : ParsedAction()
    data class StartNavigation(val destination: String) : ParsedAction()
    data class CallContact(val query: String) : ParsedAction()
    data class FindContact(val query: String) : ParsedAction()
    data class DraftSms(val recipient: String, val message: String) : ParsedAction()
    data class DraftWhatsApp(val recipient: String, val message: String) : ParsedAction()
    data object ReadNotifications : ParsedAction()
    data class SearchWeb(val query: String) : ParsedAction()
    data class AdjustVolume(val direction: Int) : ParsedAction()
    data object TakeScreenshot : ParsedAction()
    data object CancelAction : ParsedAction()
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

        // Extract clean text keeping original capitalization for queries / names
        val originalNoWake = trimmed
            .replace("^(?i)(hey jarvis|ok jarvis|jarvis|are jarvis|suno jarvis)[,\\s]*".toRegex(), "")
            .trim()

        // Cancellation command
        if (text == "cancel the message" || text == "cancel message" || text == "cancel" || text == "cancel action" || text == "cancel karo") {
            return ParsedAction.CancelAction
        }

        // Screenshot command
        if (text.contains("screenshot") || text.contains("screen shot") ||
            text.contains("screen capture") || text.contains("capture screen") ||
            text.contains("capture the screen") || text.contains("screen grab")
        ) {
            return ParsedAction.TakeScreenshot
        }

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

        // 7. YOUTUBE SEARCH & PLAYBACK (High Priority)
        // Check for specific queries like "search for Doraemon movies on YouTube"
        val ytSearchForOnMatch = Regex("(?i)search for (.+?) on youtube").find(originalNoWake)
        if (ytSearchForOnMatch != null) {
            val q = ytSearchForOnMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedAction.SearchYouTube(q, autoPlay = false)
            }
        }

        // "Search YouTube for [query]"
        val ytSearchForMatch = Regex("(?i)search youtube for (.+)").find(originalNoWake)
        if (ytSearchForMatch != null) {
            val q = ytSearchForMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedAction.SearchYouTube(q, autoPlay = false)
            }
        }

        // "Play [query] on YouTube" or "Play a video about [query]"
        val ytPlayOnMatch = Regex("(?i)play (?:a video about |a song by |some |a )?(.+?) on youtube").find(originalNoWake)
        if (ytPlayOnMatch != null) {
            val q = ytPlayOnMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedAction.SearchYouTube(q, autoPlay = true)
            }
        }

        // Hindi YouTube queries: "youtube par search karo [query]" or "youtube par [query] chalao"
        val ytHindiSearchMatch = Regex("(?i)youtube (?:par )?(?:search karo|khojo)\\s+(.+)").find(originalNoWake)
        if (ytHindiSearchMatch != null) {
            val q = ytHindiSearchMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedAction.SearchYouTube(q, autoPlay = false)
            }
        }

        val ytHindiPlayMatch = Regex("(?i)youtube par (.+?) (?:chalao|lagao|play karo)").find(originalNoWake)
        if (ytHindiPlayMatch != null) {
            val q = ytHindiPlayMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedAction.SearchYouTube(q, autoPlay = true)
            }
        }

        // Generic "Play [query]" e.g. "Play relaxing music", "Play latest song by Arijit Singh"
        if (text.startsWith("play ") && !text.contains("store") && !text.contains("game") && !text.contains("timer") && !text.contains("alarm")) {
            val q = originalNoWake.replace("(?i)^play\\s+(?:a video about |a song by |some |a )?".toRegex(), "").trim()
            if (q.isNotEmpty() && q.length < 50 && q.lowercase() != "youtube") {
                return ParsedAction.SearchYouTube(q, autoPlay = true)
            }
        }

        // 8. Explicit App Opening (e.g. "open youtube", "whatsapp kholo")
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

        // 9. Timers
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

        // 10. Alarms
        val alarmMatch = Regex("(?:set (?:an )?alarm for|alarm lagao|wake me up at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = alarmMatch.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return ParsedAction.CreateAlarm(hour, min, "JARVIS Alarm")
        }

        // 11. Navigation
        val navMatch = Regex("(?i)(?:navigate to|take me to|directions to|rasta dikhao)\\s+(.+)").find(originalNoWake)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim()
            if (dest.isNotEmpty()) {
                return ParsedAction.StartNavigation(dest)
            }
        }

        // 12. Find in Contacts ("Find Pravin in my contacts")
        val findContactMatch = Regex("(?i)(?:find|search for|dhundo)\\s+([a-zA-Z0-9\\s]+?)\\s+(?:in my contacts|in contacts|contact)").find(originalNoWake)
        if (findContactMatch != null) {
            val contactName = findContactMatch.groupValues[1].trim()
            if (contactName.isNotEmpty()) {
                return ParsedAction.FindContact(contactName)
            }
        }

        // 13. Direct Call to number: "Call this number: 9876543210"
        val callNumberMatch = Regex("(?i)(?:call this number:?|call number:?|dial number:?|dial)\\s+([0-9+\\-\\s]+)").find(originalNoWake)
        if (callNumberMatch != null) {
            val num = callNumberMatch.groupValues[1].trim()
            if (num.isNotEmpty()) {
                return ParsedAction.CallContact(num)
            }
        }

        // 14. Phone Calls by name: "Call Dad", "Call Rahul", "Call my mother"
        val callMatch = Regex("(?i)(?:call|phone lagao|call karo|make a call to)\\s+(?:to\\s+|my\\s+)?([a-zA-Z0-9\\s]+)").find(originalNoWake)
        if (callMatch != null && !text.contains("whatsapp") && !text.contains("later")) {
            val query = callMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            if (query.isNotEmpty()) {
                return ParsedAction.CallContact(query)
            }
        }

        // 15. WhatsApp Chat Opening ("Open Mom's WhatsApp chat" or "Open Rahul's WhatsApp chat")
        val waChatMatch = Regex("(?i)open\\s+([a-zA-Z0-9\\s]+?)(?:'s)?\\s+whatsapp(?:\\s+chat)?").find(originalNoWake)
        if (waChatMatch != null) {
            val recipient = waChatMatch.groupValues[1].trim()
            if (recipient.isNotEmpty()) {
                return ParsedAction.DraftWhatsApp(recipient, "")
            }
        }

        // 16. WhatsApp Messaging: "Send WhatsApp to Dad saying I am coming home" / "WhatsApp Rahul that I am coming"
        val waMatch = Regex("(?i)(?:send whatsapp to|whatsapp to|send whatsapp message to|whatsapp)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:saying|that|message|ki)\\s+(.+))?$").find(originalNoWake)
        if (waMatch != null) {
            val recipient = waMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            val msg = waMatch.groupValues.getOrNull(2)?.trim() ?: ""
            if (recipient.isNotEmpty() && recipient.lowercase() !in listOf("chat", "kholo", "open", "chalao")) {
                return ParsedAction.DraftWhatsApp(recipient, msg)
            }
        }

        // 17. SMS Messaging: "Send an SMS to Dad saying I will be home soon"
        val smsMatch = Regex("(?i)(?:send (?:an )?sms to|send message to|message|sms)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+(?:saying|that|ki)\\s+(.+))?$").find(originalNoWake)
        if (smsMatch != null) {
            val recipient = smsMatch.groupValues[1].replace("(?i)(?:\\s+ko|\\s+par)$".toRegex(), "").trim()
            val msg = smsMatch.groupValues.getOrNull(2)?.trim() ?: ""
            if (recipient.isNotEmpty()) {
                return ParsedAction.DraftSms(recipient, msg)
            }
        }

        // 18. General App Opening fallback
        val generalAppMatch = Regex("(?:open|launch|kholo)\\s+([a-zA-Z0-9\\s]+)").find(text)
        if (generalAppMatch != null) {
            val appCandidate = generalAppMatch.groupValues[1].trim()
            if (appCandidate.isNotEmpty() && appCandidate.length < 25) {
                return ParsedAction.OpenApp(appCandidate)
            }
        }

        // 19. Web search
        val searchMatch = Regex("(?i)(?:search for|google|search web for)\\s+(.+)").find(originalNoWake)
        if (searchMatch != null) {
            val query = searchMatch.groupValues[1].trim()
            if (query.isNotEmpty()) {
                return ParsedAction.SearchWeb(query)
            }
        }

        // 20. Default: Pass to Gemini AI brain for conversational question answering
        return ParsedAction.GeneralChat(trimmed)
    }
}
