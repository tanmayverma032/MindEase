package com.mindease.network

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val message: String? = null,
    val user_id: String? = null,
    val token: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val error: String? = null
)

data class AuthRequest(
    val email: String? = null,
    val password: String,
    val name: String? = null
)


data class PredictionResponse(
    val stress_level: String? = null,
    val stress_score: Int? = null,
    val report_id: String? = null
)

data class HistoryItem(
    @SerializedName(value = "report_id", alternate = ["_id", "id"])
    val report_id: String? = null,

    @SerializedName(value = "stress_level", alternate = ["Stress_Level", "stressLevel", "predicted_level"])
    var stress_level: String? = null,

    @SerializedName(value = "prediction_value", alternate = ["stress_score", "Stress_Score", "stressScore", "score", "predicted_score"])
    val stress_score: Double? = null,

    @SerializedName(value = "timestamp", alternate = ["created_at", "createdAt", "date"])
    var timestamp: String? = null,

    // Backend stores these with StressInput field names
    @SerializedName(value = "Resting_Heart_Rate_BPM", alternate = ["heart_rate", "resting_heart_rate"])
    val heart_rate: Int? = null,

    @SerializedName(value = "Sleep_Duration_Hours", alternate = ["sleep_hours", "sleep_duration"])
    val sleep_hours: Double? = null,

    @SerializedName(value = "Steps_per_Day", alternate = ["steps", "steps_per_day"])
    val steps: Int? = null,

    @SerializedName(value = "Eye_Blink_Rate_per_min", alternate = ["blink_rate", "eye_blink_rate"])
    val blink_rate: Int? = null,

    @SerializedName(value = "Heart_Rate_After_Exercise_BPM", alternate = ["post_exercise_hr"])
    val post_exercise_hr: Int? = null,

    val age: Int? = null,
    val gender: String? = null,
    val worklife: String? = null,

    @SerializedName(value = "Chest_Pain", alternate = ["chest_pain"])
    val chest_pain: Int? = null,

    @SerializedName(value = "Cholesterol_mg_dL", alternate = ["cholesterol"])
    val cholesterol: Double? = null,

    @SerializedName(value = "ECG_Result", alternate = ["ecg_result"])
    val ecg_result: Int? = null,

    @SerializedName(value = "Body_Temperature_C", alternate = ["body_temp"])
    val body_temp: Double? = null,

    // Nested input object from backend
    val input: HistoryInput? = null
)

/**
 * The backend wraps scan input parameters inside an "input" sub-object.
 * This class mirrors those fields so Gson can auto-deserialize them.
 */
data class HistoryInput(
    @SerializedName("Resting_Heart_Rate_BPM") val heart_rate: Double? = null,
    @SerializedName("Sleep_Duration_Hours") val sleep_hours: Double? = null,
    @SerializedName("Steps_per_Day") val steps: Double? = null,
    @SerializedName("Eye_Blink_Rate_per_min") val blink_rate: Double? = null,
    @SerializedName("Heart_Rate_After_Exercise_BPM") val post_exercise_hr: Double? = null,
    val age: Int? = null,
    val gender: String? = null,
    val worklife: String? = null,
    @SerializedName("Chest_Pain") val chest_pain: Int? = null,
    @SerializedName("Cholesterol_mg_dL") val cholesterol: Double? = null,
    @SerializedName("ECG_Result") val ecg_result: Int? = null,
    @SerializedName("Body_Temperature_C") val body_temp: Double? = null,
    val user_id: String? = null
)

data class HistoryResponse(
    val history: List<HistoryItem> = emptyList()
)

data class ChatMessage(
    val message: String
)

data class ChatResponse(
    val reply: String
)

data class BlinkResponse(
    val blink_rate: Int
)

data class UpdateProfileRequest(
    val name: String,
    val email: String,
    val phone: String
)

data class UpdateProfileResponse(
    val message: String? = null,
    val error: String? = null
)

data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)

data class ChangePasswordResponse(
    val message: String? = null,
    val detail: String? = null,
    val error: String? = null
)

data class VerifyEmailRequest(
    val email: String,
    val otp_code: String
)
