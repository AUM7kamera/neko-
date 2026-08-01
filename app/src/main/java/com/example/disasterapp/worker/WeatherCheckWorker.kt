package com.example.disasterapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.disasterapp.network.RetrofitClient
import com.example.disasterapp.network.WeatherApiService
import com.example.disasterapp.util.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * WeatherCheckWorker
 * - Periodically fetches a configured URL (e.g., local or public weather/alert JSON)
 * - If an "alert" condition is detected (simple heuristic), posts a high-priority notification
 *
 * Note: This worker expects you to schedule it from the app with appropriate constraints.
 */
class WeatherCheckWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val DEFAULT_CHECK_URL = "https://www.jma.go.jp/bosai/forecast/data/forecast/" // example base
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val url = inputData.getString("check_url") ?: DEFAULT_CHECK_URL
            val retrofit = RetrofitClient.create("") // We'll call full URL via service
            val api = retrofit.create(WeatherApiService::class.java)
            val resp: Response<ResponseBody> = api.fetchJson(url)
            if (!resp.isSuccessful) return@withContext Result.retry()

            val body = resp.body()?.string() ?: ""

            // Very naive alert detection: look for keywords. Replace with proper parsing for production.
            val lowered = body.lowercase()
            if (lowered.contains("warning") || lowered.contains("警報") || lowered.contains("注意報") || lowered.contains("warning")) {
                NotificationUtils.showEmergencyNotification(context, "警報を検出しました", "気象庁の警報または注意報を検出しました。")
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            return@withContext Result.retry()
        }
    }
}
