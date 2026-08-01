package com.example.disasterapp.voice

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.disasterapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.net.Socket
import java.net.URI

/**
 * VoiceVoxManager
 * - Attempts to use a local VOICEVOX engine binary placed under filesDir/voicevox/<name>
 *   where <name> is chosen based on Build.SUPPORTED_ABIS (voicevox_arm64, voicevox_armeabi_v7a,
 *   voicevox_x86_64, voicevox_x86).
 * - If a local engine is not available or not listening, falls back to a user-configured host URL
 *   stored in SharedPreferences (key: "voicevox_host").
 * - Synthesizes via /audio_query and /synthesis and saves WAV to MediaStore Downloads.
 */
class VoiceVoxManager(private val context: Context) {
    companion object {
        private const val TAG = "VoiceVoxManager"
        private const val VOICEVOX_PORT = 50021
        private const val VOICEVOX_DIR = "voicevox"
        private const val STARTUP_TIMEOUT_MS = 30_000L
    }

    private val filesDir: File = File(context.filesDir, VOICEVOX_DIR)
    private var process: Process? = null

    init {
        if (!filesDir.exists()) filesDir.mkdirs()
    }

    private fun getPreferredBinaryName(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.startsWith("arm64") -> "voicevox_arm64"
            abi.startsWith("armeabi") -> "voicevox_armeabi_v7a"
            abi.contains("x86_64") -> "voicevox_x86_64"
            abi.contains("x86") -> "voicevox_x86"
            else -> "voicevox_arm64"
        }
    }

    private fun getConfiguredHostUrl(): String {
        val prefs = context.getSharedPreferences("disaster_prefs", Context.MODE_PRIVATE)
        val host = prefs.getString("voicevox_host", null)
        return host?.trim()?.takeIf { it.isNotEmpty() } ?: "http://127.0.0.1:$VOICEVOX_PORT"
    }

    suspend fun ensureEngineStarted(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First, check configured host (may be localhost or remote)
            val configuredHostUrl = getConfiguredHostUrl()
            val (cfgHost, cfgPort) = parseHostPort(configuredHostUrl)
            if (isPortOpen(cfgHost, cfgPort)) {
                Log.i(TAG, "VOICEVOX listening at configured host $cfgHost:$cfgPort")
                return@withContext true
            }

            // Try local binary candidate based on ABI
            val candidateName = getPreferredBinaryName()
            val candidate = File(filesDir, candidateName)
            if (candidate.exists()) {
                candidate.setExecutable(true)
                startLocalBinary(candidate)
                if (waitForStartup(STARTUP_TIMEOUT_MS)) {
                    Log.i(TAG, "VOICEVOX started from local binary: ${candidate.absolutePath}")
                    return@withContext true
                }
            }

            // If candidate not present or failed to start, also try plain 'voicevox' filename as fallback
            val fallback = File(filesDir, "voicevox")
            if (fallback.exists()) {
                fallback.setExecutable(true)
                startLocalBinary(fallback)
                if (waitForStartup(STARTUP_TIMEOUT_MS)) {
                    Log.i(TAG, "VOICEVOX started from fallback binary: ${fallback.absolutePath}")
                    return@withContext true
                }
            }

            // Final attempt: re-check configured host
            if (isPortOpen(cfgHost, cfgPort)) {
                Log.i(TAG, "VOICEVOX available at configured host after attempts $cfgHost:$cfgPort")
                return@withContext true
            }

            Log.w(TAG, "No VOICEVOX engine available (tried configured host and local binaries). Configured host: $configuredHostUrl, candidate: ${candidate.absolutePath}")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure VOICEVOX started: ${e.message}", e)
            return@withContext false
        }
    }

    private fun parseHostPort(urlString: String): Pair<String, Int> {
        return try {
            val uri = URI(urlString)
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port == -1) VOICEVOX_PORT else uri.port
            Pair(host, port)
        } catch (e: Exception) {
            Pair("127.0.0.1", VOICEVOX_PORT)
        }
    }

    private fun startLocalBinary(binaryFile: File) {
        try {
            val pb = ProcessBuilder(binaryFile.absolutePath)
            pb.directory(filesDir)
            pb.redirectErrorStream(true)
            process = pb.start()
            Log.i(TAG, "Started VOICEVOX process: ${process?.pid()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting binary: ${e.message}", e)
        }
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket(host, port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun waitForStartup(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val (host, port) = parseHostPort(getConfiguredHostUrl())
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isPortOpen(host, port)) return@withContext true
            kotlinx.coroutines.delay(500)
        }
        return@withContext false
    }

    /**
     * Synthesize text to WAV, save to MediaStore, and return the saved URI.
     */
    suspend fun synthesizeToWavAndSave(text: String, filename: String = "voicevox_output.wav"): Uri? = withContext(Dispatchers.IO) {
        try {
            // Ensure engine is up (either local or configured host)
            val started = ensureEngineStarted()
            if (!started) {
                Log.e(TAG, "VOICEVOX engine not started")
                return@withContext null
            }

            // Build Retrofit using configured host URL
            val baseUrl = getConfiguredHostUrl().let { if (!it.endsWith("/")) "$it/" else it }
            val retrofit = RetrofitClient.create(baseUrl)
            val apiService = retrofit.create(VoiceVoxApi::class.java)

            // 1) /audio_query
            val audioQueryResp: Response<ResponseBody> = apiService.audioQuery(text = text, speaker = 1)
            if (!audioQueryResp.isSuccessful) {
                Log.e(TAG, "audio_query failed: ${audioQueryResp.code()}")
                return@withContext null
            }
            val audioQueryJson = audioQueryResp.body()?.string() ?: ""

            // 2) /synthesis
            val requestBody = audioQueryJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val synthesisResp: Response<ResponseBody> = apiService.synthesis(speaker = 1, body = requestBody)
            if (!synthesisResp.isSuccessful) {
                Log.e(TAG, "synthesis failed: ${synthesisResp.code()}")
                return@withContext null
            }

            val wavBytes = synthesisResp.body()?.bytes() ?: byteArrayOf()
            if (wavBytes.isEmpty()) return@withContext null

            // Save to MediaStore in Downloads/VOICEVOX
            val savedUri = saveWavToDownloads(wavBytes, filename)

            // Return saved URI
            return@withContext savedUri
        } catch (e: Exception) {
            Log.e(TAG, "synthesis error: ${e.message}", e)
            return@withContext null
        }
    }

    private fun saveWavToDownloads(bytes: ByteArray, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV: ${e.message}", e)
            // cleanup
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return null
        }
    }

    fun shareUri(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(share)
    }

    fun stopEngine() {
        try {
            process?.destroy()
            process = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping engine: ${e.message}")
        }
    }
}

// Minimal Retrofit interface for VOICEVOX local HTTP API
interface VoiceVoxApi {
    @retrofit2.http.GET("audio_query")
    suspend fun audioQuery(
        @retrofit2.http.Query("text") text: String,
        @retrofit2.http.Query("speaker") speaker: Int
    ): Response<ResponseBody>

    @retrofit2.http.POST("synthesis")
    suspend fun synthesis(
        @retrofit2.http.Query("speaker") speaker: Int,
        @retrofit2.http.Body body: okhttp3.RequestBody
    ): Response<ResponseBody>
}
