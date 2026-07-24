package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BroadcastNotificationEntity
import com.example.ui.components.BroadcastBanner
import com.example.ui.components.PharmacyBottomBar
import com.example.ui.components.PharmacyTopAppBar
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LectureDetailScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.MustansiriyahTheme
import com.example.ui.viewmodel.PharmacyViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PharmacyViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission granted for notifications
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MustansiriyahTheme(darkTheme = isDarkMode) {
                PharmacyApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val lectureId = intent?.getLongExtra("LECTURE_ID", -1L) ?: -1L
        if (lectureId != -1L) {
            viewModel.selectLectureById(lectureId)
        }
    }
}

@Composable
fun PharmacyApp(viewModel: PharmacyViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val loginError by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val subjects by viewModel.allSubjects.collectAsStateWithLifecycle()
    val examPinnedSubjects by viewModel.examPinnedSubjects.collectAsStateWithLifecycle()
    val selectedSubject by viewModel.selectedSubject.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSemester by viewModel.selectedSemester.collectAsStateWithLifecycle()
    val onlyDownloaded by viewModel.onlyDownloaded.collectAsStateWithLifecycle()

    val allLectures by viewModel.allLectures.collectAsStateWithLifecycle()
    val filteredLectures by viewModel.filteredLectures.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val selectedLecture by viewModel.selectedLecture.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var activeBroadcastAlert by remember { mutableStateOf<BroadcastNotificationEntity?>(null) }

    // Listen for instant broadcast alerts & trigger System Status Bar Notification
    LaunchedEffect(Unit) {
        viewModel.latestBroadcastAlert.collect { notif ->
            activeBroadcastAlert = notif
            com.example.util.SystemNotificationHelper.sendSystemNotification(
                context = context,
                notificationId = (notif.id.hashCode() and 0x7FFFFFFF),
                title = notif.title,
                message = notif.message,
                lectureId = notif.lectureId
            )
        }
    }

    if (!isLoggedIn) {
        LoginScreen(
            errorMessage = loginError,
            onLogin = { email, password, name, studentId, isSignUp ->
                viewModel.login(email, password, name, studentId, isSignUp)
            }
        )
    } else {
        Scaffold(
            topBar = {
                if (selectedLecture == null) {
                    PharmacyTopAppBar(
                        isDarkMode = isDarkMode,
                        isAdminMode = isAdminMode,
                        currentUser = currentUser,
                        unreadNotificationsCount = unreadCount,
                        onThemeToggle = { viewModel.toggleDarkMode() },
                        onAdminToggle = {
                            viewModel.toggleAdminMode()
                            if (!isAdminMode) {
                                viewModel.setSelectedTab("ADMIN")
                            } else if (selectedTab == "ADMIN") {
                                viewModel.setSelectedTab("HOME")
                            }
                        },
                        onNotificationClick = {
                            viewModel.setSelectedTab("NOTIFICATIONS")
                        },
                        onLogoutClick = {
                            viewModel.logout()
                        },
                        onReloadClick = {
                            viewModel.reloadServerData()
                        }
                    )
                }
            },
            bottomBar = {
                if (selectedLecture == null) {
                    PharmacyBottomBar(
                        currentTab = selectedTab,
                        isAdminMode = isAdminMode,
                        unreadNotificationsCount = unreadCount,
                        onTabSelected = { tab ->
                            viewModel.setSelectedTab(tab)
                            if (tab == "NOTIFICATIONS") {
                                viewModel.markAllNotificationsRead()
                            }
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedLecture != null) {
                    val currentLecture = selectedLecture!!
                    LectureDetailScreen(
                        lecture = currentLecture,
                        onBack = { viewModel.selectLecture(null) },
                        onToggleBookmark = { viewModel.toggleBookmark(currentLecture) },
                        onToggleDownload = { lecture ->
                            viewModel.downloadLectureFile(context, lecture) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { width -> width / 12 })
                                .togetherWith(
                                    fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { width -> -width / 12 }
                                )
                        },
                        label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            "HOME" -> HomeScreen(
                                subjects = subjects,
                                examPinnedSubjects = examPinnedSubjects,
                                lectures = filteredLectures,
                                selectedSubject = selectedSubject,
                                searchQuery = searchQuery,
                                selectedSemester = selectedSemester,
                                onlyDownloaded = onlyDownloaded,
                                onSubjectSelect = { viewModel.selectSubject(it) },
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onSemesterSelect = { viewModel.setSelectedSemester(it) },
                                onToggleOnlyDownloaded = { viewModel.toggleOnlyDownloaded() },
                                onLectureClick = { viewModel.selectLecture(it) },
                                onToggleBookmark = { viewModel.toggleBookmark(it) },
                                onToggleDownload = { lecture ->
                                    viewModel.downloadLectureFile(context, lecture) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isRefreshing = isRefreshing,
                                onReloadClick = { viewModel.reloadServerData() }
                            )

                            "NOTIFICATIONS" -> NotificationsScreen(
                                notifications = notifications,
                                onMarkAllRead = { viewModel.markNotificationsRead() },
                                onNotificationClick = { notif ->
                                    if (notif.lectureId != null) {
                                        viewModel.selectLectureById(notif.lectureId)
                                    }
                                }
                            )

                            "PROFILE" -> ProfileScreen(
                                currentUser = currentUser,
                                downloadedCount = allLectures.count { it.isDownloaded },
                                bookmarkedCount = allLectures.count { it.isBookmarked },
                                isDarkMode = isDarkMode,
                                isAdminMode = isAdminMode,
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                onNavigateToDownloads = {
                                    viewModel.toggleOnlyDownloaded()
                                    viewModel.setSelectedTab("HOME")
                                },
                                onNavigateToAdmin = {
                                    viewModel.setSelectedTab("ADMIN")
                                },
                                onLogoutClick = {
                                    viewModel.logout()
                                }
                            )

                            "ADMIN" -> AdminScreen(
                                subjects = subjects,
                                lectures = allLectures,
                                students = allStudents,
                                onUploadLecture = { title, subject, semester, url, alert, fSize, pCount ->
                                    viewModel.uploadLectureByAdmin(title, subject, semester, url, alert, fSize, pCount) {}
                                },
                                onToggleSubjectExamPinned = { subjectName, isPinned ->
                                    viewModel.toggleSubjectExamPinned(subjectName, isPinned)
                                },
                                onPublishAnnouncement = { title, msg ->
                                    viewModel.publishAnnouncement(title, msg) {}
                                },
                                onDeleteLecture = { viewModel.deleteLecture(it) },
                                onAddSubjectFolder = { folderName ->
                                    viewModel.addSubjectFolder(folderName) {}
                                },
                                onDeleteSubjectFolder = { folderName ->
                                    viewModel.deleteSubjectFolder(folderName) {}
                                }
                            )

                            else -> HomeScreen(
                                subjects = subjects,
                                examPinnedSubjects = examPinnedSubjects,
                                lectures = filteredLectures,
                                selectedSubject = selectedSubject,
                                searchQuery = searchQuery,
                                selectedSemester = selectedSemester,
                                onlyDownloaded = onlyDownloaded,
                                onSubjectSelect = { viewModel.selectSubject(it) },
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onSemesterSelect = { viewModel.setSelectedSemester(it) },
                                onToggleOnlyDownloaded = { viewModel.toggleOnlyDownloaded() },
                                onLectureClick = { viewModel.selectLecture(it) },
                                onToggleBookmark = { viewModel.toggleBookmark(it) },
                                onToggleDownload = { lecture ->
                                    viewModel.downloadLectureFile(context, lecture) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Floating Instant Broadcast Banner Alert
                BroadcastBanner(
                    notification = activeBroadcastAlert,
                    onDismiss = { activeBroadcastAlert = null },
                    onViewClick = { notif ->
                        activeBroadcastAlert = null
                        notif.lectureId?.let { id ->
                            val target = allLectures.find { it.id == id }
                            if (target != null) {
                                viewModel.selectLecture(target)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
