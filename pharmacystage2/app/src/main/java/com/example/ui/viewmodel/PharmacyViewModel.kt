package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BroadcastNotificationEntity
import com.example.data.LectureEntity
import com.example.data.PharmacyRepository
import com.example.data.SubjectEntity
import com.example.data.UserEntity
import com.example.util.PdfHelper
import com.example.util.SessionManager
import com.example.util.SystemNotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PharmacyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PharmacyRepository
    private val sessionManager = SessionManager(application)

    // Theme Mode: Dark vs Light
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    // Auth & User State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginErrorMessage = MutableStateFlow<String?>(null)
    val loginErrorMessage: StateFlow<String?> = _loginErrorMessage.asStateFlow()

    // Admin Mode state
    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        repository = PharmacyRepository(db)

        viewModelScope.launch {
            repository.allLectures.collect { lectures ->
                val pendingId = _pendingLectureId.value
                if (pendingId != null && lectures.isNotEmpty()) {
                    lectures.find { it.id == pendingId }?.let { target ->
                        selectLecture(target)
                        _pendingLectureId.value = null
                    }
                }
            }
        }

        // Restore Session synchronously first so UI opens immediately to main app if logged in
        if (sessionManager.isLoggedIn()) {
            val email = sessionManager.getUserEmail() ?: ""
            val role = sessionManager.getUserRole()
            val name = sessionManager.getUserName()
            val studentId = sessionManager.getStudentId()

            if (email.isNotEmpty()) {
                val cachedUser = UserEntity(
                    email = email,
                    fullName = name.ifBlank { "طالب دواء" },
                    studentId = studentId,
                    role = role
                )
                _currentUser.value = cachedUser
                _isAdminMode.value = (role == "ADMIN")
                _isLoggedIn.value = true

                // Sync with DB in background
                viewModelScope.launch {
                    val dbUser = repository.getUserByEmail(email)
                    if (dbUser != null) {
                        _currentUser.value = dbUser
                        _isAdminMode.value = (dbUser.role == "ADMIN")
                    } else {
                        repository.registerUser(email, cachedUser.fullName, cachedUser.studentId, cachedUser.role)
                    }
                }
            }
        }
    }

    fun toggleAdminMode() {
        if (_currentUser.value?.role == "ADMIN") {
            _isAdminMode.value = !_isAdminMode.value
        }
    }

    // Login logic
    fun login(
        email: String,
        password: String,
        studentName: String = "",
        studentId: String = "",
        isSignUpMode: Boolean = false
    ) {
        _loginErrorMessage.value = null
        val trimmedEmail = email.trim()

        if (trimmedEmail.isEmpty()) {
            _loginErrorMessage.value = "يرجى إدخال البريد الإلكتروني"
            return
        }

        // Admin Credentials check
        if (trimmedEmail.equals("saifgames.2006.2020@gmail.com", ignoreCase = true)) {
            if (password == "Sss12345ssS!") {
                val adminUser = UserEntity(
                    email = trimmedEmail,
                    fullName = "مدير النظام (Admin)",
                    studentId = "ADMIN-01",
                    role = "ADMIN"
                )
                _currentUser.value = adminUser
                _isAdminMode.value = true
                _isLoggedIn.value = true
                sessionManager.saveSession(trimmedEmail, adminUser.fullName, adminUser.studentId, "ADMIN")
                _selectedTab.value = "HOME"
            } else {
                _loginErrorMessage.value = "كلمة المرور غير صحيحة لحساب الأدمن"
            }
            return
        }

        if (password.isBlank()) {
            _loginErrorMessage.value = "يرجى إدخال كلمة المرور"
            return
        }

        // Student Login / Registration
        viewModelScope.launch {
            val existing = repository.getUserByEmail(trimmedEmail)

            if (!isSignUpMode) {
                // SIGN IN MODE
                if (existing == null) {
                    _loginErrorMessage.value = "هذا الحساب غير مسجل في قاعدة البيانات. يرجى التوجه لإنشاء حساب جديد أولاً."
                    return@launch
                }
                _currentUser.value = existing
                _isAdminMode.value = false
                _isLoggedIn.value = true
                sessionManager.saveSession(existing.email, existing.fullName, existing.studentId, existing.role)
                _selectedTab.value = "HOME"
            } else {
                // SIGN UP MODE
                if (existing != null) {
                    _loginErrorMessage.value = "هذا البريد الإلكتروني مسجل بالفعل. يرجى اختيار تسجيل الدخول."
                    return@launch
                }
                if (studentName.isBlank()) {
                    _loginErrorMessage.value = "يرجى إدخال الاسم الرباعي للطالب لإنشاء الحساب"
                    return@launch
                }

                val generatedId = if (studentId.isNotBlank()) studentId else "PHARM2-${(100..999).random()}"
                repository.registerUser(trimmedEmail, studentName, generatedId, "STUDENT")

                val newUser = UserEntity(
                    email = trimmedEmail,
                    fullName = studentName.trim(),
                    studentId = generatedId,
                    role = "STUDENT"
                )
                _currentUser.value = newUser
                _isAdminMode.value = false
                _isLoggedIn.value = true
                sessionManager.saveSession(newUser.email, newUser.fullName, newUser.studentId, "STUDENT")
                _selectedTab.value = "HOME"
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _currentUser.value = null
        _isLoggedIn.value = false
        _isAdminMode.value = false
        _selectedTab.value = "HOME"
    }

    // Navigation & Screen selection
    private val _selectedTab = MutableStateFlow("HOME") // HOME, NOTIFICATIONS, ADMIN, STUDENTS
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    // Subject Filter
    private val _selectedSubject = MutableStateFlow<String?>(null) // null = All
    val selectedSubject: StateFlow<String?> = _selectedSubject.asStateFlow()

    fun selectSubject(subjectName: String?) {
        _selectedSubject.value = subjectName
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _selectedSemester = MutableStateFlow(0) // 0 = All, 1, 2
    val selectedSemester: StateFlow<Int> = _selectedSemester.asStateFlow()

    fun setSelectedSemester(semester: Int) {
        _selectedSemester.value = semester
    }

    private val _onlyDownloaded = MutableStateFlow(false)
    val onlyDownloaded: StateFlow<Boolean> = _onlyDownloaded.asStateFlow()

    fun toggleOnlyDownloaded() {
        _onlyDownloaded.value = !_onlyDownloaded.value
    }

    // Flows from Repository
    val allSubjects: StateFlow<List<SubjectEntity>> = repository.allSubjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val examPinnedSubjects: StateFlow<List<SubjectEntity>> = repository.examPinnedSubjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allLectures: StateFlow<List<LectureEntity>> = repository.allLectures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allStudents: StateFlow<List<UserEntity>> = repository.allStudents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredLectures: StateFlow<List<LectureEntity>> = combine(
        allLectures,
        _selectedSubject,
        _searchQuery,
        _selectedSemester,
        _onlyDownloaded
    ) { lectures, subject, query, semester, downloadedOnly ->
        lectures.filter { lecture ->
            val matchesSubject = subject == null || lecture.subject == subject
            val matchesQuery = query.isBlank() ||
                    lecture.title.contains(query, ignoreCase = true) ||
                    lecture.subject.contains(query, ignoreCase = true)
            val matchesSemester = semester == 0 || lecture.semester == semester
            val matchesDownload = !downloadedOnly || lecture.isDownloaded
            matchesSubject && matchesQuery && matchesSemester && matchesDownload
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notifications: StateFlow<List<BroadcastNotificationEntity>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unreadCount: StateFlow<Int> = repository.unreadNotificationsCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Broadcast Alert Trigger for In-App Banner
    private val _latestBroadcastAlert = MutableSharedFlow<BroadcastNotificationEntity>()
    val latestBroadcastAlert: SharedFlow<BroadcastNotificationEntity> = _latestBroadcastAlert.asSharedFlow()

    // Selected Lecture for PDF Viewer Detail Screen
    private val _selectedLecture = MutableStateFlow<LectureEntity?>(null)
    val selectedLecture: StateFlow<LectureEntity?> = _selectedLecture.asStateFlow()

    private val _pendingLectureId = MutableStateFlow<Long?>(null)

    fun selectLectureById(id: Long) {
        _pendingLectureId.value = id
        val target = allLectures.value.find { it.id == id }
        if (target != null) {
            selectLecture(target)
            _pendingLectureId.value = null
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun reloadServerData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.forceSyncWithServer()
            kotlinx.coroutines.delay(600)
            _isRefreshing.value = false
            onComplete?.invoke()
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun selectLecture(lecture: LectureEntity?) {
        _selectedLecture.value = lecture
        lecture?.let {
            viewModelScope.launch {
                repository.incrementViewCount(it.id)
            }
        }
    }

    fun toggleBookmark(lecture: LectureEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(lecture.id, lecture.isBookmarked)
            _selectedLecture.value = _selectedLecture.value?.let {
                if (it.id == lecture.id) it.copy(isBookmarked = !lecture.isBookmarked) else it
            }
        }
    }

    fun downloadLectureFile(context: Context, lecture: LectureEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val pdfFile = PdfHelper.getOrDownloadPdfFile(context, lecture)
                val publicPath = PdfHelper.savePdfToPublicDeviceStorage(context, pdfFile, lecture.title)
                repository.toggleDownload(lecture.id, false, publicPath)
                _selectedLecture.value = _selectedLecture.value?.let {
                    if (it.id == lecture.id) it.copy(isDownloaded = true, downloadCount = it.downloadCount + 1, downloadPath = publicPath) else it
                }

                // Trigger Device System Notification
                SystemNotificationHelper.sendSystemNotification(
                    context = context,
                    notificationId = (lecture.id.hashCode() and 0x7FFFFFFF),
                    title = "تم تنزيل المحاضرة بنجاح 📄",
                    message = "تم حفظ محاضرة '${lecture.title}' بنجاح في جهازك (مجلد Downloads)."
                )

                onResult(true, "تم تنزيل المحاضرة وحفظها في جهازك (مجلد Downloads) بنجاح!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "حدث خطأ أثناء تنزيل المحاضرة")
            }
        }
    }

    fun toggleDownload(lecture: LectureEntity) {
        viewModelScope.launch {
            repository.toggleDownload(lecture.id, lecture.isDownloaded)
            _selectedLecture.value = _selectedLecture.value?.let {
                if (it.id == lecture.id) it.copy(isDownloaded = !lecture.isDownloaded, downloadCount = it.downloadCount + if (!lecture.isDownloaded) 1 else 0) else it
            }
        }
    }

    fun toggleSubjectExamPinned(subjectName: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.toggleSubjectExamPinned(subjectName, isPinned)
        }
    }

    fun addSubjectFolder(subjectName: String, onSuccess: () -> Unit) {
        if (subjectName.isBlank()) return
        viewModelScope.launch {
            repository.addSubjectFolder(subjectName)
            onSuccess()
        }
    }

    fun deleteSubjectFolder(subjectName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSubjectFolder(subjectName)
            if (_selectedSubject.value == subjectName) {
                _selectedSubject.value = null
            }
            onSuccess()
        }
    }

    // Admin Operations
    fun uploadLectureByAdmin(
        title: String,
        subject: String,
        semester: Int,
        fileUrl: String,
        sendBroadcastNotification: Boolean,
        fileSize: String? = null,
        pageCount: Int? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val newId = repository.uploadLectureByAdmin(
                title = title,
                subject = subject,
                semester = semester,
                fileUrl = fileUrl,
                sendBroadcastNotification = sendBroadcastNotification,
                fileSize = fileSize,
                pageCount = pageCount
            )

            val alertNotif = BroadcastNotificationEntity(
                id = System.currentTimeMillis(),
                title = "تم رفع محاضرة جديدة: $title",
                message = "تمت إضافة المحاضرة لمادة ($subject) - المرحلة الثانية. متاحة الآن للتحميل والقراءة.",
                lectureId = newId,
                stage = 2,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = "LECTURE"
            )
            _latestBroadcastAlert.emit(alertNotif)

            onSuccess()
        }
    }

    fun publishAnnouncement(title: String, message: String, onSuccess: () -> Unit) {
        if (title.isBlank() || message.isBlank()) return
        viewModelScope.launch {
            val notifId = repository.publishAnnouncementByAdmin(title, message)
            val alertNotif = BroadcastNotificationEntity(
                id = notifId,
                title = title,
                message = message,
                stage = 2,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = "ANNOUNCEMENT"
            )
            _latestBroadcastAlert.emit(alertNotif)
            onSuccess()
        }
    }

    fun deleteLecture(lecture: LectureEntity) {
        viewModelScope.launch {
            repository.deleteLecture(lecture)
            if (_selectedLecture.value?.id == lecture.id) {
                _selectedLecture.value = null
            }
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }
}
