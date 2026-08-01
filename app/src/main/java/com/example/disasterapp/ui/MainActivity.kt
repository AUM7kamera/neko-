package com.example.disasterapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.disasterapp.R
import com.example.disasterapp.update.AppUpdater
import com.example.disasterapp.util.NotificationUtils
import com.example.disasterapp.util.PermissionUtils
import com.example.disasterapp.voice.VoiceVoxManager
import com.example.disasterapp.worker.WeatherCheckWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val WORK_NAME = "weather_check_periodic"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationUtils.createChannels(this)

        val btnStartWorker = findViewById<Button>(R.id.btn_start_worker)
        val btnStopWorker = findViewById<Button>(R.id.btn_stop_worker)
        val btnOpenMap = findViewById<Button>(R.id.btn_open_map)
        val btnCheckUpdate = findViewById<Button>(R.id.btn_check_update)
        val btnSynthesize = findViewById<Button>(R.id.btn_synthesize)
        val btnEngineSettings = findViewById<Button>(R.id.btn_engine_settings)

        btnStartWorker.setOnClickListener {
            requestPermissionsIfNeeded()
            startPeriodicWork()
            Toast.makeText(this, "ワーカーを登録しました", Toast.LENGTH_SHORT).show()
        }

        btnStopWorker.setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
            Toast.makeText(this, "ワーカーを解除しました", Toast.LENGTH_SHORT).show()
        }

        btnOpenMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        btnCheckUpdate.setOnClickListener {
            // Example version.json URL: replace with your hosted JSON
            val versionJsonUrl = "https://example.com/version.json"
            lifecycleScope.launch {
                try {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    val updater = AppUpdater(this@MainActivity)
                    updater.checkAndUpdate(versionJsonUrl, info.versionCode)
                } catch (e: Exception) {
                    Log.e(TAG, "update check error: ${e.message}")
                }
            }
        }

        btnEngineSettings.setOnClickListener {
            val prefs = getSharedPreferences("disaster_prefs", MODE_PRIVATE)
            val current = prefs.getString("voicevox_host", "http://127.0.0.1:50021")
            val edit = EditText(this).apply { setText(current) }
            AlertDialog.Builder(this)
                .setTitle("VOICEVOX エンジンのホスト")
                .setMessage("例: http://192.168.1.50:50021 または http://127.0.0.1:50021")
                .setView(edit)
                .setPositiveButton("保存") { _, _ ->
                    prefs.edit().putString("voicevox_host", edit.text.toString().trim()).apply()
                    Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        btnSynthesize.setOnClickListener {
            lifecycleScope.launch {
                val manager = VoiceVoxManager(this@MainActivity)
                val uri = manager.synthesizeToWavAndSave("これはテストの合成音声です。", "test_output.wav")
                if (uri != null) {
                    manager.shareUri(uri)
                } else {
                    Toast.makeText(this@MainActivity, "合成に失敗しました。エンジンが起動しているか確認してください。", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Request permissions on launch
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
        if (missing.isNotEmpty()) {
            PermissionUtils.requestPermissions(this, missing, 100)
        }
    }

    private fun startPeriodicWork() {
        val workRequest = PeriodicWorkRequestBuilder<WeatherCheckWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
