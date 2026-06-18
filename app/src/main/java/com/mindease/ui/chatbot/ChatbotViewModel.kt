package com.mindease.ui.chatbot

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.BuildConfig
import com.mindease.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ChatbotViewModel(private val repository: Repository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SYSTEM_PROMPT = """You are MindEase Assistant, a friendly and caring medical and mental health companion. Follow these rules strictly:

1. ONLY answer questions related to: stress, anxiety, mental health, depression, sleep, exercise, heart health, breathing exercises, meditation, nutrition for health, general medical/wellness topics, fitness, heart rate, blood pressure, body temperature, BMI, vitamins, hydration, skin health, eye health, medical reports.

2. If the user asks anything NOT related to health or wellness (like coding, math, politics, entertainment, sports, recipes unrelated to health, etc.), reply ONLY with: "I'm here to help with your health and wellness! 😊 Feel free to ask me anything about stress, mental health, sleep, exercise, or general wellness."

3. Keep answers SHORT — maximum 3-4 sentences unless asked for detail.

4. Be warm, empathetic, and supportive. Use emojis sparingly (1-2 per message max).

5. If the user seems stressed or anxious, acknowledge their feelings first.

6. Never diagnose medical conditions. If serious, suggest consulting a doctor.

7. IMPORTANT: Reply in the SAME LANGUAGE the user writes in. Auto-detect and match language.

8. When analyzing images: Only analyze health/medical-related images (skin conditions, eye redness, medical reports, fitness, wellness). For non-medical images, politely decline.

User's message: """

        private val HEALTH_KEYWORDS = listOf(
            "stress", "anxious", "anxiety", "depress", "sad", "sleep", "insomnia",
            "heart", "blood pressure", "headache", "pain", "tired", "fatigue",
            "meditat", "breath", "exercise", "workout", "yoga", "calm",
            "mental", "health", "wellness", "relax", "panic", "worry",
            "diet", "nutrition", "weight", "bmi", "cholesterol",
            "doctor", "medicine", "therapy", "counsel", "feel", "mood",
            "anger", "angry", "cry", "emotion", "burnout", "overwhelm",
            "happy", "better", "help", "support", "cope", "heal",
            "vitamin", "supplement", "hydrat", "water",
            "muscle", "body", "temperature", "fever",
            "eye", "blink", "pulse", "ecg", "symptom", "skin",
            "fitness", "step", "walk", "run", "cardio", "image", "photo",
            "rash", "redness", "swelling", "report", "scan", "analyze",
            "tanaav", "neend", "dard", "thakan", "dil", "sehat",
            "vyayam", "khana", "paani", "dawai", "bukhar"
        )

        private const val OFF_TOPIC_REPLY = "I'm here to help with your health and wellness! 😊 Feel free to ask me anything about stress, mental health, sleep, exercise, or general wellness."

        private const val IMAGE_OFF_TOPIC = "I can only analyze health and medical-related images 🏥 Please share images related to skin concerns, eye health, medical reports, or fitness for analysis."
    }

    fun toggleVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = !_uiState.value.voiceMode)
    }

    fun enableVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = true)
    }

    fun sendMessage(message: String, imageBitmap: Bitmap? = null) {
        if (message.isBlank() && imageBitmap == null) return

        val userMessage = ChatMessageUI(message.trim(), isUser = true, imageBitmap = imageBitmap)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )

        // If image attached, use Gemini Vision API directly
        if (imageBitmap != null) {
            sendImageToGemini(message.trim(), imageBitmap)
            return
        }

        val lowerMessage = message.lowercase()
        if (!isHealthTopic(lowerMessage) && lowerMessage.length > 10) {
            addBotReply(OFF_TOPIC_REPLY)
            return
        }

        // Text-only: use backend chat API
        viewModelScope.launch {
            val wrappedMessage = SYSTEM_PROMPT + message.trim()
            val result = repository.chat(wrappedMessage)

            if (result.isSuccess) {
                var reply = result.getOrNull()?.reply ?: "I'm sorry, I couldn't process that. Could you try rephrasing? 😊"
                if (reply.length > 800) reply = reply.take(800) + "..."
                addBotReply(reply)
            } else {
                Log.e("CHATBOT", "API error: ${result.exceptionOrNull()?.message}")
                addBotReply("I'm having trouble connecting right now. Please try again in a moment. 🙏")
            }
        }
    }

    private fun sendImageToGemini(prompt: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    // Compress bitmap
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val imageBase64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                    val fullPrompt = SYSTEM_PROMPT + (prompt.ifBlank { "Analyze this image for health-related concerns." })

                    // Build Gemini API request with vision
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", fullPrompt))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", imageBase64)
                        }))
                    }

                    val body = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    }

                    val apiKey = BuildConfig.GEMINI_API_KEY
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        if (text.length > 800) text.take(800) + "..." else text
                    } else {
                        Log.e("GEMINI", "Error: $responseBody")
                        "I couldn't analyze the image right now. Please try again. 🙏"
                    }
                }
                addBotReply(reply)
            } catch (e: Exception) {
                Log.e("GEMINI", "Exception: ${e.message}")
                addBotReply("Something went wrong while analyzing the image. Please try again. 🙏")
            }
        }
    }

    private fun addBotReply(text: String) {
        val botMessage = ChatMessageUI(text, isUser = false)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + botMessage,
            isLoading = false
        )
    }

    private fun isHealthTopic(message: String): Boolean {
        if (message.length <= 10) return true
        return HEALTH_KEYWORDS.any { keyword -> message.contains(keyword) }
    }
}

data class ChatState(
    val messages: List<ChatMessageUI> = listOf(
        ChatMessageUI("Hey there! 😊 I'm your MindEase wellness companion. Ask me anything about stress, mental health, sleep, or wellness! You can also share health-related images for analysis.", isUser = false)
    ),
    val isLoading: Boolean = false,
    val voiceMode: Boolean = false
)

data class ChatMessageUI(
    val text: String,
    val isUser: Boolean,
    val imageBitmap: Bitmap? = null
)

class ChatbotViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatbotViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatbotViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
