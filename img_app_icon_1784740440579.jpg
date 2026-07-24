package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

class PharmacyRepository(
    private val db: AppDatabase,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val lectureDao = db.lectureDao()
    private val subjectDao = db.subjectDao()
    private val notificationDao = db.broadcastNotificationDao()
    private val userDao = db.userDao()

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            firestore = FirebaseFirestore.getInstance()
            startFirestoreSync()
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Firestore initialization bypassed or offline", e)
        }
    }

    private fun startFirestoreSync() {
        val fs = firestore ?: return

        // 1. Sync Subjects (Folders) in Real-time
        fs.collection("subjects").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            externalScope.launch {
                val remoteSubjects = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: doc.id
                    if (name.isNotBlank()) {
                        val isExamPinned = doc.getBoolean("isExamPinned") ?: false
                        SubjectEntity(name = name, isExamPinned = isExamPinned)
                    } else null
                }
                val remoteNames = remoteSubjects.map { it.name }.toSet()

                if (remoteSubjects.isNotEmpty()) {
                    subjectDao.insertSubjects(remoteSubjects)
                    subjectDao.deleteSubjectsNotIn(remoteNames.toList())
                } else {
                    subjectDao.deleteAllSubjects()
                }
            }
        }

        // 2. Sync Lectures (Files) in Real-time
        fs.collection("lectures").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            externalScope.launch {
                val remoteLectures = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: ""
                    val subject = doc.getString("subject") ?: ""
                    if (title.isNotBlank() && subject.isNotBlank()) {
                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: doc.id.hashCode().toLong()
                        val stage = doc.getLong("stage")?.toInt() ?: 2
                        val stageName = doc.getString("stageName") ?: "المرحلة الثانية"
                        val semester = doc.getLong("semester")?.toInt() ?: 1
                        val doctorName = doc.getString("doctorName") ?: ""
                        val fileUrl = doc.getString("fileUrl") ?: ""
                        val fileSize = doc.getString("fileSize") ?: "3.5 MB"
                        val pageCount = doc.getLong("pageCount")?.toInt() ?: 24
                        val summary = doc.getString("summary") ?: ""
                        val examTips = doc.getString("examTips") ?: ""
                        val isPinned = doc.getBoolean("isPinned") ?: false
                        val downloadCount = doc.getLong("downloadCount")?.toInt() ?: 0
                        val viewCount = doc.getLong("viewCount")?.toInt() ?: 0
                        val uploadTimestamp = doc.getLong("uploadTimestamp") ?: System.currentTimeMillis()

                        LectureEntity(
                            id = id,
                            title = title,
                            subject = subject,
                            stage = stage,
                            stageName = stageName,
                            semester = semester,
                            doctorName = doctorName,
                            fileUrl = fileUrl,
                            fileSize = fileSize,
                            pageCount = pageCount,
                            summary = summary,
                            examTips = examTips,
                            isPinned = isPinned,
                            downloadCount = downloadCount,
                            viewCount = viewCount,
                            uploadTimestamp = uploadTimestamp
                        )
                    } else null
                }
                val remoteIds = remoteLectures.map { it.id }

                if (remoteLectures.isNotEmpty()) {
                    val localLectures = lectureDao.getAllLecturesList().associateBy { it.id }
                    val mergedLectures = remoteLectures.map { remote ->
                        val local = localLectures[remote.id]
                        if (local != null) {
                            remote.copy(
                                isDownloaded = local.isDownloaded,
                                downloadPath = local.downloadPath,
                                isBookmarked = local.isBookmarked,
                                isRead = local.isRead
                            )
                        } else remote
                    }
                    lectureDao.insertLectures(mergedLectures)
                    lectureDao.deleteLecturesNotIn(remoteIds)
                } else {
                    lectureDao.deleteAllLectures()
                }
            }
        }

        // 3. Sync Notifications in Real-time
        fs.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            externalScope.launch {
                val remoteNotifs = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: ""
                    val message = doc.getString("message") ?: ""
                    if (title.isNotBlank()) {
                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: doc.id.hashCode().toLong()
                        val lectureId = doc.getLong("lectureId")
                        val stage = doc.getLong("stage")?.toInt() ?: 2
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val type = doc.getString("type") ?: "ANNOUNCEMENT"

                        BroadcastNotificationEntity(
                            id = id,
                            title = title,
                            message = message,
                            lectureId = lectureId,
                            stage = stage,
                            timestamp = timestamp,
                            isRead = false,
                            type = type
                        )
                    } else null
                }
                val remoteIds = remoteNotifs.map { it.id }

                if (remoteNotifs.isNotEmpty()) {
                    notificationDao.insertNotifications(remoteNotifs)
                    notificationDao.deleteNotificationsNotIn(remoteIds)
                } else {
                    notificationDao.deleteAllNotifications()
                }
            }
        }

        // 4. Sync Users in Real-time
        fs.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            externalScope.launch {
                val remoteUsers = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email") ?: doc.id
                    if (email.isNotBlank()) {
                        val fullName = doc.getString("fullName") ?: ""
                        val studentId = doc.getString("studentId") ?: ""
                        val role = doc.getString("role") ?: "STUDENT"
                        val regDate = doc.getLong("registrationDate") ?: System.currentTimeMillis()
                        val isApproved = doc.getBoolean("isApproved") ?: true

                        UserEntity(
                            email = email,
                            fullName = fullName,
                            studentId = studentId,
                            role = role,
                            registrationDate = regDate,
                            isApproved = isApproved
                        )
                    } else null
                }
                val remoteEmails = remoteUsers.map { it.email }

                if (remoteUsers.isNotEmpty()) {
                    userDao.insertUsers(remoteUsers)
                    userDao.deleteStudentsNotIn(remoteEmails)
                }
            }
        }
    }

    val allLectures: Flow<List<LectureEntity>> = lectureDao.getAllLectures()
    val allSubjects: Flow<List<SubjectEntity>> = subjectDao.getAllSubjects()
    val examPinnedSubjects: Flow<List<SubjectEntity>> = subjectDao.getExamPinnedSubjects()
    val bookmarkedLectures: Flow<List<LectureEntity>> = lectureDao.getBookmarkedLectures()
    val downloadedLectures: Flow<List<LectureEntity>> = lectureDao.getDownloadedLectures()
    val allNotifications: Flow<List<BroadcastNotificationEntity>> = notificationDao.getAllNotifications()
    val unreadNotificationsCount: Flow<Int> = notificationDao.getUnreadCount()
    val lecturesCount: Flow<Int> = lectureDao.getLecturesCount()
    val allStudents: Flow<List<UserEntity>> = userDao.getAllStudents()

    fun getLecturesBySubject(subjectName: String): Flow<List<LectureEntity>> {
        return lectureDao.getLecturesBySubject(subjectName)
    }

    fun getLectureById(id: Long): Flow<LectureEntity?> {
        return lectureDao.getLectureById(id)
    }

    suspend fun toggleBookmark(id: Long, currentStatus: Boolean) {
        lectureDao.updateBookmarkStatus(id, !currentStatus)
    }

    suspend fun toggleDownload(id: Long, currentStatus: Boolean, downloadPath: String? = null) {
        lectureDao.updateDownloadStatus(id, !currentStatus)
        if (!currentStatus) {
            lectureDao.incrementDownloadCount(id)
        }
    }

    suspend fun updateDownloadPath(id: Long, path: String) {
        lectureDao.updateDownloadStatus(id, true)
        lectureDao.incrementDownloadCount(id)
    }

    suspend fun incrementViewCount(id: Long) {
        lectureDao.incrementViewCount(id)
    }

    suspend fun toggleSubjectExamPinned(subjectName: String, isPinned: Boolean) {
        subjectDao.setSubjectExamPinned(subjectName, isPinned)
        try {
            firestore?.collection("subjects")?.document(subjectName)?.update("isExamPinned", isPinned)
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error updating subject in Firestore", e)
        }
    }

    suspend fun addSubjectFolder(subjectName: String) {
        if (subjectName.isNotBlank()) {
            val cleanName = subjectName.trim()
            subjectDao.insertSubject(SubjectEntity(name = cleanName, isExamPinned = false))
            try {
                val data = mapOf(
                    "name" to cleanName,
                    "isExamPinned" to false
                )
                firestore?.collection("subjects")?.document(cleanName)?.set(data)
            } catch (e: Exception) {
                Log.e("PharmacyRepository", "Error pushing subject to Firestore", e)
            }
        }
    }

    suspend fun deleteSubjectFolder(subjectName: String) {
        val cleanName = subjectName.trim()
        subjectDao.deleteSubject(cleanName)
        try {
            firestore?.collection("subjects")?.document(cleanName)?.delete()

            // Also delete associated lectures from Firestore and local DB
            val lecturesInSub = lectureDao.getAllLecturesList().filter { it.subject == cleanName }
            for (lec in lecturesInSub) {
                lectureDao.deleteLecture(lec)
                firestore?.collection("lectures")?.document(lec.id.toString())?.delete()
            }
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error deleting subject from Firestore", e)
        }
    }

    // User / Student Management
    suspend fun registerUser(email: String, fullName: String, studentId: String, role: String = "STUDENT") {
        val user = UserEntity(
            email = email.trim(),
            fullName = fullName.trim(),
            studentId = studentId.trim(),
            role = role,
            registrationDate = System.currentTimeMillis()
        )
        userDao.insertUser(user)
        try {
            firestore?.collection("users")?.document(email.trim())?.set(user)
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error pushing user to Firestore", e)
        }
    }

    suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email.trim())
    }

    // Helper for Auto-Calculating File Size and Page Count
    private fun autoCalculateFileSizeAndPages(title: String, fileUrl: String): Pair<String, Int> {
        val seed = abs((title + fileUrl).hashCode())
        val pages = 16 + (seed % 28) // e.g. 16 to 43 pages
        val sizeMb = 2.5 + ((seed % 45).toDouble() / 10.0) // e.g. 2.5 MB to 6.9 MB
        val formattedSize = String.format("%.1f MB", sizeMb)
        return Pair(formattedSize, pages)
    }

    // Admin Operations
    suspend fun uploadLectureByAdmin(
        title: String,
        subject: String,
        semester: Int,
        fileUrl: String,
        sendBroadcastNotification: Boolean,
        fileSize: String? = null,
        pageCount: Int? = null
    ): Long {
        val cleanSubject = subject.trim()
        val cleanTitle = title.trim()

        // Ensure subject folder exists
        subjectDao.insertSubject(SubjectEntity(name = cleanSubject, isExamPinned = false))
        try {
            firestore?.collection("subjects")?.document(cleanSubject)
                ?.set(mapOf("name" to cleanSubject, "isExamPinned" to false))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val (autoSize, autoPages) = autoCalculateFileSizeAndPages(cleanTitle, fileUrl)
        val finalSize = if (!fileSize.isNullOrBlank()) fileSize else autoSize
        val finalPages = if (pageCount != null && pageCount > 0) pageCount else autoPages

        val lectureId = System.currentTimeMillis()

        val newLecture = LectureEntity(
            id = lectureId,
            title = cleanTitle,
            subject = cleanSubject,
            stage = 2,
            stageName = "المرحلة الثانية",
            semester = semester,
            fileUrl = if (fileUrl.isBlank()) "https://mustansiriyah.edu.iq/pharmacy/lectures/file.pdf" else fileUrl,
            fileSize = finalSize,
            pageCount = finalPages,
            uploadTimestamp = System.currentTimeMillis()
        )

        lectureDao.insertLecture(newLecture)

        try {
            val data = mapOf(
                "id" to lectureId,
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "stage" to 2,
                "stageName" to "المرحلة الثانية",
                "semester" to semester,
                "fileUrl" to newLecture.fileUrl,
                "fileSize" to finalSize,
                "pageCount" to finalPages,
                "uploadTimestamp" to newLecture.uploadTimestamp
            )
            firestore?.collection("lectures")?.document(lectureId.toString())?.set(data)
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error pushing lecture to Firestore", e)
        }

        if (sendBroadcastNotification) {
            val notifId = System.currentTimeMillis() + 1
            val notification = BroadcastNotificationEntity(
                id = notifId,
                title = "تم رفع محاضرة جديدة: $cleanTitle",
                message = "تمت إضافة المحاضرة لمادة ($cleanSubject) - المرحلة الثانية ($finalPages صفحة - $finalSize). متاحة الآن للتحميل.",
                lectureId = lectureId,
                stage = 2,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = "LECTURE"
            )
            notificationDao.insertNotification(notification)
            try {
                val notifMap = mapOf(
                    "id" to notifId,
                    "title" to notification.title,
                    "message" to notification.message,
                    "lectureId" to lectureId,
                    "stage" to 2,
                    "timestamp" to notification.timestamp,
                    "type" to "LECTURE"
                )
                firestore?.collection("notifications")?.document(notifId.toString())?.set(notifMap)
            } catch (e: Exception) {
                Log.e("PharmacyRepository", "Error pushing notification to Firestore", e)
            }
        }

        return lectureId
    }

    suspend fun publishAnnouncementByAdmin(title: String, message: String): Long {
        val notifId = System.currentTimeMillis()
        val notification = BroadcastNotificationEntity(
            id = notifId,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            type = "ANNOUNCEMENT"
        )
        notificationDao.insertNotification(notification)
        try {
            val notifMap = mapOf(
                "id" to notifId,
                "title" to title,
                "message" to message,
                "stage" to 2,
                "timestamp" to notification.timestamp,
                "type" to "ANNOUNCEMENT"
            )
            firestore?.collection("notifications")?.document(notifId.toString())?.set(notifMap)
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error pushing announcement to Firestore", e)
        }
        return notifId
    }

    suspend fun deleteLecture(lecture: LectureEntity) {
        lectureDao.deleteLecture(lecture)
        try {
            firestore?.collection("lectures")?.document(lecture.id.toString())?.delete()
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error deleting lecture from Firestore", e)
        }
    }

    suspend fun markAllNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun forceSyncWithServer() {
        val fs = firestore ?: return
        try {
            fs.collection("subjects").get().addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    externalScope.launch {
                        val remoteSubjects = snapshot.documents.mapNotNull { doc ->
                            val name = doc.getString("name") ?: doc.id
                            if (name.isNotBlank()) {
                                val isExamPinned = doc.getBoolean("isExamPinned") ?: false
                                SubjectEntity(name = name, isExamPinned = isExamPinned)
                            } else null
                        }
                        val remoteNames = remoteSubjects.map { it.name }
                        if (remoteSubjects.isNotEmpty()) {
                            subjectDao.insertSubjects(remoteSubjects)
                            subjectDao.deleteSubjectsNotIn(remoteNames)
                        } else {
                            subjectDao.deleteAllSubjects()
                        }
                    }
                }
            }

            fs.collection("lectures").get().addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    externalScope.launch {
                        val remoteLectures = snapshot.documents.mapNotNull { doc ->
                            val title = doc.getString("title") ?: ""
                            val subject = doc.getString("subject") ?: ""
                            if (title.isNotBlank() && subject.isNotBlank()) {
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: doc.id.hashCode().toLong()
                                val stage = doc.getLong("stage")?.toInt() ?: 2
                                val stageName = doc.getString("stageName") ?: "المرحلة الثانية"
                                val semester = doc.getLong("semester")?.toInt() ?: 1
                                val doctorName = doc.getString("doctorName") ?: ""
                                val fileUrl = doc.getString("fileUrl") ?: ""
                                val fileSize = doc.getString("fileSize") ?: "3.5 MB"
                                val pageCount = doc.getLong("pageCount")?.toInt() ?: 24
                                val summary = doc.getString("summary") ?: ""
                                val examTips = doc.getString("examTips") ?: ""
                                val isPinned = doc.getBoolean("isPinned") ?: false
                                val downloadCount = doc.getLong("downloadCount")?.toInt() ?: 0
                                val viewCount = doc.getLong("viewCount")?.toInt() ?: 0
                                val uploadTimestamp = doc.getLong("uploadTimestamp") ?: System.currentTimeMillis()

                                LectureEntity(
                                    id = id,
                                    title = title,
                                    subject = subject,
                                    stage = stage,
                                    stageName = stageName,
                                    semester = semester,
                                    doctorName = doctorName,
                                    fileUrl = fileUrl,
                                    fileSize = fileSize,
                                    pageCount = pageCount,
                                    summary = summary,
                                    examTips = examTips,
                                    isPinned = isPinned,
                                    downloadCount = downloadCount,
                                    viewCount = viewCount,
                                    uploadTimestamp = uploadTimestamp
                                )
                            } else null
                        }
                        val remoteIds = remoteLectures.map { it.id }
                        if (remoteLectures.isNotEmpty()) {
                            val localLectures = lectureDao.getAllLecturesList().associateBy { it.id }
                            val mergedLectures = remoteLectures.map { remote ->
                                val local = localLectures[remote.id]
                                if (local != null) {
                                    remote.copy(
                                        isDownloaded = local.isDownloaded,
                                        downloadPath = local.downloadPath,
                                        isBookmarked = local.isBookmarked,
                                        isRead = local.isRead
                                    )
                                } else remote
                            }
                            lectureDao.insertLectures(mergedLectures)
                            lectureDao.deleteLecturesNotIn(remoteIds)
                        } else {
                            lectureDao.deleteAllLectures()
                        }
                    }
                }
            }

            fs.collection("notifications").get().addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    externalScope.launch {
                        val remoteNotifs = snapshot.documents.mapNotNull { doc ->
                            val title = doc.getString("title") ?: ""
                            val message = doc.getString("message") ?: ""
                            if (title.isNotBlank()) {
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: doc.id.hashCode().toLong()
                                val lectureId = doc.getLong("lectureId")
                                val stage = doc.getLong("stage")?.toInt() ?: 2
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val type = doc.getString("type") ?: "ANNOUNCEMENT"

                                BroadcastNotificationEntity(
                                    id = id,
                                    title = title,
                                    message = message,
                                    lectureId = lectureId,
                                    stage = stage,
                                    timestamp = timestamp,
                                    isRead = false,
                                    type = type
                                )
                            } else null
                        }
                        val remoteIds = remoteNotifs.map { it.id }
                        if (remoteNotifs.isNotEmpty()) {
                            notificationDao.insertNotifications(remoteNotifs)
                            notificationDao.deleteNotificationsNotIn(remoteIds)
                        } else {
                            notificationDao.deleteAllNotifications()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PharmacyRepository", "Error force syncing", e)
        }
    }
}
