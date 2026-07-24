package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.LectureEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object PdfHelper {

    suspend fun getOrDownloadPdfFile(
        context: Context,
        lecture: LectureEntity,
        onProgress: (String) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val safeFileName = lecture.title.replace(Regex("[^a-zA-Z0-9_ -]"), "_") + "_${lecture.id}.pdf"
        val targetFile = File(downloadDir, safeFileName)

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        val urlString = lecture.fileUrl
        if (urlString.isBlank()) {
            throw java.io.FileNotFoundException("رابط الملف فارغ")
        }

        // 1. Base64 encoded PDF string
        if (urlString.startsWith("data:application/pdf;base64,") || urlString.startsWith("data:pdf;base64,") || urlString.startsWith("base64:") || urlString.contains("base64,")) {
            try {
                val base64Data = if (urlString.contains("base64,")) urlString.substringAfter("base64,") else urlString.removePrefix("base64:")
                val cleanBase64 = base64Data.replace("\n", "").replace("\r", "").replace(" ", "").trim()
                val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                FileOutputStream(targetFile).use { output ->
                    output.write(bytes)
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@withContext targetFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (targetFile.exists()) targetFile.delete()
                throw e
            }
        } 
        // 2. Local File path
        else if (urlString.startsWith("file://") || urlString.startsWith("/")) {
            try {
                val filePath = urlString.removePrefix("file://")
                val sourceFile = File(filePath)
                if (sourceFile.exists() && sourceFile.length() > 0) {
                    sourceFile.copyTo(targetFile, overwrite = true)
                    if (targetFile.exists() && targetFile.length() > 0) {
                        return@withContext targetFile
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        } 
        // 3. Content URI from System Picker
        else if (urlString.startsWith("content://")) {
            try {
                val uri = Uri.parse(urlString)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return@withContext targetFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (targetFile.exists()) targetFile.delete()
                throw e
            }
        } 
        // 4. Remote HTTP/HTTPS / Cloud Storage downloadURL
        else if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
            val tempFile = File(downloadDir, "${safeFileName}.tmp")
            try {
                onProgress("جاري الاتصال بالخادم السحابي...")
                var currentUrl = urlString
                var redirects = 0
                val maxRedirects = 6
                var success = false

                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.connect()

                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308
                    ) {
                        val newUrl = connection.getHeaderField("Location")
                        if (!newUrl.isNullOrBlank()) {
                            currentUrl = newUrl
                            redirects++
                            connection.disconnect()
                            continue
                        }
                    }

                    if (status in 200..299) {
                        val contentLength = connection.contentLengthLong
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int
                                var totalRead = 0L
                                var lastReportedPercent = -1
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead
                                    if (contentLength > 0) {
                                        val percent = ((totalRead * 100) / contentLength).toInt()
                                        if (percent != lastReportedPercent) {
                                            lastReportedPercent = percent
                                            onProgress("جاري تنزيل المحاضرة من السيرفر ($percent%)...")
                                        }
                                    } else {
                                        val mbRead = String.format(java.util.Locale.US, "%.1f MB", totalRead / (1024.0 * 1024.0))
                                        onProgress("جاري تنزيل المحاضرة ($mbRead)...")
                                    }
                                }
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (targetFile.exists()) targetFile.delete()
                            tempFile.renameTo(targetFile)
                            success = true
                        }
                        connection.disconnect()
                        break
                    } else {
                        connection.disconnect()
                        throw java.io.IOException("استجابة غير صالحة من السيرفر: $status")
                    }
                }

                if (success && targetFile.exists() && targetFile.length() > 0) {
                    return@withContext targetFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (tempFile.exists()) tempFile.delete()
                if (targetFile.exists()) targetFile.delete()
                throw java.io.IOException("تعذر تنزيل المحاضرة من السيرفر. يرجى التأكد من توفر الاتصال بالإنترنت.", e)
            }
        }

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        } else {
            throw java.io.FileNotFoundException("تعذر تحميل ملف الـ PDF. تأكد من توفر الاتصال ورابط الملف الحقيقي.")
        }
    }

    fun savePdfToPublicDeviceStorage(context: Context, sourceFile: File, lectureTitle: String): String {
        val safeTitle = lectureTitle.replace(Regex("[^a-zA-Z0-9_آ-ي -]"), "_") + ".pdf"
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeTitle)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mustansiriyah_Pharmacy")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    return "Download/Mustansiriyah_Pharmacy/$safeTitle"
                }
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "Mustansiriyah_Pharmacy")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            val publicFile = File(appDir, safeTitle)
            sourceFile.copyTo(publicFile, overwrite = true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            downloadManager?.addCompletedDownload(
                safeTitle,
                "محاضرة صيدلة - $lectureTitle",
                true,
                "application/pdf",
                publicFile.absolutePath,
                publicFile.length(),
                true
            )
            return publicFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return sourceFile.absolutePath
        }
    }

    fun openPdfInExternalApp(context: Context, pdfFile: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "افتح ملف PDF بواسطة..."))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "لم يتم العثور على تطبيق لقراءة ملفات PDF!", Toast.LENGTH_SHORT).show()
        }
    }
}
