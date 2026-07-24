package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LectureDao {
    @Query("SELECT * FROM lectures ORDER BY uploadTimestamp DESC")
    fun getAllLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures")
    suspend fun getAllLecturesList(): List<LectureEntity>

    @Query("SELECT * FROM lectures WHERE subject = :subject ORDER BY uploadTimestamp DESC")
    fun getLecturesBySubject(subject: String): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE isBookmarked = 1 ORDER BY uploadTimestamp DESC")
    fun getBookmarkedLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE isDownloaded = 1 ORDER BY uploadTimestamp DESC")
    fun getDownloadedLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE id = :id")
    fun getLectureById(id: Long): Flow<LectureEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectures(lectures: List<LectureEntity>)

    @Update
    suspend fun updateLecture(lecture: LectureEntity)

    @Delete
    suspend fun deleteLecture(lecture: LectureEntity)

    @Query("DELETE FROM lectures WHERE id = :id")
    suspend fun deleteLectureById(id: Long)

    @Query("DELETE FROM lectures WHERE id NOT IN (:ids)")
    suspend fun deleteLecturesNotIn(ids: List<Long>)

    @Query("DELETE FROM lectures")
    suspend fun deleteAllLectures()

    @Query("UPDATE lectures SET isDownloaded = :downloaded WHERE id = :id")
    suspend fun updateDownloadStatus(id: Long, downloaded: Boolean)

    @Query("UPDATE lectures SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, bookmarked: Boolean)

    @Query("UPDATE lectures SET viewCount = viewCount + 1 WHERE id = :id")
    suspend fun incrementViewCount(id: Long)

    @Query("UPDATE lectures SET downloadCount = downloadCount + 1 WHERE id = :id")
    suspend fun incrementDownloadCount(id: Long)

    @Query("SELECT COUNT(*) FROM lectures")
    fun getLecturesCount(): Flow<Int>
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY isExamPinned DESC, name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects")
    suspend fun getAllSubjectsList(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE isExamPinned = 1")
    fun getExamPinnedSubjects(): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Query("UPDATE subjects SET isExamPinned = :isPinned WHERE name = :subjectName")
    suspend fun setSubjectExamPinned(subjectName: String, isPinned: Boolean)

    @Query("DELETE FROM subjects WHERE name = :subjectName")
    suspend fun deleteSubject(subjectName: String)

    @Query("DELETE FROM subjects WHERE name NOT IN (:names)")
    suspend fun deleteSubjectsNotIn(names: List<String>)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()
}

@Dao
interface BroadcastNotificationDao {
    @Query("SELECT * FROM broadcast_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<BroadcastNotificationEntity>>

    @Query("SELECT * FROM broadcast_notifications")
    suspend fun getAllNotificationsList(): List<BroadcastNotificationEntity>

    @Query("SELECT COUNT(*) FROM broadcast_notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: BroadcastNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<BroadcastNotificationEntity>)

    @Query("UPDATE broadcast_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM broadcast_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM broadcast_notifications WHERE id NOT IN (:ids)")
    suspend fun deleteNotificationsNotIn(ids: List<Long>)

    @Query("DELETE FROM broadcast_notifications")
    suspend fun deleteAllNotifications()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY registrationDate DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'STUDENT' ORDER BY registrationDate DESC")
    fun getAllStudents(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("DELETE FROM users WHERE email = :email")
    suspend fun deleteUser(email: String)

    @Query("DELETE FROM users WHERE email NOT IN (:emails) AND role != 'ADMIN'")
    suspend fun deleteStudentsNotIn(emails: List<String>)
}
