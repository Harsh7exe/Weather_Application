package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DailyForecast
import com.example.data.model.HourlyForecast
import com.example.data.model.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object GeminiWeatherService {
    private const val TAG = "GeminiWeatherService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    private const val MODEL = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch weather data for a city.
     * Uses Gemini API directly via REST if an API key is available, or falls back to smart local simulation.
     */
    suspend fun fetchWeatherReport(cityName: String): WeatherReport = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                Log.d(TAG, "Attempting to fetch with Gemini AI API for city: $cityName")
                val responseJson = requestGeminiWeather(cityName, apiKey)
                if (responseJson != null) {
                    val report = parseWeatherReportFromJson(responseJson, cityName)
                    Log.d(TAG, "Successfully generated AI weather report for: $cityName")
                    return@withContext report
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini API call failed, falling back to simulated data: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "No valid Gemini API key found, using local high-fidelity simulator.")
        }
        
        // Simulating highly realistic deterministic data
        return@withContext generateSimulatedWeather(cityName)
    }

    private fun requestGeminiWeather(cityName: String, apiKey: String): String? {
        val prompt = """
            Generate a highly realistic, up-to-date, detailed weather report and forecast for the city of "$cityName".
            You must reply only with a valid JSON object matching the following structure exactly. Do not wrap in markdown tags like ```json or include any text before or after the JSON:
            {
              "cityName": "$cityName",
              "temperature": 68.0,
              "condition": "Partly Cloudy",
              "highTemp": 72.0,
              "lowTemp": 58.0,
              "humidityPercent": 64,
              "windSpeedMph": 12.0,
              "windDirection": "WSW",
              "uvIndex": 5,
              "pressureMb": 1013,
              "visibilityMiles": 10.0,
              "isAlertActive": true,
              "activeAlertTitle": "Severe Wind Alert",
              "activeAlertDesc": "High winds are expected from 2 PM to 8 PM with gusts up to 45 mph. Secure lightweight outdoor objects.",
              "runSuitabilityScore": 8,
              "runSuitabilityText": "Perfect for a light jog. Good humidity and clear routes.",
              "hourlyForecast": [
                {"timeLabel": "10 AM", "temp": 64.0, "condition": "Sunny", "precipitationChance": 5, "windSpeedMph": 6.5},
                {"timeLabel": "12 PM", "temp": 68.0, "condition": "Sunny", "precipitationChance": 10, "windSpeedMph": 7.0},
                {"timeLabel": "2 PM", "temp": 71.0, "condition": "Partly Cloudy", "precipitationChance": 15, "windSpeedMph": 11.5},
                {"timeLabel": "4 PM", "temp": 72.0, "condition": "Partly Cloudy", "precipitationChance": 20, "windSpeedMph": 12.0},
                {"timeLabel": "6 PM", "temp": 68.0, "condition": "Partly Cloudy", "precipitationChance": 35, "windSpeedMph": 9.5},
                {"timeLabel": "8 PM", "temp": 62.0, "condition": "Cloudy", "precipitationChance": 50, "windSpeedMph": 8.0}
              ],
              "dailyForecast": [
                {"dayLabel": "Today", "condition": "Partly Cloudy", "lowTemp": 58.0, "highTemp": 72.0, "precipitationChance": 20},
                {"dayLabel": "Tomorrow", "condition": "Sunny", "lowTemp": 59.0, "highTemp": 74.0, "precipitationChance": 5},
                {"dayLabel": "Wed", "condition": "Rainy", "lowTemp": 55.0, "highTemp": 68.0, "precipitationChance": 85},
                {"dayLabel": "Thu", "condition": "Partly Cloudy", "lowTemp": 56.0, "highTemp": 70.0, "precipitationChance": 15},
                {"dayLabel": "Fri", "condition": "Sunny", "lowTemp": 57.0, "highTemp": 73.0, "precipitationChance": 10}
              ],
              "aiInsight": "AI Recommendation: Excellent morning for outdoor activities, but secure any loose patio furniture. The high winds peaking late afternoon might make cycling harder."
            }
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            // Set system instructions for strict JSON compliance and creative richness
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are a weather microservice that returns rich weather telemetry and forecasts structured exactly in pure JSON format.")
                    })
                })
            })
        }

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val url = "$BASE_URL/v1beta/models/$MODEL:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Request failed with code: ${response.code}")
                return null
            }
            val resBody = response.body?.string() ?: return null
            val responseObj = JSONObject(resBody)
            val candidates = responseObj.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidateObj = candidates.getJSONObject(0)
                val contentObj = candidateObj.getJSONObject("content")
                val parts = contentObj.getJSONArray("parts")
                if (parts.length() > 0) {
                    var text = parts.getJSONObject(0).getString("text")
                    // Clean markdown markers if Gemini outputs them anyway
                    text = text.trim()
                    if (text.startsWith("```json")) {
                        text = text.substringAfter("```json").substringBeforeLast("```")
                    } else if (text.startsWith("```")) {
                        text = text.substringAfter("```").substringBeforeLast("```")
                    }
                    return text.trim()
                }
            }
        }
        return null
    }

    private fun parseWeatherReportFromJson(jsonStr: String, defaultCity: String): WeatherReport {
        val root = JSONObject(jsonStr)
        val cityName = root.optString("cityName", defaultCity)
        val temp = root.optDouble("temperature", 68.0)
        val condition = root.optString("condition", "Partly Cloudy")
        val high = root.optDouble("highTemp", temp + 4.0)
        val low = root.optDouble("lowTemp", temp - 10.0)
        val humidity = root.optInt("humidityPercent", 60)
        val windSpeed = root.optDouble("windSpeedMph", 10.0)
        val windDir = root.optString("windDirection", "W")
        val uv = root.optInt("uvIndex", 4)
        val pressure = root.optInt("pressureMb", 1015)
        val visibility = root.optDouble("visibilityMiles", 9.0)
        val isAlertActive = root.optBoolean("isAlertActive", false)
        val alertTitle = if (isAlertActive) root.optString("activeAlertTitle", "Severe Wind Alert") else null
        val alertDesc = if (isAlertActive) root.optString("activeAlertDesc", "Caution expected due to weather anomaly.") else null
        val runScore = root.optInt("runSuitabilityScore", 8)
        val runText = root.optString("runSuitabilityText", "Suitable for outdoor exercises.")
        val aiInsight = root.optString("aiInsight", "AI Weather Report is loaded successfully for $cityName.")

        val hourlyList = mutableListOf<HourlyForecast>()
        val hourlyArr = root.optJSONArray("hourlyForecast")
        if (hourlyArr != null) {
            for (i in 0 until hourlyArr.length()) {
                val obj = hourlyArr.getJSONObject(i)
                hourlyList.add(HourlyForecast(
                    timeLabel = obj.optString("timeLabel", "${i + 8} AM"),
                    temp = obj.optDouble("temp", temp),
                    condition = obj.optString("condition", condition),
                    precipitationChance = obj.optInt("precipitationChance", 10),
                    windSpeedMph = obj.optDouble("windSpeedMph", windSpeed)
                ))
            }
        } else {
            // Placeholder fallback
            hourlyList.addAll(listOf(
                HourlyForecast("8 AM", temp - 2, condition, 10, windSpeed),
                HourlyForecast("11 AM", temp, condition, 15, windSpeed),
                HourlyForecast("2 PM", temp + 2, condition, 20, windSpeed),
                HourlyForecast("5 PM", temp + 1, condition, 35, windSpeed),
                HourlyForecast("8 PM", temp - 3, condition, 10, windSpeed)
            ))
        }

        val dailyList = mutableListOf<DailyForecast>()
        val dailyArr = root.optJSONArray("dailyForecast")
        if (dailyArr != null) {
            for (i in 0 until dailyArr.length()) {
                val obj = dailyArr.getJSONObject(i)
                dailyList.add(DailyForecast(
                    dayLabel = obj.optString("dayLabel", "Day $i"),
                    condition = obj.optString("condition", condition),
                    lowTemp = obj.optDouble("lowTemp", low),
                    highTemp = obj.optDouble("highTemp", high),
                    precipitationChance = obj.optInt("precipitationChance", 20)
                ))
            }
        } else {
            dailyList.addAll(listOf(
                DailyForecast("Today", condition, low, high, 20),
                DailyForecast("Tomorrow", "Sunny", low + 1, high + 2, 5),
                DailyForecast("Sat", "Partly Cloudy", low - 1, high, 15)
            ))
        }

        return WeatherReport(
            cityName = cityName,
            temperature = temp,
            condition = condition,
            highTemp = high,
            lowTemp = low,
            humidityPercent = humidity,
            windSpeedMph = windSpeed,
            windDirection = windDir,
            uvIndex = uv,
            pressureMb = pressure,
            visibilityMiles = visibility,
            isAlertActive = isAlertActive,
            activeAlertTitle = alertTitle,
            activeAlertDesc = alertDesc,
            runSuitabilityScore = runScore,
            runSuitabilityText = runText,
            hourlyForecast = hourlyList,
            dailyForecast = dailyList,
            aiInsight = aiInsight
        )
    }

    /**
     * Generates extremely realistic weather conditions deterministically based on city name.
     */
    fun generateSimulatedWeather(cityName: String): WeatherReport {
        val sanitized = cityName.trim().lowercase(Locale.ROOT)
        
        // Base temperatures and weather profiles for popular settings
        val temp: Double
        val condition: String
        val isAlertActive: Boolean
        val alertTitle: String?
        val alertDesc: String?
        val lowTemp: Double
        val highTemp: Double
        val humidity: Int
        val windSpeed: Double
        val windDir: String
        val runScore: Int
        val runText: String
        val aiInsight: String

        when {
            sanitized.contains("san francisco") -> {
                temp = 62.0
                condition = "Partly Cloudy"
                isAlertActive = true
                alertTitle = "Severe Wind Alert"
                alertDesc = "High winds are expected across the Bay Area with gust peaks over 45 mph. Light yard objects may drift."
                lowTemp = 53.0
                highTemp = 69.0
                humidity = 78
                windSpeed = 16.5
                windDir = "WSW"
                runScore = 7
                runText = "Perfect for jogging, but watch out for misty breezes!"
                aiInsight = "Microclimate Advisor: Standard marine layers are building up. We advise windbreakers for shoreline cycling. Conditions are moderate overall."
            }
            sanitized.contains("phoenix") || sanitized.contains("las vegas") || sanitized.contains("dubai") || sanitized.contains("desert")-> {
                temp = 104.0
                condition = "Sunny"
                isAlertActive = true
                alertTitle = "Extreme Heat Advisory"
                alertDesc = "Relentless daytime heat is forecast to exceed dangerous levels. Hydrate frequently and stay indoors."
                lowTemp = 82.0
                highTemp = 108.0
                humidity = 12
                windSpeed = 8.0
                windDir = "NE"
                runScore = 2
                runText = "Strenuous solar rays! Run indoors today."
                aiInsight = "Safe Fitness Alert: The UV Index is at an extreme critical level (11+). Outdoor activities should be strictly suspended between 10 AM and 5 PM. Keep pets protected."
            }
            sanitized.contains("mumbai") || sanitized.contains("singapore") || sanitized.contains("bangkok") || sanitized.contains("tropical") -> {
                temp = 86.0
                condition = "Tropical Rain"
                isAlertActive = true
                alertTitle = "Heavy Monsoon Warning"
                alertDesc = "Heavy downpours expected within localized districts with localized urban water-logging. Travel warnings active."
                lowTemp = 78.0
                highTemp = 91.0
                humidity = 92
                windSpeed = 14.0
                windDir = "SW"
                runScore = 3
                runText = "High humidity and rain. Outdoor tracks are soggy."
                aiInsight = "Monsoon Safety Check: Heavy tropical rainfall peaks mid-afternoon. If traveling, expect heavy delays. Run indoors or choose cardiorespiratory training today."
            }
            sanitized.contains("london") || sanitized.contains("seattle") || sanitized.contains("vancouver") || sanitized.contains("rain") -> {
                temp = 54.0
                condition = "Heavy Rain"
                isAlertActive = false
                alertTitle = null
                alertDesc = null
                lowTemp = 48.0
                highTemp = 58.0
                humidity = 88
                windSpeed = 11.0
                windDir = "SW"
                runScore = 5
                runText = "Slippery roads. Excellent for dynamic pacing indoor treadmills."
                aiInsight = "Rainy Day Recommendation: Bring a full-sized umbrella. For running, swap mud-heavy trails for asphalt paths with wet-grip shoes."
            }
            sanitized.contains("tokyo") || sanitized.contains("seoul") -> {
                temp = 65.0
                condition = "Sunny"
                isAlertActive = false
                alertTitle = null
                alertDesc = null
                lowTemp = 52.0
                highTemp = 72.0
                humidity = 48
                windSpeed = 6.0
                windDir = "NNE"
                runScore = 10
                runText = "Flawless! Ideal temperature, negligible winds."
                aiInsight = "Outdoor Explorer recommendation: Today is a pristine, picture-perfect 10/10 day. Ideal for marathon training, picnics, or scenic trail hiking in regional parks!"
            }
            else -> {
                // Deterministic generation based on string lengths and hash codes
                val hashValue = cityName.hashCode()
                val isWarm = hashValue % 2 == 0
                temp = if (isWarm) (68.0 + (hashValue % 15)) else (42.0 + (hashValue % 20))
                condition = when (hashValue % 4) {
                    0 -> "Sunny"
                    1 -> "Partly Cloudy"
                    2 -> "Cloudy"
                    else -> "Rainy"
                }
                isAlertActive = (hashValue % 7 == 0)
                alertTitle = if (isAlertActive) "Localized High Winds Advisory" else null
                alertDesc = if (isAlertActive) "Localized weather streams might create minor turbulence. Take normal precautions." else null
                lowTemp = temp - (6 + (hashValue % 6))
                highTemp = temp + (4 + (hashValue % 8))
                humidity = 35 + (hashValue % 55)
                windSpeed = 4.0 + (hashValue % 14)
                windDir = when (hashValue % 4) {
                    0 -> "N"
                    1 -> "E"
                    2 -> "S"
                    else -> "W"
                }
                runScore = if (temp > 85 || temp < 45 || condition == "Rainy") 4 else 8
                runText = if (runScore >= 8) "Perfect weather score. Highly suitable for cardio!" else "Sub-optimal temps. Proceed with moderate wear."
                aiInsight = "AI Forecast Insights: A standard micro-profile is generated for $cityName. Winds are moderate. Dressing in mild sportswear layer sets is recommended."
            }
        }

        // Generate hourly forecast based on temp for next 24-48 hours (e.g. 24 entries of 2-hour increments)
        val hourly = (0..23).map { i ->
            val hourCount = i * 2
            val timeLabel = when (i) {
                0 -> "Now"
                else -> "+$hourCount hrs"
            }
            val hourTemp = temp + (kotlin.math.sin(i * 0.5) * 4.0)
            val hourCond = when ((cityName.hashCode() + i) % 4) {
                0 -> "Sunny"
                1 -> "Partly Cloudy"
                2 -> "Cloudy"
                else -> "Rainy"
            }
            val precChance = when (hourCond) {
                "Sunny" -> 5
                "Partly Cloudy" -> 20
                "Cloudy" -> 45
                else -> 85
            }
            val windSp = windSpeed + (kotlin.math.cos(i * 0.4) * 3)
            HourlyForecast(
                timeLabel = timeLabel,
                temp = hourTemp,
                condition = hourCond,
                precipitationChance = precChance,
                windSpeedMph = if (windSp < 0.0) 0.0 else windSp
            )
        }

        // Generate 7-day forecast
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val currentDayIdx = kotlin.math.abs(cityName.hashCode()) % 7
        val daily = (0..6).map { i ->
            val dayName = days[(currentDayIdx + i) % 7]
            val dayLow = lowTemp + (i % 3) - (i % 2)
            val dayHigh = highTemp + (i % 4) - 2
            val dayCond = when ((cityName.hashCode() + i) % 4) {
                0 -> "Sunny"
                1 -> "Partly Cloudy"
                2 -> "Cloudy"
                else -> "Rainy"
            }
            val precChance = when (dayCond) {
                "Sunny" -> 10
                "Partly Cloudy" -> 25
                "Cloudy" -> 50
                else -> 90
            }
            DailyForecast(dayName, dayCond, dayLow, dayHigh, precChance)
        }

        return WeatherReport(
            cityName = cityName,
            temperature = temp,
            condition = condition,
            highTemp = highTemp,
            lowTemp = lowTemp,
            humidityPercent = humidity,
            windSpeedMph = windSpeed,
            windDirection = windDir,
            uvIndex = if (condition == "Sunny") 7 else 3,
            pressureMb = 1012,
            visibilityMiles = if (condition == "Rainy") 6.5 else 10.0,
            isAlertActive = isAlertActive,
            activeAlertTitle = alertTitle,
            activeAlertDesc = alertDesc,
            runSuitabilityScore = runScore,
            runSuitabilityText = runText,
            hourlyForecast = hourly,
            dailyForecast = daily,
            aiInsight = aiInsight
        )
    }
}
