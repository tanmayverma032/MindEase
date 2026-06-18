package com.mindease.network

import android.util.Log
import com.mindease.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiHelper {

    private const val TAG = "GEMINI"

    suspend fun generateTips(
        stressLevel: String,
        sleepHours: Double,
        steps: Int,
        restingHR: Int,
        postExerciseHR: Int,
        blinkRate: Int,
        age: Int,
        cholesterol: Double,
        bodyTemp: Double,
        chestPain: Int = 0,
        ecgResult: Int = 0
    ): List<String> {

        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank()) {
                    Log.e(TAG, "Gemini API key not configured")
                    return@withContext defaultTips(stressLevel, sleepHours, steps, restingHR, chestPain, cholesterol, blinkRate)
                }

                val prompt = buildPrompt(
                    stressLevel, sleepHours, steps, restingHR,
                    postExerciseHR, blinkRate, age, cholesterol, bodyTemp,
                    chestPain, ecgResult
                )

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                )

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                        put("maxOutputTokens", 400)
                    })
                }

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                if (responseCode != 200) {
                    val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e(TAG, "API error: $errorStream")
                    return@withContext defaultTips(stressLevel, sleepHours, steps, restingHR, chestPain, cholesterol, blinkRate)
                }

                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)

                val text = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val tips = parseTips(text)

                Log.d(TAG, "Generated ${tips.size} tips")
                tips.ifEmpty { defaultTips(stressLevel, sleepHours, steps, restingHR, chestPain, cholesterol, blinkRate) }

            } catch (e: Exception) {
                Log.e(TAG, "Gemini error: ${e.message}")
                defaultTips(stressLevel, sleepHours, steps, restingHR, chestPain, cholesterol, blinkRate)
            }
        }
    }

    private fun buildPrompt(
        stressLevel: String, sleepHours: Double, steps: Int,
        restingHR: Int, postExerciseHR: Int, blinkRate: Int,
        age: Int, cholesterol: Double, bodyTemp: Double,
        chestPain: Int, ecgResult: Int
    ): String {

        val chestPainLabel = when (chestPain) { 2 -> "Yes (reported)"; 1 -> "Mild"; else -> "No" }
        val ecgLabel = when (ecgResult) { 2 -> "Sinus Tachycardia"; 1 -> "Mild Tachycardia"; else -> "Normal" }

        // Build a list of identified problems
        val problems = mutableListOf<String>()
        if (sleepHours < 6) problems.add("CRITICAL: Very poor sleep ($sleepHours hours)")
        else if (sleepHours < 7) problems.add("Low sleep ($sleepHours hours)")
        if (steps < 3000) problems.add("CRITICAL: Very low physical activity ($steps steps)")
        else if (steps < 5000) problems.add("Low activity ($steps steps)")
        if (restingHR > 100) problems.add("Elevated resting heart rate ($restingHR BPM)")
        if (chestPain > 0) problems.add("Chest pain reported ($chestPainLabel)")
        if (cholesterol > 200) problems.add("Borderline high cholesterol ($cholesterol)")
        if (blinkRate > 20) problems.add("High blink rate ($blinkRate/min - eye strain)")
        if (ecgResult > 0) problems.add("Abnormal ECG ($ecgLabel)")
        if (bodyTemp > 37.5) problems.add("Slightly elevated temperature (${bodyTemp}°C)")

        val problemsSummary = if (problems.isNotEmpty()) {
            "\n\nIDENTIFIED PROBLEMS (MUST address these):\n${problems.joinToString("\n") { "⚠️ $it" }}"
        } else ""

        return """You are a caring health wellness advisor. A user just completed a stress assessment. Based on their data, give specific, actionable tips to help them.

User's assessment results:
- Stress Level: $stressLevel
- Sleep Duration: $sleepHours hours
- Daily Steps: $steps
- Resting Heart Rate: $restingHR BPM
- Post-Exercise Heart Rate: $postExerciseHR BPM
- Eye Blink Rate: $blinkRate per minute
- Chest Pain: $chestPainLabel
- ECG Result: $ecgLabel
- Age: $age
- Cholesterol: $cholesterol mg/dL
- Body Temperature: $bodyTemp°C$problemsSummary

STRICT RULES:
- Give MINIMUM 2 and MAXIMUM 4 tips
- Each tip MUST directly address a specific problem from the user's data
- If chest pain is reported, MUST include a tip about seeing a doctor and avoiding heavy exertion
- If sleep is low, MUST include a sleep improvement tip
- If steps are low, MUST include an activity tip
- If heart rate is high, include a relaxation tip
- Each tip should be 1-2 sentences, specific and actionable
- Format: numbered list (1. 2. 3. 4.)
- NO introductions, NO conclusions, ONLY the numbered tips"""
    }

    private fun parseTips(text: String): List<String> {
        val lines = text.trim().lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^\\d+[.)\\-]\\s*"), "")
                    .replace(Regex("^[-•*]\\s*"), "")
                    .replace(Regex("^\\*\\*.*?\\*\\*\\s*"), "") // Remove bold prefixes
                    .trim()
            }
            .filter { it.length > 10 }

        // Return between 2 and 4 tips
        return when {
            lines.size < 2 -> lines
            lines.size > 4 -> lines.take(4)
            else -> lines
        }
    }

    /**
     * Fallback tips when Gemini is not available — built from user's actual data.
     */
    private fun defaultTips(
        stressLevel: String, sleepHours: Double, steps: Int,
        restingHR: Int, chestPain: Int, cholesterol: Double, blinkRate: Int
    ): List<String> {
        val tips = mutableListOf<String>()

        // Chest pain — always first if present
        if (chestPain > 0) {
            tips.add("You reported chest pain — please consult a doctor soon and avoid heavy physical exertion until cleared.")
        }

        // Sleep issues
        if (sleepHours < 6) {
            tips.add("Your sleep of ${sleepHours.toInt()} hours is critically low. Set a fixed bedtime, avoid screens 1 hour before sleep, and aim for 7-8 hours.")
        } else if (sleepHours < 7) {
            tips.add("Try to add 30-60 minutes more sleep by creating a calming bedtime routine and reducing caffeine after 4 PM.")
        }

        // Activity issues
        if (steps < 3000) {
            tips.add("Your step count is very low at $steps. Start with short 10-minute walks after each meal and gradually increase.")
        } else if (steps < 5000) {
            tips.add("Boost your daily steps by taking the stairs, parking farther away, or adding a 20-minute evening walk.")
        }

        // Heart rate
        if (restingHR > 100) {
            tips.add("Your resting heart rate of $restingHR BPM is elevated. Practice deep breathing exercises and reduce caffeine intake.")
        }

        // Cholesterol
        if (cholesterol > 200) {
            tips.add("Your cholesterol is borderline high. Focus on eating more fiber, healthy fats, and reducing processed foods.")
        }

        // Eye strain
        if (blinkRate > 20) {
            tips.add("Your high blink rate suggests eye strain. Follow the 20-20-20 rule: every 20 minutes, look at something 20 feet away for 20 seconds.")
        }

        // Stress level
        if (tips.size < 2) {
            if (stressLevel.contains("High", true)) {
                tips.add("Practice 5-minute deep breathing: inhale 4 seconds, hold 4 seconds, exhale 6 seconds. Do this 3 times a day.")
            } else {
                tips.add("Keep up the good work! Stay consistent with your healthy habits and maintain adequate hydration.")
            }
        }

        // Ensure at least 2 tips
        if (tips.size < 2) {
            tips.add("Take a 10-minute break every hour to stretch, hydrate, and rest your eyes.")
        }

        return tips.take(4)
    }
}
