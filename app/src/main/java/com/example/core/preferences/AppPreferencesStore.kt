package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "minimart_pro_prefs")

class AppPreferencesStore(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language_code")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyActiveBranchId = stringPreferencesKey("active_branch_id")

    val languageFlow: Flow<AppLanguage> = context.appDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    val themeModeFlow: Flow<ThemeMode> = context.appDataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[keyTheme])
    }

    val activeBranchIdFlow: Flow<String?> = context.appDataStore.data.map { prefs ->
        prefs[keyActiveBranchId]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appDataStore.edit { it[keyLanguage] = language.code }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.appDataStore.edit { it[keyTheme] = themeMode.key }
    }

    suspend fun setActiveBranchId(branchId: String?) {
        context.appDataStore.edit { prefs ->
            if (branchId.isNullOrBlank()) {
                prefs.remove(keyActiveBranchId)
            } else {
                prefs[keyActiveBranchId] = branchId
            }
        }
    }
}
