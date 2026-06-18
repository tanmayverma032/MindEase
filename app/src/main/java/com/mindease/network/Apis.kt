package com.mindease.network

import okhttp3.MultipartBody
import retrofit2.http.*

// Separate auth API with longer timeout and no retries
interface BackendAuthApi {

    @POST("login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("signup")
    suspend fun signup(@Body request: AuthRequest): AuthResponse

    @POST("verify_email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): AuthResponse
}

interface BackendApi {

    @POST("predict_stress")
    suspend fun predictStress(
        @Header("Authorization") token: String,
        @Body input: StressInput
    ): PredictionResponse

    @GET("history/{user_id}")
    suspend fun getHistory(
        @Header("Authorization") token: String,
        @Path("user_id") userId: String
    ): HistoryResponse

    @PUT("update_profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): UpdateProfileResponse

    @POST("change_password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): ChangePasswordResponse
}

// FIXED: No trailing slash — matches actual API endpoint /blink-count
interface BlinkApi {

    @Multipart
    @POST("blink-count")
    suspend fun countBlinks(
        @Part video: MultipartBody.Part
    ): BlinkResponse
}

interface ChatbotApi {

    @POST("chat")
    suspend fun chat(@Body request: ChatMessage): ChatResponse
}