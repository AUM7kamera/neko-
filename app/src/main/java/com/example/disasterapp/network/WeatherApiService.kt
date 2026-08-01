package com.example.disasterapp.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface WeatherApiService {
    // Generic fetch by full URL (some public APIs don't have fixed base)
    @GET
    suspend fun fetchJson(@Url url: String): Response<ResponseBody>

    // Streaming download (for large files) if needed
    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}
