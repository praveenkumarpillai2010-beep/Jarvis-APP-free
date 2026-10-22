package com.example.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {

    private val tag = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelName = "gemini-3.5-flash"
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    suspend fun askJarvis(prompt: String, conversationHistory: List<Pair<String, String>> = emptyList()): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                Exception("Gemini API key is not configured in Secrets. Local command processor is active.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val contentsArray = JSONArray()

            // Include system instruction or conversation context
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
                            "You are articulate, polite, loyal, and efficient. " +
                            "If the user speaks English, answer in English. " +
                            "If the user speaks Hindi or Hinglish, respond in natural Hindi or Hinglish. " +
                            "Always keep spoken answers concise (1 to 3 short sentences) unless an in-depth breakdown is explicitly requested. " +
                            "Address the user politely as 'sir' or 'boss' occasionally when appropriate."
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
                Log.e(tag, "Gemini API error code: ${response.code}, body: $responseBody")
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
            Log.e(tag, "Gemini network exception", e)
            Result.failure(e)
        }
    }
}
