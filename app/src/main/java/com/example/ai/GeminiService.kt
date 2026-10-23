package com.example.ai

import android.util.Log
import com.example.data.GeminiConnectionStatus
import com.example.data.JarvisPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiInterpretation(
    val actionType: String, // "ACTION", "CHAT", "CLARIFY"
    val actionCategory: String?, // e.g. "OPEN_APP", "SEARCH_YOUTUBE", "CALL_CONTACT", etc.
    val parameters: Map<String, String>,
    val spokenResponse: String,
    val detailedResponse: String
)

class GeminiService(private val preferencesManager: JarvisPreferencesManager) {

    private val tag = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    /**
     * Test connection to Gemini API with the given or active key and model.
     * Never logs the API key to Logcat or analytics.
     */
    suspend fun testConnection(keyOverride: String? = null, modelOverride: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = (keyOverride ?: preferencesManager.getActiveApiKey()).trim()
        val model = (modelOverride ?: preferencesManager.getSelectedModel()).trim()

        if (apiKey.isEmpty()) {
            val err = "No API Key configured. Please enter and save your Gemini API key."
            preferencesManager.setConnectionStatus(GeminiConnectionStatus.NOT_CONFIGURED, err)
            return@withContext Result.failure(Exception(err))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "Respond with 'ONLINE'")))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 10)
            })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(mediaTypeJson))
                .build()

            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            val respString = response.body?.string() ?: ""

            when (code) {
                200 -> {
                    preferencesManager.setConnectionStatus(GeminiConnectionStatus.CONNECTED, "")
                    Result.success("Connection successful! Model $model is active ($latency ms).")
                }
                400, 401, 403 -> {
                    val errMsg = "Invalid API Key or unauthorized model ($code). Verify your key in Google AI Studio."
                    preferencesManager.setConnectionStatus(GeminiConnectionStatus.INVALID_KEY, errMsg)
                    Result.failure(Exception(errMsg))
                }
                429 -> {
                    val errMsg = "Gemini quota or rate limit exceeded (HTTP 429). Please check your account quota."
                    preferencesManager.setConnectionStatus(GeminiConnectionStatus.QUOTA_EXCEEDED, errMsg)
                    Result.failure(Exception(errMsg))
                }
                else -> {
                    val errMsg = "Gemini service returned HTTP $code. Check server status."
                    preferencesManager.setConnectionStatus(GeminiConnectionStatus.NETWORK_ERROR, errMsg)
                    Result.failure(Exception(errMsg))
                }
            }
        } catch (e: IOException) {
            val errMsg = "Network error: Unable to reach Gemini API. Please check your internet connection."
            preferencesManager.setConnectionStatus(GeminiConnectionStatus.NETWORK_ERROR, errMsg)
            Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            val errMsg = "Connection test error: ${e.message}"
            preferencesManager.setConnectionStatus(GeminiConnectionStatus.NETWORK_ERROR, errMsg)
            Result.failure(Exception(errMsg))
        }
    }

    /**
     * Ask Gemini a general conversational question.
     */
    suspend fun askJarvis(prompt: String, conversationHistory: List<Pair<String, String>> = emptyList()): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getSelectedModel()

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemini API key is not configured in Settings. Local command processor is active.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val contentsArray = JSONArray()

            // Include conversation context
            for ((role, text) in conversationHistory.takeLast(6)) {
                val turnObj = JSONObject().apply {
                    put("role", if (role == "USER") "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }
                contentsArray.put(turnObj)
            }

            // Current prompt
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }
            )

            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put(
                    "text",
                    "You are JARVIS, Tony Stark's futuristic personal AI assistant on Android. " +
                            "You are articulate, loyal, highly intelligent, and concise. " +
                            "If the user speaks English, answer in English. " +
                            "If the user speaks Hindi or Hinglish, answer naturally in Hindi or Hinglish. " +
                            "Keep spoken answers under 2-3 short sentences. Address the user politely as 'sir' occasionally."
                )))
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", systemInstruction)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("maxOutputTokens", 600)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(mediaTypeJson))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini server error (${response.code})"))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            if (text.isNotBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(Exception("Received empty response from Gemini."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uses Gemini to understand conversational, complex or ambiguous commands.
     * Classifies the utterance into an allowlisted Android action or conversational Q&A.
     */
    suspend fun interpretCommandWithGemini(
        utterance: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<GeminiInterpretation> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.getActiveApiKey()
        val model = preferencesManager.getSelectedModel()

        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("No API key available."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val promptInstruction = """
You are the AI Command Classifier for JARVIS on Android.
Analyze the user utterance: "$utterance".

Supported Action Allowlist:
- OPEN_APP (params: target e.g. "youtube", "whatsapp", "instagram", "chrome", "maps", "settings", "camera", "calculator")
- FLASHLIGHT_ON (params: none)
- FLASHLIGHT_OFF (params: none)
- GET_BATTERY_STATUS (params: none)
- CREATE_TIMER (params: seconds)
- CREATE_ALARM (params: hour, minute)
- SEARCH_YOUTUBE (params: query, autoPlay: "true"|"false")
- CALL_CONTACT (params: recipient e.g. "Dad", "Rahul", or phone number)
- DRAFT_WHATSAPP (params: recipient, message)
- DRAFT_SMS (params: recipient, message)
- TAKE_SCREENSHOT (params: none)
- START_NAVIGATION (params: destination)
- OPEN_SYSTEM_SETTINGS (params: setting e.g. "wifi", "bluetooth", "display", "sound")
- GENERAL_ANSWER (if user is asking a general question, study/coding problem, or having a chat)
- CLARIFY (if the action requested is ambiguous e.g. 'call him' without a name)

Respond ONLY with valid JSON in this exact structure:
{
  "type": "ACTION" or "CHAT" or "CLARIFY",
  "action": "<ONE_OF_THE_ALLOWLIST_ACTIONS>",
  "params": {
    "key": "value"
  },
  "spokenResponse": "<Concise 1-2 sentence response to speak aloud>",
  "detailedResponse": "<Full detailed explanation for the screen>"
}
""".trimIndent()

        val contentsArray = JSONArray()
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", promptInstruction)))
        })

        val requestBodyJson = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
                put("maxOutputTokens", 500)
            })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(mediaTypeJson))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini error ${response.code}"))
            }

            val rootJson = JSONObject(responseBody)
            val candidateText = rootJson.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "") ?: ""

            if (candidateText.isBlank()) {
                return@withContext Result.failure(Exception("Empty candidate"))
            }

            val parsedJson = JSONObject(candidateText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            val type = parsedJson.optString("type", "CHAT")
            val action = parsedJson.optString("action", "GENERAL_ANSWER")
            val paramsObj = parsedJson.optJSONObject("params")
            val params = mutableMapOf<String, String>()
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.optString(k, "")
                }
            }
            val spoken = parsedJson.optString("spokenResponse", "")
            val detailed = parsedJson.optString("detailedResponse", spoken)

            Result.success(
                GeminiInterpretation(
                    actionType = type,
                    actionCategory = action,
                    parameters = params,
                    spokenResponse = spoken,
                    detailedResponse = detailed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
