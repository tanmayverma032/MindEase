package com.mindease.network

import com.google.gson.annotations.SerializedName

data class StressInput(

    @SerializedName("Eye_Blink_Rate_per_min")
    val eyeBlinkRate: Int,

    @SerializedName("Resting_Heart_Rate_BPM")
    val restingHeartRate: Int,

    @SerializedName("Heart_Rate_After_Exercise_BPM")
    val heartRateAfterExercise: Int,

    // ✅ Backend expects lowercase
    @SerializedName("age")
    val age: Int,

    @SerializedName("gender")
    val gender: String,

    @SerializedName("worklife")
    val worklife: String,

    @SerializedName("Sleep_Duration_Hours")
    val sleepDuration: Double,

    // ✅ Must be INT (0,1,2)
    @SerializedName("Chest_Pain")
    val chestPain: Int,

    @SerializedName("Cholesterol_mg_dL")
    val cholesterol: Double,

    // ✅ Must be INT (0,1,2)
    @SerializedName("ECG_Result")
    val ecgResult: Int,

    @SerializedName("Body_Temperature_C")
    val bodyTemp: Double,

    @SerializedName("Steps_per_Day")
    val steps: Int,

    // ✅ safer nullable
    @SerializedName("user_id")
    val userId: String?
)