package com.prayagi.netraguardian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prayagi.netraguardian.data.BatteryRepository
import com.prayagi.netraguardian.data.GuardianPreferences
import com.prayagi.netraguardian.data.GuardianSettings
import com.prayagi.netraguardian.domain.BatteryReading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val repository: BatteryRepository,
    preferences: GuardianPreferences
) : ViewModel() {
    private val _reading = MutableStateFlow<BatteryReading?>(null)
    val reading: StateFlow<BatteryReading?> = _reading.asStateFlow()
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardianSettings())
    init {
        viewModelScope.launch {
            repository.observeBattery().collectLatest { value ->
                _reading.value = value
                repository.saveSnapshot(value)
            }
        }
    }
    fun setVoice(enabled: Boolean) = viewModelScope.launch { preferences.setVoiceEnabled(enabled) }
    fun setNightSilent(enabled: Boolean) = viewModelScope.launch { preferences.setNightSilentEnabled(enabled) }
    fun setLanguage(language: String) = viewModelScope.launch { preferences.setLanguage(language) }
    fun clearLocalHistory() = viewModelScope.launch { repository.clearHistory() }
}
