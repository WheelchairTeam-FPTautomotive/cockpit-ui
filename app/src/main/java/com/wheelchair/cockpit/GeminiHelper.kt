package com.wheelchair.cockpit

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiHelper {

    // Initialized from BuildConfig (loaded from .env or local.properties), or user input UI
    private var userApiKey: String = BuildConfig.GEMINI_API_KEY.takeIf { it != "YOUR_GEMINI_API_KEY_HERE" } ?: ""

    private const val SYSTEM_INSTRUCTION = """
You are Wheelchair Copilot, an intelligent, empathetic, and ultra-helpful AI automotive assistant inside an Android Automotive vehicle cockpit.
Guidelines:
1. Provide concise, clear, and safe responses suitable for a driver operating a vehicle.
2. Answer queries in the same language the driver spoke in (Vietnamese if spoken in Vietnamese, English if spoken in English).
3. Keep answers under 3-4 sentences so Text-To-Speech can pronounce it clearly without distracting the driver.
4. If asked vehicle technical questions (tire pressure, service schedule, HVAC, controls), give accurate, reassuring guidance.
"""

    fun setApiKey(apiKey: String) {
        userApiKey = apiKey
    }

    suspend fun queryGemini(userQuery: String, apiKeyOverride: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: userApiKey

        if (apiKey.isBlank()) {
            Log.w("GeminiAI", "No Gemini API key provided. Falling back to local RAG backend.")
            throw IllegalStateException("API_KEY_EMPTY")
        }

        try {
            Log.d("GeminiAI", "Sending query to Gemini AI: '$userQuery'")
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val prompt = "$SYSTEM_INSTRUCTION\n\nDriver Query: $userQuery"
            val response = generativeModel.generateContent(prompt)
            val answerText = response.text?.trim() ?: "Sorry, I could not generate a response."
            Log.d("GeminiAI", "Gemini AI Response: '$answerText'")
            answerText
        } catch (e: Exception) {
            Log.e("GeminiAI", "Gemini API Error: ${e.message}", e)
            throw e
        }
    }
}
