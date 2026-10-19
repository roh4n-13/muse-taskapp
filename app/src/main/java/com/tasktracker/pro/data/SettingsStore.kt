package com.tasktracker.pro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Ordinary (non-secret) app settings. Sheets credentials live in SecurePrefs. */
class SettingsStore(context: Context) {
    private val ds = context.applicationContext.dataStore

    val theme: Flow<ThemeMode> = ds.data.map {
        runCatching { ThemeMode.valueOf(it[KEY_THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val reminderEnabled: Flow<Boolean> = ds.data.map { it[KEY_REMINDER] ?: false }
    val reminderHour: Flow<Int> = ds.data.map { it[KEY_HOUR] ?: 9 }
    val reminderMinute: Flow<Int> = ds.data.map { it[KEY_MINUTE] ?: 0 }

    suspend fun setTheme(mode: ThemeMode) = ds.edit { it[KEY_THEME] = mode.name }
    suspend fun setReminderEnabled(enabled: Boolean) = ds.edit { it[KEY_REMINDER] = enabled }
    suspend fun setReminderTime(hour: Int, minute: Int) = ds.edit {
        it[KEY_HOUR] = hour.coerceIn(0, 23)
        it[KEY_MINUTE] = minute.coerceIn(0, 59)
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_REMINDER = booleanPreferencesKey("reminder_enabled")
        private val KEY_HOUR = intPreferencesKey("reminder_hour")
        private val KEY_MINUTE = intPreferencesKey("reminder_minute")
    }
}
