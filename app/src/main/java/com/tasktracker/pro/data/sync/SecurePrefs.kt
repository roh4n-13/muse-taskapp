package com.tasktracker.pro.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Abstraction over sync-related persistent state so SyncEngine is unit-testable
 * without Android's encrypted storage.
 */
interface SyncPrefs {
    var webAppUrl: String
    var token: String
    var syncEnabled: Boolean
    var lastSyncTime: String
    var lastSyncError: String
    var lastSyncedIds: Set<String>
    val isConfigured: Boolean
    fun clearConnection()
}

/**
 * Encrypted storage for the Google Sheets connection (Web App URL + secret token).
 * Nothing else sensitive lives here; ordinary settings use SettingsStore.
 */
class SecurePrefs(context: Context) : SyncPrefs {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "sheets_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override var webAppUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_URL, v.trim()).apply()

    override var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

    /** True only after the user explicitly enables sync (never implied by saved values). */
    override var syncEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    override var lastSyncTime: String
        get() = prefs.getString(KEY_LAST_SYNC, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_SYNC, v).apply()

    override var lastSyncError: String
        get() = prefs.getString(KEY_LAST_ERROR, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_ERROR, v).apply()

    /** IDs seen in the last successful snapshot — used for safe deletion reconciliation. */
    override var lastSyncedIds: Set<String>
        get() = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_IDS, v).apply()

    override val isConfigured: Boolean get() = webAppUrl.isNotBlank() && token.isNotBlank()

    override fun clearConnection() {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_TOKEN)
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_IDS)
            .apply()
    }

    companion object {
        private const val KEY_URL = "web_app_url"
        private const val KEY_TOKEN = "secret_token"
        private const val KEY_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC = "last_sync_time"
        private const val KEY_LAST_ERROR = "last_sync_error"
        private const val KEY_IDS = "last_synced_ids"
    }
}
