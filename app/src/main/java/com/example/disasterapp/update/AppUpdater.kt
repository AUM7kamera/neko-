package com.example.disasterapp.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.disasterapp.network.GitHubApiService
import com.example.disasterapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

class AppUpdater(private val context: Context) {
    companion object {
        private const val TAG = "AppUpdater"
    }

    private val api by lazy {
        RetrofitClient.create("https://api.github.com/").create(GitHubApiService::class.java)
    }

    /**
     * Check a version.json URL and if newer, download APK and prompt install.
     * versionJsonUrl: full URL to a JSON with structure { versionCode:Int, versionName:String, apkUrl:String }
     */
    suspend fun checkAndUpdate(versionJsonUrl: String, currentVersionCode: Int) {
        try {
            val resp = api.fetchVersionJson(versionJsonUrl)
            if (!resp.isSuccessful) {
                Log.w(TAG, "version.json fetch failed: ${resp.code()}")
                return
            }
            val info = resp.body() ?: return
            if (info.versionCode <= currentVersionCode) {
                Log.i(TAG, "No update available: ${info.versionCode} <= $currentVersionCode")
                return
            }

            // download apk
            val apkResp = api.downloadFile(info.apkUrl)
            if (!apkResp.isSuccessful) {
                Log.w(TAG, "apk download failed: ${apkResp.code()}")
                return
            }

            val apkFile = saveResponseBodyToFile(apkResp.body(), "update.apk") ?: return

            promptInstall(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "update check failed: ${e.message}", e)
        }
    }

    private suspend fun saveResponseBodyToFile(body: ResponseBody?, filename: String): File? = withContext(Dispatchers.IO) {
        if (body == null) return@withContext null
        try {
            val file = File(context.cacheDir, filename)
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext file
        } catch (e: Exception) {
            Log.e(TAG, "failed saving apk: ${e.message}", e)
            return@withContext null
        }
    }

    private fun promptInstall(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // For Android 8+ we need REQUEST_INSTALL_PACKAGES permission (user setting)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // open settings
                    val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settings)
                    return
                }
            }

            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Installer activity not found: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "promptInstall error: ${e.message}", e)
        }
    }
}
