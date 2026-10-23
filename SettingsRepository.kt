package com.lockin.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lockInDataStore by preferencesDataStore(
    name = "lockin_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class AppSettings(
    val selectedPackages: Set<String> = emptySet(),
    val durationMinutes: Int = 45,
    val dailyGoalMinutes: Int = 120,
    val activeStartedAt: Long = 0L,
    val activeUntil: Long = 0L,
    val activePlannedMinutes: Int = 0,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStartMinute: Int = 22 * 60,
    val bedtimeEndMinute: Int = 7 * 60,
    val bedtimePackages: Set<String> = emptySet(),
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0
) {
    val focusActive: Boolean get() = activeUntil > System.currentTimeMillis()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val selectedPackages = stringSetPreferencesKey("focus_packages")
        val duration = intPreferencesKey("focus_duration")
        val goal = intPreferencesKey("daily_goal")
        val startedAt = longPreferencesKey("focus_started_at")
        val activeUntil = longPreferencesKey("focus_active_until")
        val planned = intPreferencesKey("focus_planned")
        val bedtimeEnabled = booleanPreferencesKey("bedtime_enabled")
        val bedtimeStart = intPreferencesKey("bedtime_start")
        val bedtimeEnd = intPreferencesKey("bedtime_end")
        val bedtimePackages = stringSetPreferencesKey("bedtime_packages")
        val alarmEnabled = booleanPreferencesKey("alarm_enabled")
        val alarmHour = intPreferencesKey("alarm_hour")
        val alarmMinute = intPreferencesKey("alarm_minute")
    }

    val settings: Flow<AppSettings> = context.lockInDataStore.data.map { p ->
        AppSettings(
            selectedPackages = p[Keys.selectedPackages] ?: emptySet(),
            durationMinutes = p[Keys.duration] ?: 45,
            dailyGoalMinutes = p[Keys.goal] ?: 120,
            activeStartedAt = p[Keys.startedAt] ?: 0L,
            activeUntil = p[Keys.activeUntil] ?: 0L,
            activePlannedMinutes = p[Keys.planned] ?: 0,
            bedtimeEnabled = p[Keys.bedtimeEnabled] ?: false,
            bedtimeStartMinute = p[Keys.bedtimeStart] ?: 22 * 60,
            bedtimeEndMinute = p[Keys.bedtimeEnd] ?: 7 * 60,
            bedtimePackages = p[Keys.bedtimePackages] ?: emptySet(),
            alarmEnabled = p[Keys.alarmEnabled] ?: false,
            alarmHour = p[Keys.alarmHour] ?: 7,
            alarmMinute = p[Keys.alarmMinute] ?: 0
        )
    }

    suspend fun update(block: suspend androidx.datastore.preferences.core.MutablePreferences.() -> Unit) {
        context.lockInDataStore.edit { block(it) }
    }

    suspend fun startFocus(minutes: Int, packages: Set<String>, now: Long = System.currentTimeMillis()): Boolean {
        var started = false
        context.lockInDataStore.edit {
            if ((it[Keys.activeUntil] ?: 0L) > 0L) return@edit
            it[Keys.selectedPackages] = packages
            it[Keys.duration] = minutes
            it[Keys.startedAt] = now
            it[Keys.activeUntil] = now + minutes * 60_000L
            it[Keys.planned] = minutes
            started = true
        }
        return started
    }

    suspend fun clearFocus() {
        update {
            this[Keys.startedAt] = 0L
            this[Keys.activeUntil] = 0L
            this[Keys.planned] = 0
        }
    }

    suspend fun saveSelected(packages: Set<String>) {
        update { this[Keys.selectedPackages] = packages }
    }

    suspend fun saveBedtime(enabled: Boolean, start: Int, end: Int, packages: Set<String>) {
        update {
            this[Keys.bedtimeEnabled] = enabled
            this[Keys.bedtimeStart] = start.coerceIn(0, 1439)
            this[Keys.bedtimeEnd] = end.coerceIn(0, 1439)
            this[Keys.bedtimePackages] = packages
        }
    }

    suspend fun saveGoal(minutes: Int) {
        update { this[Keys.goal] = minutes.coerceIn(15, 1440) }
    }

    suspend fun saveAlarm(enabled: Boolean, hour: Int, minute: Int) {
        update {
            this[Keys.alarmEnabled] = enabled
            this[Keys.alarmHour] = hour.coerceIn(0, 23)
            this[Keys.alarmMinute] = minute.coerceIn(0, 59)
        }
    }
}
