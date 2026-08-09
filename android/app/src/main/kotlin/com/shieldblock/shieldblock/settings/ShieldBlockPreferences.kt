package com.shieldblock.shieldblock.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.shieldBlockDataStore by preferencesDataStore(name = "shieldblock_settings")

class ShieldBlockPreferences(private val context: Context) {
    fun snapshot(): SettingsSnapshot = runBlocking {
        val values = context.shieldBlockDataStore.data.first()
        SettingsSnapshot(
            historyLimit = values[HISTORY_LIMIT]?.coerceIn(100, 50_000) ?: DEFAULT_HISTORY_LIMIT,
            debugMode = values[DEBUG_MODE] ?: false,
        )
    }

    fun update(historyLimit: Int? = null, debugMode: Boolean? = null): SettingsSnapshot = runBlocking {
        val values = context.shieldBlockDataStore.edit { values ->
            historyLimit?.let { values[HISTORY_LIMIT] = it.coerceIn(100, 50_000) }
            debugMode?.let { values[DEBUG_MODE] = it }
        }
        SettingsSnapshot(
            historyLimit = values[HISTORY_LIMIT]?.coerceIn(100, 50_000) ?: DEFAULT_HISTORY_LIMIT,
            debugMode = values[DEBUG_MODE] ?: false,
        )
    }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 5_000
        private val HISTORY_LIMIT = intPreferencesKey("history_limit")
        private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }
}

data class SettingsSnapshot(
    val historyLimit: Int,
    val debugMode: Boolean,
)
