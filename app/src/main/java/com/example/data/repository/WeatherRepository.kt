package com.example.data.repository

import com.example.data.api.GeminiWeatherService
import com.example.data.database.SavedCityDao
import com.example.data.database.SavedAlertDao
import com.example.data.model.SavedCity
import com.example.data.model.SavedAlert
import com.example.data.model.WeatherReport
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class WeatherRepository(
    private val savedCityDao: SavedCityDao,
    private val savedAlertDao: SavedAlertDao
) {
    val allSavedCities: Flow<List<SavedCity>> = savedCityDao.getAllCities()
    val allAlerts: Flow<List<SavedAlert>> = savedAlertDao.getAllAlerts()

    suspend fun getWeatherReport(cityName: String): WeatherReport {
        val report = GeminiWeatherService.fetchWeatherReport(cityName)
        
        // Automatically cache or update the city state in our database upon successful retrieval
        val savedCity = SavedCity(
            name = formatCityName(cityName),
            temp = report.temperature,
            condition = report.condition,
            isFavorite = false, // Keep previous favourite value if merging, parsed by viewModel
            timestamp = System.currentTimeMillis()
        )
        savedCityDao.insertCity(savedCity)
        
        return report
    }

    suspend fun saveCachedCity(city: SavedCity) {
        savedCityDao.insertCity(city)
    }

    suspend fun deleteCity(cityName: String) {
        savedCityDao.deleteCity(formatCityName(cityName))
    }

    suspend fun updateFavorite(cityName: String, isFav: Boolean) {
        savedCityDao.updateFavorite(formatCityName(cityName), isFav)
    }

    suspend fun saveAlert(alert: SavedAlert) {
        savedAlertDao.insertAlert(alert)
    }

    suspend fun deleteAlert(id: Long) {
        savedAlertDao.deleteAlert(id)
    }

    suspend fun clearAllAlerts() {
        savedAlertDao.deleteAllAlerts()
    }

    private fun formatCityName(name: String): String {
        return name.trim().split(" ").joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
