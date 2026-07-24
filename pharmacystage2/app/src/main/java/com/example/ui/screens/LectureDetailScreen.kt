package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LectureEntity
import com.example.ui.theme.CardBorder
import com.example.ui.theme.GoldAccent
import com.example.util.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.foundation.layout.statusBarsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureDetailScreen(
    lecture: LectureEntity,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDownload: (LectureEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableIntStateOf(lecture.pageCount) }
    var currentPage by remember { mutableIntStateOf(1) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPdf by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("جاري فتح ملف المحاضرة...") }

    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 3.0f)
        offset += offsetChange
    }

    // Load PDF file natively
    LaunchedEffect(lecture) {
        isLoadingPdf = true
        statusText = "جاري تهيئة عارض الملفات..."
        withContext(Dispatchers.IO) {
            try {
                val file = PdfHelper.getOrDownloadPdfFile(context, lecture) { msg ->
                    statusText = msg
                }
                pdfFile = file

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = pfd

                val renderer = PdfRenderer(pfd)
                pdfRenderer = renderer
                totalPages = renderer.pageCount
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingPdf = false
            }
        }
    }

    // Render Page whenever currentPage or pdfRenderer changes
    LaunchedEffect(pdfRenderer, currentPage) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        val pageIndex = (currentPage - 1).coerceIn(0, totalPages - 1)

        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                val displayMetrics = context.resources.displayMetrics
                val densityScale = (displayMetrics.density * 1.2f).coerceIn(1.5f, 2.2f)

                val width = (page.width * densityScale).toInt()
                val height = (page.height * densityScale).toInt()
                
                val bitmap = try {
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Bitmap.createBitmap((page.width * 1.2f).toInt(), (page.height * 1.2f).toInt(), Bitmap.Config.ARGB_8888)
                }

                // Fill canvas with pure solid white background to prevent transparent anti-aliasing blending artifacts
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                pageBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // PDF Reader Header Bar
        Surface(
            shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pdf_reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = lecture.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${lecture.subject} • ${lecture.doctorName.ifBlank { "كلية الصيدلة" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Open in External App
                    IconButton(
                        onClick = {
                            pdfFile?.let { file ->
                                PdfHelper.openPdfInExternalApp(context, file)
                            } ?: run {
                                Toast.makeText(context, "جاري تحضير ملف PDF...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open External",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (lecture.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (lecture.isBookmarked) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onToggleDownload(lecture) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (lecture.isDownloaded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            contentColor = if (lecture.isDownloaded) MaterialTheme.colorScheme.primary else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("pdf_reader_download_button")
                    ) {
                        Icon(
                            imageVector = if (lecture.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lecture.isDownloaded) "محملة بالكامل" else "تنزيل المحاضرة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PDF View Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingPdf) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = GoldAccent)
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (pageBitmap != null) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformState),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page $currentPage",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF Reader",
                        tint = GoldAccent,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "اضغط للتحميل المباشر وقراءة المحاضرة بالكامل عبر السيرفر",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingPdf = true
                                    statusText = "جاري الاتصال بالسيرفر وتنزيل المحاضرة..."
                                    try {
                                        val file = PdfHelper.getOrDownloadPdfFile(context, lecture) { msg ->
                                            statusText = msg
                                        }
                                        pdfFile = file
                                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                        fileDescriptor = pfd
                                        val renderer = PdfRenderer(pfd)
                                        pdfRenderer = renderer
                                        totalPages = renderer.pageCount
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "خطأ في التنزيل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isLoadingPdf = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحميل المحاضرة من السيرفر", fontSize = 12.sp)
                        }

                        pdfFile?.let { file ->
                            Button(
                                onClick = { PdfHelper.openPdfInExternalApp(context, file) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("قارئ خارجي", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // PDF Navigation Controls
        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Navigation (Chevron Prev / Next)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPage > 1) currentPage-- },
                        enabled = currentPage > 1,
                        modifier = Modifier.testTag("pdf_prev_page_button")
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Prev Page")
                    }

                    Text(
                        text = "صفحة $currentPage من $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { if (currentPage < totalPages) currentPage++ },
                        enabled = currentPage < totalPages,
                        modifier = Modifier.testTag("pdf_next_page_button")
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Next Page")
                    }
                }

                // Zoom Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        scale = (scale - 0.2f).coerceAtLeast(0.8f)
                        if (scale == 1.0f) offset = Offset.Zero
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    IconButton(onClick = { scale = (scale + 0.2f).coerceAtMost(3.0f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
