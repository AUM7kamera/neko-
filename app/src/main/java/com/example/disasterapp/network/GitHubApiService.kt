package com.example.disasterapp.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import retrofit2.http.Path
import retrofit2.http.Query

// Data class for version.json expected structure
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

interface GitHubApiService {
    // Fetch a version.json from any URL
    @GET
    suspend fun fetchVersionJson(@Url url: String): Response<VersionInfo>

    // Download arbitrary file (APK, binary)
    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}
