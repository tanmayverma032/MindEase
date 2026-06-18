package com.mindease.network

import com.mindease.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Retry interceptor — safe implementation */
class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null

        while (attempt < maxRetries) {
            try {
                return chain.proceed(chain.request())
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt >= maxRetries) throw e
            }
        }

        throw lastException ?: IOException("Unknown network error")
    }
}

object ApiClient {

    private const val TIMEOUT = 60L
    private const val AUTH_TIMEOUT = 120L // Longer timeout for auth calls (Render can be slow)
    private const val BLINK_TIMEOUT = 90L // Longer timeout for video upload + processing

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Separate client for auth API with longer timeout and NO retries
    // (retrying signup can create duplicate pending users on backend)
    private val authClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(AUTH_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AUTH_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(AUTH_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // Separate client for blink API with longer timeouts (video upload + ML processing)
    private val blinkClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 1))
            .addInterceptor(logging)
            .connectTimeout(BLINK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BLINK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BLINK_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }

    private fun createRetrofit(baseUrl: String, httpClient: OkHttpClient = client): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val backendApi: BackendApi by lazy {
        createRetrofit(BuildConfig.BACKEND_URL)
            .create(BackendApi::class.java)
    }

    val backendAuthApi: BackendAuthApi by lazy {
        createRetrofit(BuildConfig.BACKEND_URL, authClient)
            .create(BackendAuthApi::class.java)
    }

    val blinkApi: BlinkApi by lazy {
        createRetrofit(BuildConfig.BLINK_URL, blinkClient)
            .create(BlinkApi::class.java)
    }

    val chatbotApi: ChatbotApi by lazy {
        createRetrofit(BuildConfig.CHATBOT_URL)
            .create(ChatbotApi::class.java)
    }
}