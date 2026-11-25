package com.savyor.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedSettingsManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                "savyor_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("EncryptedSettings", "EncryptedSharedPreferences initialization failed, falling back to standard prefs", e)
            context.applicationContext.getSharedPreferences("savyor_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_UPDATE_YTDLP = "auto_update_ytdlp"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
        private const val KEY_LAST_YTDLP_UPDATE_CHECK_MS = "last_ytdlp_update_check_ms"
        private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
        private const val KEY_LIBRARY_VIEW_MODE = "library_view_mode"
        private const val KEY_LIBRARY_SORT_ORDER = "library_sort_order"
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var isWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var autoUpdateYtDlp: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_YTDLP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE_YTDLP, value).apply()

    var isFirstRunDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, value).apply()

    var lastYtDlpUpdateCheckMs: Long
        get() = prefs.getLong(KEY_LAST_YTDLP_UPDATE_CHECK_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE_CHECK_MS, value).apply()

    var lastBackupMs: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_MS, value).apply()

    var libraryViewMode: String
        get() = prefs.getString(KEY_LIBRARY_VIEW_MODE, "GRID") ?: "GRID"
        set(value) = prefs.edit().putString(KEY_LIBRARY_VIEW_MODE, value).apply()

    var librarySortOrder: String
        get() = prefs.getString(KEY_LIBRARY_SORT_ORDER, "NEWEST") ?: "NEWEST"
        set(value) = prefs.edit().putString(KEY_LIBRARY_SORT_ORDER, value).apply()

    fun isApiKeyConfigured(): Boolean = geminiApiKey.isNotBlank()

    fun getMaskedApiKey(): String {
        val key = geminiApiKey
        return when {
            key.isBlank() -> "Not configured"
            key.length <= 8 -> "••••••••"
            else -> "${key.take(6)}••••••••${key.takeLast(4)}"
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
