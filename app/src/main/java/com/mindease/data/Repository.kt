package com.mindease.data

import android.util.Log
import com.mindease.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.HttpException

class Repository(private val userPreferences: UserPreferences) {

    private val backendApi = ApiClient.backendApi
    private val backendAuthApi = ApiClient.backendAuthApi
    private val blinkApi = ApiClient.blinkApi
    private val chatbotApi = ApiClient.chatbotApi

    // ---------------- AUTH ---------------- //

    /**
     * Save the name entered during signup locally, so we have it even if backend returns blank.
     */
    suspend fun saveSignupName(name: String) {
        if (name.isNotBlank()) {
            userPreferences.saveUserName(name)
        }
    }

    /**
     * Wake-up ping to ensure Render backend is warm before auth calls.
     */
    private fun wakeUpBackend() {
        try {
            val pingClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val pingRequest = okhttp3.Request.Builder()
                .url(com.mindease.BuildConfig.BACKEND_URL)
                .get()
                .build()
            pingClient.newCall(pingRequest).execute().close()
            Log.d("AUTH_DEBUG", "Backend wake-up ping successful")
        } catch (e: Exception) {
            Log.w("AUTH_DEBUG", "Wake-up ping failed (continuing): ${e.message}")
        }
    }

    suspend fun login(request: AuthRequest): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Wake-up ping to ensure Render backend is warm
                wakeUpBackend()

                val response = backendAuthApi.login(request)

                Log.d("AUTH_DEBUG", "Login response: $response")

                response.token?.let { userPreferences.saveAuthToken(it) }
                response.user_id?.let { userPreferences.saveUserId(it) }

                // Save name: prefer backend response, fallback to email prefix
                if (!response.name.isNullOrBlank()) {
                    userPreferences.saveUserName(response.name)
                } else {
                    // Backend returned empty name — check if we have one saved already
                    val savedName = userPreferences.userNameFlow.first()
                    if (savedName.isBlank() && !response.email.isNullOrBlank()) {
                        // Use email prefix as fallback (e.g., "tanmay" from "tanmay@gmail.com")
                        val emailPrefix = response.email.substringBefore("@")
                            .replaceFirstChar { it.uppercase() }
                        userPreferences.saveUserName(emailPrefix)
                    }
                }

                response.email?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserEmail(it) }
                response.phone?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserPhone(it) }

                Result.success(response)

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("AUTH_ERROR", "Code: ${e.code()} Body: $errorBody")
                val detail = try {
                    org.json.JSONObject(errorBody ?: "{}").optString("detail", errorBody ?: "Login failed")
                } catch (_: Exception) { errorBody ?: "Login failed" }
                Result.failure(Exception(detail))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("AUTH_ERROR", "Timeout: ${e.message}")
                Result.failure(Exception("Server is taking too long. Please try again."))
            } catch (e: Exception) {
                Log.e("AUTH_ERROR", "Exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun signup(request: AuthRequest): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Wake-up ping to ensure Render backend is warm
                wakeUpBackend()

                val response = backendAuthApi.signup(request)

                Log.d("AUTH_DEBUG", "Signup response: $response")

                response.token?.let { userPreferences.saveAuthToken(it) }
                response.user_id?.let { userPreferences.saveUserId(it) }
                response.name?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserName(it) }
                response.email?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserEmail(it) }
                response.phone?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserPhone(it) }

                Result.success(response)

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("AUTH_ERROR", "Code: ${e.code()} Body: $errorBody")
                val detail = try {
                    org.json.JSONObject(errorBody ?: "{}").optString("detail", errorBody ?: "Signup failed")
                } catch (_: Exception) { errorBody ?: "Signup failed" }
                Result.failure(Exception(detail))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("AUTH_ERROR", "Timeout: ${e.message}")
                Result.failure(Exception("Server is taking too long to respond. Please try again in a moment."))
            } catch (e: java.net.ConnectException) {
                Log.e("AUTH_ERROR", "Connection: ${e.message}")
                Result.failure(Exception("Cannot connect to server. Please check your internet connection."))
            } catch (e: Exception) {
                Log.e("AUTH_ERROR", "Exception: ${e.message}")
                Result.failure(Exception("Something went wrong. Please try again."))
            }
        }
    }

    suspend fun verifyEmail(request: VerifyEmailRequest): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = backendAuthApi.verifyEmail(request)

                Log.d("AUTH_DEBUG", "Verify response: $response")

                response.token?.let { userPreferences.saveAuthToken(it) }
                response.user_id?.let { userPreferences.saveUserId(it) }
                response.name?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserName(it) }
                response.email?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserEmail(it) }
                response.phone?.takeIf { it.isNotBlank() }?.let { userPreferences.saveUserPhone(it) }

                Result.success(response)

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("AUTH_ERROR", "Code: ${e.code()} Body: $errorBody")
                val detail = try {
                    org.json.JSONObject(errorBody ?: "{}").optString("detail", errorBody ?: "Verification failed")
                } catch (_: Exception) { errorBody ?: "Verification failed" }
                Result.failure(Exception(detail))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("AUTH_ERROR", "Timeout: ${e.message}")
                Result.failure(Exception("Server is taking too long. Please try again."))
            } catch (e: Exception) {
                Log.e("AUTH_ERROR", "Exception: ${e.message}")
                Result.failure(Exception("Verification failed. Please try again."))
            }
        }
    }

    suspend fun logout() {
        userPreferences.clearSession()
    }

    // ---------------- STRESS ---------------- //

    suspend fun predictStress(
        token: String?,
        input: StressInput
    ): Result<PredictionResponse> {

        return withContext(Dispatchers.IO) {
            try {
                if (token.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("User not authenticated"))
                }

                val response = backendApi.predictStress(token, input)

                // Save scan timestamp locally (backend doesn't return timestamps)
                try {
                    val scanTimestamp = System.currentTimeMillis().toString()
                    val scanKey = "${input.eyeBlinkRate}_${input.restingHeartRate}_${input.sleepDuration}_${input.steps}"
                    saveScanTimestamp(scanKey, scanTimestamp, response.stress_score, response.stress_level)
                    Log.d("SCAN_SAVE", "Saved scan: key=$scanKey, ts=$scanTimestamp, score=${response.stress_score}, level=${response.stress_level}")
                } catch (e: Exception) {
                    Log.e("SCAN_SAVE", "Failed to save scan timestamp: ${e.message}")
                }

                Result.success(response)

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "API error ${e.code()}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Save a scan timestamp locally for matching with backend history.
     */
    private suspend fun saveScanTimestamp(scanKey: String, timestamp: String, score: Int?, level: String?) {
        val scanList = userPreferences.getScanTimestamps()
        scanList.add(0, ScanTimestampEntry(scanKey, timestamp, score, level))
        // Keep max 50 entries
        while (scanList.size > 50) scanList.removeAt(scanList.lastIndex)
        userPreferences.saveScanTimestamps(scanList)
    }

    // ---------------- HISTORY ---------------- //

    suspend fun getHistory(token: String, userId: String): Result<HistoryResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${com.mindease.BuildConfig.BACKEND_URL}history/$userId"
                Log.d("HISTORY", "Fetching: $url")

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", token)
                    .get()
                    .build()

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"

                // Check for non-success HTTP status (e.g., 500 Internal Server Error)
                if (!response.isSuccessful) {
                    Log.e("HISTORY", "Server error ${response.code}: $body")
                    return@withContext Result.success(HistoryResponse(emptyList()))
                }

                // Safely parse JSON — backend might return non-JSON on error
                val json = try {
                    org.json.JSONObject(body)
                } catch (e: org.json.JSONException) {
                    Log.e("HISTORY", "Invalid JSON response: ${body.take(200)}")
                    return@withContext Result.success(HistoryResponse(emptyList()))
                }
                val historyArray = json.optJSONArray("history") ?: org.json.JSONArray()

                val items = mutableListOf<HistoryItem>()
                for (i in 0 until historyArray.length()) {
                    val obj = historyArray.getJSONObject(i)
                    items.add(parseHistoryItem(obj))
                }

                // Backend returns oldest-first. Reverse so newest is first.
                items.reverse()

                // Assign timestamps from saved local data (newest saved → newest item)
                val savedTimestamps = userPreferences.getScanTimestamps()
                Log.d("HISTORY", "Items: ${items.size}, Saved timestamps: ${savedTimestamps.size}")

                // Match by scan key (parameter hash)
                val usedTimestamps = mutableSetOf<Int>()
                items.forEach { item ->
                    if (item.timestamp == null) {
                        val scanKey = "${item.blink_rate?.toInt()}_${item.heart_rate}_${item.sleep_hours}_${item.steps}"
                        for (idx in savedTimestamps.indices) {
                            if (idx !in usedTimestamps && savedTimestamps[idx].scanKey == scanKey) {
                                item.timestamp = savedTimestamps[idx].timestamp
                                usedTimestamps.add(idx)
                                break
                            }
                        }
                    }
                }

                // For items still without timestamps: assign remaining saved timestamps in order
                var nextSavedIdx = 0
                items.forEach { item ->
                    if (item.timestamp == null) {
                        while (nextSavedIdx < savedTimestamps.size && nextSavedIdx in usedTimestamps) {
                            nextSavedIdx++
                        }
                        if (nextSavedIdx < savedTimestamps.size) {
                            item.timestamp = savedTimestamps[nextSavedIdx].timestamp
                            usedTimestamps.add(nextSavedIdx)
                            nextSavedIdx++
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    val first = items.first()
                    Log.d("HISTORY", "First: score=${first.stress_score}, level=${first.stress_level}, ts=${first.timestamp}")
                }

                // Filter out hidden (locally deleted) scans
                val hiddenScans = userPreferences.getHiddenScans()
                val filteredItems = if (hiddenScans.isNotEmpty()) {
                    items.filter { generateScanFingerprint(it) !in hiddenScans }
                } else items

                Log.d("HISTORY", "After filtering: ${filteredItems.size} items (hidden: ${hiddenScans.size})")

                Result.success(HistoryResponse(filteredItems))

            } catch (e: Exception) {
                Log.e("HISTORY", "Error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Delete a history item from the backend.
     */
    suspend fun deleteHistoryItem(token: String, userId: String, index: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${com.mindease.BuildConfig.BACKEND_URL}history/$userId/$index"
                Log.d("HISTORY", "Deleting scan at index $index")

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", token)
                    .delete()
                    .build()

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                Log.d("HISTORY", "Delete response: ${response.code}")

                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Log.e("HISTORY", "Delete error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Manually parse a single history item JSON object using a recursive search.
     * This handles ANY backend schema (flat, nested inside "prediction", "result", etc.)
     */
    private fun parseHistoryItem(obj: org.json.JSONObject): HistoryItem {
        // Flatten the entire JSON object into a simple key-value map
        val flatMap = mutableMapOf<String, Any>()
        flattenJson("", obj, flatMap)

        Log.d("HISTORY_PARSE", "All keys: ${flatMap.keys.toList()}")
        Log.d("HISTORY_PARSE", "All values: ${flatMap.entries.take(20).map { "${it.key}=${it.value}" }}")

        return HistoryItem(
            report_id = findString(flatMap, "report_id", "_id", "id", "oid"),

            stress_level = findString(flatMap, "stress_level", "Stress_Level", "stressLevel",
                "predicted_level", "level", "result", "prediction",
                "prediction.stress_level", "prediction.Stress_Level",
                "result.stress_level", "result.Stress_Level"),

            stress_score = findDouble(flatMap, "prediction_value", "stress_score", "Stress_Score", "stressScore",
                "score", "predicted_score", "prediction_score",
                "prediction.stress_score", "prediction.Stress_Score",
                "result.stress_score", "result.Stress_Score"),

            timestamp = findString(flatMap, "timestamp", "created_at", "createdAt",
                "date", "time", "updatedAt", "updated_at",
                "prediction_timestamp", "scan_timestamp"),

            heart_rate = findInt(flatMap, "Resting_Heart_Rate_BPM", "heart_rate",
                "resting_heart_rate", "restingHeartRate", "heartRate"),

            sleep_hours = findDouble(flatMap, "Sleep_Duration_Hours", "sleep_hours",
                "sleepDuration", "sleep_duration", "sleepHours"),

            steps = findInt(flatMap, "Steps_per_Day", "steps", "stepsPerDay", "steps_per_day"),

            blink_rate = findInt(flatMap, "Eye_Blink_Rate_per_min", "blink_rate",
                "eyeBlinkRate", "eye_blink_rate", "blinkRate"),

            post_exercise_hr = findInt(flatMap, "Heart_Rate_After_Exercise_BPM",
                "post_exercise_hr", "heartRateAfterExercise", "postExerciseHr"),

            age = findInt(flatMap, "age", "Age"),

            gender = findString(flatMap, "gender", "Gender"),

            worklife = findString(flatMap, "worklife", "Worklife"),

            chest_pain = findInt(flatMap, "Chest_Pain", "chest_pain", "chestPain"),

            cholesterol = findDouble(flatMap, "Cholesterol_mg_dL", "cholesterol"),

            ecg_result = findInt(flatMap, "ECG_Result", "ecg_result", "ecgResult"),

            body_temp = findDouble(flatMap, "Body_Temperature_C", "body_temp", "bodyTemp")
        ).also {
            Log.d("HISTORY_PARSE", "Parsed → score=${it.stress_score}, level=${it.stress_level}, ts=${it.timestamp}")
        }
    }

    private fun flattenJson(prefix: String, obj: org.json.JSONObject, map: MutableMap<String, Any>) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            val fullKey = if (prefix.isNotEmpty()) "$prefix.$key" else key
            if (value is org.json.JSONObject) {
                // Handle MongoDB Extended JSON types
                when {
                    value.has("\$date") -> {
                        val dateVal = value.get("\$date")
                        if (dateVal is org.json.JSONObject && dateVal.has("\$numberLong")) {
                            map[key] = dateVal.getString("\$numberLong")
                            map[fullKey] = dateVal.getString("\$numberLong")
                        } else {
                            map[key] = dateVal.toString()
                            map[fullKey] = dateVal.toString()
                        }
                    }
                    value.has("\$numberInt") -> {
                        map[key] = value.getString("\$numberInt")
                        map[fullKey] = value.getString("\$numberInt")
                    }
                    value.has("\$numberLong") -> {
                        map[key] = value.getString("\$numberLong")
                        map[fullKey] = value.getString("\$numberLong")
                    }
                    value.has("\$numberDouble") -> {
                        map[key] = value.getString("\$numberDouble")
                        map[fullKey] = value.getString("\$numberDouble")
                    }
                    value.has("\$oid") -> {
                        map[key] = value.getString("\$oid")
                        map[fullKey] = value.getString("\$oid")
                    }
                    else -> {
                        // Recurse: store BOTH simple key and full dotted path
                        flattenJson(fullKey, value, map)
                    }
                }
            } else if (value != org.json.JSONObject.NULL) {
                map[key] = value
                if (prefix.isNotEmpty()) {
                    map[fullKey] = value
                }
            }
        }
    }

    private fun findString(map: Map<String, Any>, vararg keys: String): String? {
        for (key in keys) {
            // Exact match
            map[key]?.let { return it.toString() }

            // Case-insensitive match
            val matchingKey = map.keys.find { it.equals(key, ignoreCase = true) }
            if (matchingKey != null) return map[matchingKey].toString()
        }
        return null
    }

    private fun findInt(map: Map<String, Any>, vararg keys: String): Int? {
        val strVal = findString(map, *keys) ?: return null
        return try {
            strVal.toDoubleOrNull()?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun findDouble(map: Map<String, Any>, vararg keys: String): Double? {
        val strVal = findString(map, *keys) ?: return null
        return strVal.toDoubleOrNull()
    }

    // ---------------- PROFILE ---------------- //

    suspend fun updateProfile(
        token: String,
        request: UpdateProfileRequest
    ): Result<UpdateProfileResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = backendApi.updateProfile(token, request)

                // ✅ Save locally (IMPORTANT)
                userPreferences.saveProfile(
                    name = request.name,
                    email = request.email,
                    phone = request.phone ?: ""
                )

                Result.success(response)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---------------- CHANGE PASSWORD ---------------- //

    suspend fun changePassword(
        token: String,
        request: ChangePasswordRequest
    ): Result<ChangePasswordResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = backendApi.changePassword(token, request)
                Result.success(response)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Password change failed"
                // Parse the detail from FastAPI error response
                val detail = try {
                    org.json.JSONObject(errorBody).optString("detail", errorBody)
                } catch (_: Exception) { errorBody }
                Result.success(ChangePasswordResponse(detail = detail))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---------------- BLINK ---------------- //

    suspend fun countBlinks(video: MultipartBody.Part): Result<BlinkResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = blinkApi.countBlinks(video)

                if (response == null || response.blink_rate == null) {
                    return@withContext Result.failure(Exception("Invalid blink data"))
                }

                Result.success(response)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ---------------- CHAT ---------------- //

    suspend fun chat(message: String): Result<ChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = chatbotApi.chat(ChatMessage(message))
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}