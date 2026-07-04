package com.example.omnispread.data

import android.content.Context
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientProvider {
    private const val PREF_NAME = "omnispread_prefs"
    private const val KEY_BASE_URL = "base_url"
    const val DEFAULT_URL = "http://10.0.2.2:8000/"

    private var _service: ApiService? = null
    private var _currentUrl: String = ""

    fun getService(context: Context): ApiService {
        val savedUrl = getSavedUrl(context)
        if (_service == null || savedUrl != _currentUrl) {
            _currentUrl = savedUrl
            _service = buildService(savedUrl)
        }
        return _service!!
    }

    fun getSavedUrl(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun saveUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, url.trim()).apply()
        _service = null
    }

    private fun buildService(baseUrl: String): ApiService {
        val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class OmniSpreadRepository(private val api: ApiService) {

    suspend fun startScan(request: ScanRequest): ScanStartResponse = api.startScan(request)

    suspend fun pollResults(
        taskId: String,
        onUpdate: (TaskResult) -> Unit,
        intervalMs: Long = 2000L,
        maxAttempts: Int = 300,
    ): TaskResult {
        repeat(maxAttempts) {
            val result = api.getResults(taskId)
            onUpdate(result)
            if (result.status == "completed" || result.status == "failed") return result
            delay(intervalMs)
        }
        throw Exception("Polling timed out after $maxAttempts attempts")
    }

    suspend fun runBacktest(request: BacktestRequest): BacktestResult = api.runBacktest(request)
}
