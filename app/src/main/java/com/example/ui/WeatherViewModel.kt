package com.example.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedAlert
import com.example.data.model.SavedCity
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val report: WeatherReport) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val appContext: Context
) : ViewModel() {
    private val TAG = "WeatherViewModel"
    private val CHANNEL_ID = "severe_weather_alerts"

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val savedCities: StateFlow<List<SavedCity>> = repository.allSavedCities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val severeAlerts: StateFlow<List<SavedAlert>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Initial default city load
    init {
        searchCity("San Francisco")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchCity(cityName: String) {
        if (cityName.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                Log.d(TAG, "viewModel: Searching city $cityName")
                val report = repository.getWeatherReport(cityName)
                _uiState.value = WeatherUiState.Success(report)
                
                // If the dynamic report has an active severe weather alert,
                // automatically cache it in the database alerts hub and trigger a notification
                if (report.isAlertActive && report.activeAlertTitle != null) {
                    val alert = SavedAlert(
                        cityName = report.cityName,
                        title = report.activeAlertTitle,
                        description = report.activeAlertDesc ?: "Severe alert active.",
                        severity = "Severe"
                    )
                    repository.saveAlert(alert)
                    sendStatusBarNotification(
                        appContext,
                        report.activeAlertTitle,
                        report.activeAlertDesc ?: "Severe alert warning active."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Failed to fetch weather: ${e.localizedMessage}")
            }
        }
    }

    fun toggleFavorite(cityName: String, isNowFav: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(cityName, isNowFav)
            
            // If currently viewing active state, update in Success state also
            val current = _uiState.value
            if (current is WeatherUiState.Success && current.report.cityName.equals(cityName, ignoreCase = true)) {
                // Ensure room remains in sync
                val updatedCity = SavedCity(
                    name = current.report.cityName,
                    temp = current.report.temperature,
                    condition = current.report.condition,
                    isFavorite = isNowFav,
                    timestamp = System.currentTimeMillis()
                )
                repository.saveCachedCity(updatedCity)
            }
        }
    }

    fun deleteCity(cityName: String) {
        viewModelScope.launch {
            repository.deleteCity(cityName)
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            repository.deleteAlert(id)
        }
    }

    fun clearAllAlerts() {
        viewModelScope.launch {
            repository.clearAllAlerts()
        }
    }

    /**
     * Simulation Trigger: Generates a severe weather alert with notifications which are posted directly
     * to the user's system Status Bar.
     */
    fun simulateSevereWeatherWarning(context: Context, specificCity: String? = null) {
        viewModelScope.launch {
            val targetCity = specificCity ?: when (val state = _uiState.value) {
                is WeatherUiState.Success -> state.report.cityName
                else -> "Your Area"
            }

            // Create notification descriptors beautifully
            val alerts = listOf(
                Pair("Tornado Danger Warning", "Tornado shelter orders active. Take cover in reinforced underground spaces immediately."),
                Pair("Extreme Ice Blizzard Warning", "Rapid snow drifts accumulating with visibility down to zero feet. Avoid travel."),
                Pair("High Flash Flood Danger", "Sudden excessive rainwater accumulation. Move towards higher elevated ground immediately."),
                Pair("Extreme UV/Heat Alert", "Forecast maximum temperatures ready to exceed 106°F. Keep adequately hydrated. Avoid solar exposure.")
            )
            val randomChoice = alerts.random()
            val alertTitle = "${randomChoice.first} - $targetCity"
            val alertDesc = randomChoice.second

            // 1. Save alert into local room database for persistent record
            val alert = SavedAlert(
                cityName = targetCity,
                title = alertTitle,
                description = alertDesc,
                severity = "Extreme"
            )
            repository.saveAlert(alert)

            // 2. Dispatch a hardware Status Bar Notification
            sendStatusBarNotification(context, alertTitle, alertDesc)
        }
    }

    private fun sendStatusBarNotification(context: Context, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel on Android Oreo (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Severe Weather Broadcasts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts regarding immediate severe weather shifts"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Generate the native Status Bar Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning) // Using system warning drawable
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Natively dispatched StatusBar Notification: $title")
    }
}

class WeatherViewModelFactory(
    private val repository: WeatherRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
