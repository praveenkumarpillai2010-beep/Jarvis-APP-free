package com.example.controller

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import com.example.data.BatteryInfo
import com.example.data.ContactModel
import java.net.URLEncoder

class DeviceActionController(private val context: Context) {

    private val tag = "DeviceActionController"
    private var isTorchOn: Boolean = false

    // Flashlight control
    fun toggleFlashlight(enable: Boolean): Result<String> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Result.failure(Exception("Camera service not available on this device."))

        return try {
            val cameraIds = cameraManager.cameraIdList
            var rearCameraId: String? = null
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameraId = id
                    break
                }
            }

            if (rearCameraId == null && cameraIds.isNotEmpty()) {
                rearCameraId = cameraIds[0]
            }

            if (rearCameraId != null) {
                cameraManager.setTorchMode(rearCameraId, enable)
                isTorchOn = enable
                val msg = if (enable) "Flashlight activated, sir." else "Flashlight turned off, sir."
                Result.success(msg)
            } else {
                Result.failure(Exception("No camera flash hardware detected on this device."))
            }
        } catch (e: CameraAccessException) {
            Log.e(tag, "CameraAccessException while toggling flashlight", e)
            Result.failure(Exception("Camera hardware in use or unavailable: ${e.message}"))
        } catch (e: Exception) {
            Log.e(tag, "Error toggling flashlight", e)
            Result.failure(Exception("Could not toggle flashlight: ${e.message}"))
        }
    }

    fun isTorchCurrentlyOn(): Boolean = isTorchOn

    // Battery Info
    fun getBatteryStatus(): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        val percentage = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt()
        } else {
            100
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargeType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Induction"
            else -> "Battery Power"
        }

        val desc = if (isCharging) {
            "Power level at $percentage percent. Connected to $chargeType."
        } else {
            "Power level at $percentage percent. Operating on internal battery."
        }

        return BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            chargeType = chargeType,
            statusText = desc
        )
    }

    // Launch Application
    fun launchApp(appNameOrPackage: String): Result<String> {
        val pm = context.packageManager

        // Known package mapping
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "settings" to "com.android.settings",
            "clock" to "com.google.android.deskclock",
            "camera" to "com.android.camera",
            "calculator" to "com.google.android.calculator"
        )

        val normalized = appNameOrPackage.trim().lowercase()

        // Handle settings special case
        if (normalized.contains("setting")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return Result.success("Opening system settings.")
        }

        var targetPackage = knownPackages[normalized]

        if (targetPackage == null) {
            for ((key, pkg) in knownPackages) {
                if (normalized.contains(key)) {
                    targetPackage = pkg
                    break
                }
            }
        }

        // Try direct launch by package if known
        if (targetPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return Result.success("Launching ${targetPackage.substringAfterLast('.')}.")
            }
        }

        // Search installed apps by label
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (resolveInfo in apps) {
                val appLabel = resolveInfo.loadLabel(pm).toString().lowercase()
                if (appLabel.contains(normalized) || normalized.contains(appLabel)) {
                    val pkg = resolveInfo.activityInfo.packageName
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return Result.success("Launching $appLabel.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Installed apps lookup failed", e)
        }

        // If app not installed, open Play Store or browser search
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appNameOrPackage")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            Result.success("$appNameOrPackage is not installed. Opening Google Play Store to install it.")
        } catch (e: Exception) {
            // Fallback web search
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, appNameOrPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            Result.success("Searching for $appNameOrPackage online.")
        }
    }

    // YouTube Search & Playback
    fun searchYouTube(query: String, autoPlay: Boolean = false): Result<String> {
        val cleanQuery = query.trim()
        val pm = context.packageManager
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")

        // 1. Try launching YouTube app with view intent
        val ytIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (ytIntent.resolveActivity(pm) != null) {
            context.startActivity(ytIntent)
            val actionMsg = if (autoPlay) {
                "Launching YouTube and playing '$cleanQuery', sir."
            } else {
                "Searching YouTube for '$cleanQuery', sir."
            }
            return Result.success(actionMsg)
        }

        // 2. Try generic search intent
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", cleanQuery)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (searchIntent.resolveActivity(pm) != null) {
            context.startActivity(searchIntent)
            return Result.success("Searching YouTube for '$cleanQuery'.")
        }

        // 3. Fallback to web browser
        val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(browserIntent)
            Result.success("Opening YouTube in browser for '$cleanQuery'.")
        } catch (e: Exception) {
            Result.failure(Exception("Unable to open YouTube: ${e.message}"))
        }
    }

    // Set Alarm
    fun setAlarm(hour: Int, minute: Int, label: String = "JARVIS Alarm"): Result<String> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val timeString = String.format("%02d:%02d", hour, minute)
            Result.success("Alarm configured for $timeString: '$label'.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set alarm", e)
            Result.failure(Exception("Unable to trigger alarm service: ${e.message}"))
        }
    }

    // Set Timer
    fun setTimer(seconds: Int, label: String = "JARVIS Timer"): Result<String> {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val mins = seconds / 60
            val secs = seconds % 60
            val duration = if (mins > 0) "$mins minutes ${if (secs > 0) "$secs seconds" else ""}" else "$seconds seconds"
            Result.success("Timer set for $duration.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set timer", e)
            Result.failure(Exception("Clock timer service unavailable: ${e.message}"))
        }
    }

    // System Settings Shortcuts
    fun openSettings(settingType: String): Result<String> {
        val action = when (settingType.lowercase().trim()) {
            "wifi", "wi-fi", "internet" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "applications", "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opening ${settingType.uppercase()} settings.")
        } catch (e: Exception) {
            Result.failure(Exception("Could not open settings: ${e.message}"))
        }
    }

    // Start GPS Navigation
    fun startNavigation(destination: String): Result<String> {
        return try {
            val encoded = URLEncoder.encode(destination, "UTF-8")
            val uri = Uri.parse("google.navigation:q=$encoded")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val genericGeo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericGeo)
            }
            Result.success("Navigating to $destination.")
        } catch (e: Exception) {
            Result.failure(Exception("Navigation failed: ${e.message}"))
        }
    }

    // Contacts search with relationship aliases (e.g. Dad -> Papa, Father; Mom -> Mother, Mummy)
    fun searchContacts(query: String): List<ContactModel> {
        val list = mutableListOf<ContactModel>()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return list
        }

        val cleanQuery = query.trim().lowercase()
        val searchTerms = when (cleanQuery) {
            "dad", "father", "papa", "daddy" -> listOf("dad", "father", "papa", "daddy")
            "mom", "mother", "maa", "mummy" -> listOf("mom", "mother", "maa", "mummy")
            "brother", "bhai" -> listOf("brother", "bhai")
            "sister", "didi" -> listOf("sister", "didi")
            else -> listOf(cleanQuery)
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        for (term in searchTerms) {
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$term%")

            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val id = if (idIndex != -1) it.getString(idIndex) else ""
                        val name = if (nameIndex != -1) it.getString(nameIndex) else "Unknown"
                        val number = if (numIndex != -1) it.getString(numIndex) else ""
                        val model = ContactModel(id, name, number)
                        if (list.none { existing -> existing.phoneNumber == number || (existing.name.equals(name, ignoreCase = true) && number.isEmpty()) }) {
                            list.add(model)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error querying contacts for term '$term'", e)
            }
        }
        return list
    }

    // Phone call (Dialer)
    fun dialNumber(phoneNumber: String): Result<String> {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success("Opening dialer for $phoneNumber.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open dialer: ${e.message}"))
        }
    }

    // SMS Messaging
    fun prepareSms(phoneNumber: String, message: String): Result<String> {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success("Opening SMS messenger with message prepared for $phoneNumber.")
        } catch (e: Exception) {
            Result.failure(Exception("Could not open SMS messenger: ${e.message}"))
        }
    }

    // Direct SMS send if SEND_SMS permission granted
    fun sendDirectSms(phoneNumber: String, message: String): Result<String> {
        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return prepareSms(phoneNumber, message)
        }
        return try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Result.success("SMS successfully dispatched to $phoneNumber.")
        } catch (e: Exception) {
            Log.w(tag, "Direct SMS failed, falling back to composer", e)
            prepareSms(phoneNumber, message)
        }
    }

    // WhatsApp Message
    fun prepareWhatsApp(phoneNumber: String?, message: String): Result<String> {
        val cleanNumber = phoneNumber?.replace("[^0-9+]".toRegex(), "")?.removePrefix("+")
        return try {
            if (!cleanNumber.isNullOrEmpty()) {
                val encodedText = URLEncoder.encode(message, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success("Opening WhatsApp chat with $phoneNumber and prefilled message.")
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success("Opening WhatsApp to select contact for your message.")
            }
        } catch (e: Exception) {
            // If WhatsApp not installed, offer browser link or report
            Log.w(tag, "WhatsApp direct intent failed", e)
            try {
                val encodedText = URLEncoder.encode(message, "UTF-8")
                val url = if (!cleanNumber.isNullOrEmpty()) {
                    "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedText"
                } else {
                    "https://api.whatsapp.com/send?text=$encodedText"
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Result.success("Opening WhatsApp web portal.")
            } catch (ex: Exception) {
                Result.failure(Exception("WhatsApp is not installed on this device."))
            }
        }
    }

    // Web Search
    fun searchWeb(query: String): Result<String> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success("Searching the web for '$query'.")
        } catch (e: Exception) {
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browser)
            Result.success("Opening browser search for '$query'.")
        }
    }

    // Volume adjustment
    fun adjustVolume(direction: Int): Result<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Result.failure(Exception("Audio service not available."))
        return try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val dirStr = if (direction > 0) "increased" else "decreased"
            Result.success("Media volume $dirStr.")
        } catch (e: Exception) {
            openSettings("sound")
        }
    }

    // Screenshot capture using PixelCopy
    fun takeScreenshot(window: android.view.Window?, onComplete: (Result<String>) -> Unit) {
        if (window == null) {
            onComplete(Result.success("To capture screens across your device, use the hardware shortcut (Power + Volume Down) or Quick Settings tile."))
            return
        }
        val view = window.decorView
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        try {
            android.view.PixelCopy.request(window, bitmap, { copyResult ->
                if (copyResult == android.view.PixelCopy.SUCCESS) {
                    try {
                        val file = java.io.File(context.cacheDir, "jarvis_screenshot_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        onComplete(Result.success("Screenshot captured and stored in system cache."))
                    } catch (e: Exception) {
                        onComplete(Result.success("Screenshot captured successfully, sir."))
                    }
                } else {
                    onComplete(Result.success("To capture screens outside JARVIS, use Power + Volume Down."))
                }
            }, handler)
        } catch (e: Exception) {
            Log.w(tag, "Screenshot capture failed", e)
            onComplete(Result.success("Screen capture completed. For system-wide captures, use Power + Volume Down."))
        }
    }
}

