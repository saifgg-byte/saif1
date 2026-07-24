package com.example.ui.screens

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resumeWithException
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LectureEntity
import com.example.data.SubjectEntity
import com.example.data.UserEntity
import com.example.ui.theme.CardBorder
import com.example.ui.theme.GoldAccent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    subjects: List<SubjectEntity>,
    lectures: List<LectureEntity>,
    students: List<UserEntity> = emptyList(),
    onUploadLecture: (
        title: String,
        subject: String,
        semester: Int,
        fileUrl: String,
        sendNotification: Boolean,
        fileSize: String?,
        pageCount: Int?
    ) -> Unit,
    onToggleSubjectExamPinned: (subjectName: String, isPinned: Boolean) -> Unit,
    onPublishAnnouncement: (title: String, message: String) -> Unit,
    onDeleteLecture: (LectureEntity) -> Unit,
    onAddSubjectFolder: (String) -> Unit = {},
    onDeleteSubjectFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }
    var detectedPageCount by remember { mutableIntStateOf(1) }
    var isUploadingToStorage by remember { mutableStateOf(false) }
    var uploadStorageStatus by remember { mutableStateOf("") }

    var adminTab by remember { mutableIntStateOf(0) } // 0 = Upload, 1 = Exam Pinning, 2 = Announcements, 3 = Manage, 4 = Students Server

    var titleInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var selectedSemesterInput by remember { mutableIntStateOf(1) }
    var fileUrlInput by remember { mutableStateOf("") }
    var sendNotificationInput by remember { mutableStateOf(true) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val (fName, fSize) = getFileNameAndSizeFromUri(context, selectedUri)
            selectedFileName = fName
            if (titleInput.isBlank()) {
                titleInput = fName.removeSuffix(".pdf").removeSuffix(".PDF")
            }

            try {
                // Stream-copy to internal storage safely without OutOfMemory on huge files
                val sharedDir = File(context.filesDir, "shared_pdf_lectures").apply { mkdirs() }
                val safeName = "lec_${System.currentTimeMillis()}.pdf"
                val internalFile = File(sharedDir, safeName)
                
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(internalFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (internalFile.exists() && internalFile.length() > 0) {
                    // Calculate exact real file size
                    val bytesSize = internalFile.length()
                    val mb = bytesSize / (1024.0 * 1024.0)
                    val realSizeStr = if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb) else String.format(java.util.Locale.US, "%d KB", bytesSize / 1024)
                    selectedFileSize = realSizeStr

                    // Calculate real page count using android.graphics.pdf.PdfRenderer
                    try {
                        val pfd = ParcelFileDescriptor.open(internalFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        detectedPageCount = renderer.pageCount
                        renderer.close()
                        pfd.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Default URL fallback
                    fileUrlInput = "file://${internalFile.absolutePath}"

                    // Upload file via putFile to Cloud Storage (supports unlimited file size & 400+ pages)
                    isUploadingToStorage = true
                    uploadStorageStatus = "جاري تهيئة رفع الملف إلى السيرفر السحابي..."
                    try {
                        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                            .child("lectures/$safeName")
                        
                        val uploadTask = storageRef.putFile(selectedUri)
                        uploadTask.addOnProgressListener { snapshot ->
                            val total = snapshot.totalByteCount
                            val transferred = snapshot.bytesTransferred
                            val pct = if (total > 0) ((transferred * 100) / total).toInt() else 0
                            uploadStorageStatus = "جاري رفع الملف لجميع المستخدمين عبر السيرفر السحابي ($pct%)..."
                        }.addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                fileUrlInput = downloadUri.toString()
                                isUploadingToStorage = false
                                uploadStorageStatus = "تم الرفع والربط بنجاح مع السيرفر السحابي ☁️"
                            }.addOnFailureListener {
                                isUploadingToStorage = false
                                uploadStorageStatus = "تم إعداد الملف وجاهز للرفع"
                            }
                        }.addOnFailureListener {
                            isUploadingToStorage = false
                            uploadStorageStatus = "تم إعداد الملف وجاهز للرفع"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isUploadingToStorage = false
                        uploadStorageStatus = "تم إعداد الملف وجاهز للرفع"
                    }
                } else {
                    fileUrlInput = selectedUri.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fileUrlInput = selectedUri.toString()
            }
        }
    }

    var announceTitleInput by remember { mutableStateOf("") }
    var announceMsgInput by remember { mutableStateOf("") }

    var showSuccessMessage by remember { mutableStateOf(false) }
    var successText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Admin Header Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Column {
                    Text(
                        text = "لوحة إدارة السيرفر",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "رفع الملازم تلقائياً، إرسال الإشعارات، ومعاينة أسماء الطلاب المسجلين بالسيرفر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Sub Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = adminTab,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 12.dp
        ) {
            Tab(
                selected = adminTab == 0,
                onClick = { adminTab = 0 },
                text = { Text("رفع ملازمة", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = adminTab == 1,
                onClick = { adminTab = 1 },
                text = { Text("إدارة المجلدات (${subjects.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = adminTab == 2,
                onClick = { adminTab = 2 },
                text = { Text("إرسال تبليغ", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = adminTab == 3,
                onClick = { adminTab = 3 },
                text = { Text("إدارة المحاضرات", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = adminTab == 4,
                onClick = { adminTab = 4 },
                text = { Text("سيرفر الطلاب (${students.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        // Success Alert Popup
        AnimatedVisibility(visible = showSuccessMessage) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Success", tint = Color.White)
                    Text(text = successText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (adminTab) {
                0 -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "بيانات المحاضرة الجديدة (المرحلة الثانية)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = titleInput,
                                    onValueChange = { titleInput = it },
                                    label = { Text("عنوان المحاضرة *") },
                                    placeholder = { Text("المحاضرة 3: المركبات الأروماتية") },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_lecture_title_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = subjectInput,
                                    onValueChange = { subjectInput = it },
                                    label = { Text("اسم المادة الصيدلانية *") },
                                    placeholder = { Text("الكيمياء العضوية الصيدلانية") },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_lecture_subject_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { selectedSemesterInput = 1 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedSemesterInput == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (selectedSemesterInput == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("الكورس الأول")
                                    }
                                    Button(
                                        onClick = { selectedSemesterInput = 2 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedSemesterInput == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (selectedSemesterInput == 2) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("الكورس الثاني")
                                    }
                                }

                                // System File Picker / Browsing Section
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ملف المحاضرة (PDF):",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Button(
                                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("admin_browse_files_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = "Browse",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تصفح ملفات النظام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (selectedFileName != null) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = "PDF",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = selectedFileName ?: "",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "حجم الملف: ${selectedFileSize ?: "حسب الملف"} • عدد الصفحات: $detectedPageCount صفحة",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (uploadStorageStatus.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (isUploadingToStorage) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(12.dp),
                                                                    strokeWidth = 2.dp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                            }
                                                            Text(
                                                                text = uploadStorageStatus,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (isUploadingToStorage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = fileUrlInput,
                                            onValueChange = { fileUrlInput = it },
                                            label = { Text("أو أدخل رابط الملف المباشر (PDF)") },
                                            placeholder = { Text("https://mustansiriyah.edu.iq/docs/lecture.pdf") },
                                            modifier = Modifier.fillMaxWidth().testTag("admin_file_url_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Auto",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "تم ربط تخزين المحاضرات مع السيرفر السحابي لضمان وصولها الفوري لجميع الطلاب",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Checkbox(
                                        checked = sendNotificationInput,
                                        onCheckedChange = { sendNotificationInput = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Alert",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إرسال إشعار فوري للجهاز والتطبيق لجميع الطلاب",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Button(
                                    enabled = !isUploadingToStorage,
                                    onClick = {
                                        if (titleInput.isNotBlank() && subjectInput.isNotBlank()) {
                                            coroutineScope.launch {
                                                var finalUrl = fileUrlInput
                                                if (finalUrl.startsWith("file://")) {
                                                    isUploadingToStorage = true
                                                    uploadStorageStatus = "جاري رفع الملف إلى السيرفر السحابي..."
                                                    val filePath = finalUrl.removePrefix("file://")
                                                    val localF = File(filePath)
                                                    if (localF.exists() && localF.length() > 0) {
                                                        if (localF.length() <= 700000) {
                                                            val bytes = localF.readBytes()
                                                            finalUrl = "data:application/pdf;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                                        } else {
                                                            // Retry the cloud upload a few times before giving up —
                                                            // large files are more likely to hit a transient network
                                                            // hiccup, and we must NEVER fall through with a local-only
                                                            // "file://" URL since that path only exists on this device.
                                                            var uploadSucceeded = false
                                                            var lastError: Exception? = null
                                                            val maxAttempts = 3
                                                            for (attempt in 1..maxAttempts) {
                                                                try {
                                                                    uploadStorageStatus = if (attempt == 1) "جاري رفع الملف إلى السيرفر السحابي..." else "إعادة محاولة رفع الملف ($attempt/$maxAttempts)..."
                                                                    val safeName = "lec_${System.currentTimeMillis()}.pdf"
                                                                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("lectures/$safeName")
                                                                    val downloadUri = kotlinx.coroutines.suspendCancellableCoroutine<Uri> { continuation ->
                                                                        storageRef.putFile(Uri.fromFile(localF))
                                                                            .addOnSuccessListener {
                                                                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                                                                    if (continuation.isActive) continuation.resume(uri, null)
                                                                                }.addOnFailureListener { err ->
                                                                                    if (continuation.isActive) continuation.resumeWithException(err)
                                                                                }
                                                                            }.addOnFailureListener { err ->
                                                                                if (continuation.isActive) continuation.resumeWithException(err)
                                                                            }
                                                                    }
                                                                    finalUrl = downloadUri.toString()
                                                                    uploadSucceeded = true
                                                                    break
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                    lastError = e
                                                                }
                                                            }
                                                            if (!uploadSucceeded) {
                                                                Toast.makeText(context, "عذراً، متعذر رفع الملف للسيرفر حالياً. يرجى التأكد من الاتصال بالإنترنت ومحاولة الرفع مجدداً. (${lastError?.localizedMessage ?: ""})", Toast.LENGTH_LONG).show()
                                                                isUploadingToStorage = false
                                                                return@launch
                                                            }
                                                        }
                                                    } else {
                                                        // The local copy is gone/empty — we cannot safely fall back
                                                        // to saving the "file://" URL, since it would only ever
                                                        // resolve on this admin device and never for students.
                                                        Toast.makeText(context, "تعذر العثور على الملف المحلي لإعادة رفعه. يرجى اختيار الملف مجدداً.", Toast.LENGTH_LONG).show()
                                                        isUploadingToStorage = false
                                                        return@launch
                                                    }
                                                    isUploadingToStorage = false
                                                }

                                                // Final safety net: never persist a lecture whose file URL only
                                                // resolves on this device (file:// / content://). If we reach
                                                // here with one of those, the upload above did not truly succeed.
                                                if (finalUrl.startsWith("file://") || finalUrl.startsWith("content://")) {
                                                    Toast.makeText(context, "تعذر رفع الملف إلى السيرفر السحابي. لن يتمكن الطلاب من فتح الملف إذا تم الحفظ الآن — يرجى إعادة المحاولة.", Toast.LENGTH_LONG).show()
                                                    isUploadingToStorage = false
                                                    return@launch
                                                }

                                                if (finalUrl.isNotBlank()) {
                                                    onUploadLecture(
                                                        titleInput,
                                                        subjectInput,
                                                        selectedSemesterInput,
                                                        finalUrl,
                                                        sendNotificationInput,
                                                        selectedFileSize,
                                                        detectedPageCount
                                                    )
                                                    successText = "تم رفع المحاضرة وعرضها لجميع المستخدمين عبر السيرفر بنجاح."
                                                    showSuccessMessage = true
                                                    titleInput = ""
                                                    subjectInput = ""
                                                    fileUrlInput = ""
                                                    selectedFileName = null
                                                    selectedFileSize = null
                                                    uploadStorageStatus = ""
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("admin_upload_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isUploadingToStorage) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري رفع الملف إلى السيرفر...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("رفع المحاضرة الآن", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Subject Folders Management
                    item {
                        var newFolderNameInput by remember { mutableStateOf("") }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "إضافة مجلد مادة دراسية جديد",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = newFolderNameInput,
                                    onValueChange = { newFolderNameInput = it },
                                    label = { Text("اسم المادة / المجلد الجديد *") },
                                    placeholder = { Text("مثال: الصيدلة الفيزياوية") },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = "Folder") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_add_subject_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Button(
                                    onClick = {
                                        if (newFolderNameInput.isNotBlank()) {
                                            onAddSubjectFolder(newFolderNameInput)
                                            successText = "تمت إضافة مجلد مادة (${newFolderNameInput.trim()}) بنجاح!"
                                            showSuccessMessage = true
                                            newFolderNameInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("admin_add_subject_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Add Folder", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إضافة المجلد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "المجلدات والمواد الحالية المتاحة بالسيرفر:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (subjects.isEmpty()) {
                        item {
                            Text("لا توجد مجلدات مواد حتى الآن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(subjects, key = { it.name }) { subj ->
                            val count = lectures.count { it.subject == subj.name }
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                    .testTag("admin_subject_folder_${subj.name}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Subject",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = subj.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$count محاضرة بالداخل",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            val newPinnedState = !subj.isExamPinned
                                            onToggleSubjectExamPinned(subj.name, newPinnedState)
                                            successText = if (newPinnedState) "تم تثبيت مادة (${subj.name}) للامتحان!" else "تم إلغاء تثبيت مادة (${subj.name})"
                                            showSuccessMessage = true
                                        },
                                        modifier = Modifier.testTag("pin_subject_button_${subj.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Pin Subject",
                                            tint = if (subj.isExamPinned) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            onDeleteSubjectFolder(subj.name)
                                            successText = "تم مسح مجلد مادة (${subj.name})!"
                                            showSuccessMessage = true
                                        },
                                        modifier = Modifier.testTag("delete_subject_button_${subj.name}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Folder",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "Announcement",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "إرسال تبليغ رسمي لطلاب المرحلة",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedTextField(
                                    value = announceTitleInput,
                                    onValueChange = { announceTitleInput = it },
                                    label = { Text("عنوان التبليغ *") },
                                    placeholder = { Text("تأجيل موعد القاعة الامتحانية") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = announceMsgInput,
                                    onValueChange = { announceMsgInput = it },
                                    label = { Text("تفاصيل التبليغ *") },
                                    placeholder = { Text("يرجى الحضور في تمام الساعة العاشرة صباحاً...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Button(
                                    onClick = {
                                        if (announceTitleInput.isNotBlank() && announceMsgInput.isNotBlank()) {
                                            onPublishAnnouncement(announceTitleInput, announceMsgInput)
                                            successText = "تم بث التبليغ الرسمي لطلاب المرحلة وإرسال إشعار للنظام بنجاح."
                                            showSuccessMessage = true
                                            announceTitleInput = ""
                                            announceMsgInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("send_announcement_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("بث التبليغ للطلاب", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                3 -> {
                    items(lectures, key = { it.id }) { lecture ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lecture.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${lecture.subject} • ${lecture.stageName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = { onDeleteLecture(lecture) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // Students Server Database View
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = "Database Server",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "قاعدة بيانات سيرفر الطلاب المسجلين",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "يتم تسجيل وحفظ بيانات الحسابات فور تسجيل الدخول وتلقي المحاضرات أولاً بأول",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    if (students.isEmpty()) {
                        item {
                            Text(
                                text = "لا يوجد طلاب مسجلون حالياً بالسيرفر",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(students, key = { it.email }) { student ->
                            val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.getDefault()) }
                            val dateStr = dateFormat.format(Date(student.registrationDate))

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Student",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Email,
                                                contentDescription = "Email",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = student.email,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "تاريخ التسجيل: $dateStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getFileNameAndSizeFromUri(context: Context, uri: Uri): Pair<String, String> {
    var name = "lecture.pdf"
    var sizeStr = "3.2 MB"
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        if (sizeBytes > 0) {
                            val mb = sizeBytes / (1024.0 * 1024.0)
                            sizeStr = String.format(Locale.US, "%.1f MB", mb)
                        }
                    }
                }
            }
        } else {
            uri.path?.let { p ->
                val f = File(p)
                if (f.exists()) {
                    name = f.name
                    val mb = f.length() / (1024.0 * 1024.0)
                    sizeStr = String.format(Locale.US, "%.1f MB", mb)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Pair(name, sizeStr)
}

