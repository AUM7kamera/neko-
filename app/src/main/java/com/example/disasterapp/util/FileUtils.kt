package com.example.disasterapp.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileUtils {
    private const val TAG = "FileUtils"

    fun makeExecutable(file: File): Boolean {
        return try {
            if (!file.exists()) return false
            file.setExecutable(true, false)
            // additionally try chmod via runtime
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
            } catch (_: Exception) {
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "makeExecutable failed: ${e.message}")
            false
        }
    }

    fun saveStreamToFile(stream: InputStream, target: File): Boolean {
        return try {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                stream.copyTo(out)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "saveStreamToFile failed: ${e.message}")
            false
        }
    }

    fun unzipToDirectory(zipInput: InputStream, destDir: File) {
        ZipInputStream(zipInput).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
