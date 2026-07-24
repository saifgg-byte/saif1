package com.example.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pharmacy_user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_EMAIL = "key_user_email"
        private const val KEY_NAME = "key_user_name"
        private const val KEY_STUDENT_ID = "key_student_id"
        private const val KEY_ROLE = "key_user_role"
    }

    fun saveSession(email: String, fullName: String, studentId: String, role: String) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_NAME, fullName.trim())
            .putString(KEY_STUDENT_ID, studentId.trim())
            .putString(KEY_ROLE, role.trim())
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getUserName(): String = prefs.getString(KEY_NAME, "") ?: ""
    fun getStudentId(): String = prefs.getString(KEY_STUDENT_ID, "") ?: ""
    fun getUserRole(): String = prefs.getString(KEY_ROLE, "STUDENT") ?: "STUDENT"

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
