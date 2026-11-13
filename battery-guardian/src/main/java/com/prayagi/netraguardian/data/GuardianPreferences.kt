package com.prayagi.netraguardian.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.guardianStore by preferencesDataStore(name = "guardian_preferences")

data class GuardianSettings(
    val voiceEnabled: Boolean = true,
    val nightSilentEnabled: Boolean = true,
    val language: String = "en",
    val theme: String = "system"
)

@Singleton
class GuardianPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val voiceKey = booleanPreferencesKey("voice_enabled")
    private val silentKey = booleanPreferencesKey("night_silent_enabled")
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    val settings: Flow<GuardianSettings> = context.guardianStore.data.map { p ->
        GuardianSettings(p[voiceKey] ?: true, p[silentKey] ?: true, p[languageKey] ?: "en", p[themeKey] ?: "system")
    }
    suspend fun setVoiceEnabled(enabled: Boolean) { context.guardianStore.edit { it[voiceKey] = enabled } }
    suspend fun setNightSilentEnabled(enabled: Boolean) { context.guardianStore.edit { it[silentKey] = enabled } }
    suspend fun setLanguage(language: String) { context.guardianStore.edit { it[languageKey] = language } }
    suspend fun setTheme(theme: String) { context.guardianStore.edit { it[themeKey] = theme } }
}
