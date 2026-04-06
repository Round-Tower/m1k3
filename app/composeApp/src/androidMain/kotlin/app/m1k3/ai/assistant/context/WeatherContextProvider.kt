package app.m1k3.ai.assistant.context

import android.util.Log
import app.m1k3.ai.domain.context.LocationContext
import app.m1k3.ai.domain.context.WeatherContext
import app.m1k3.ai.domain.context.wmoCodeToDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Fetches current weather from Open-Meteo.
 *
 * Open-Meteo is free, requires no API key, and is privacy-respecting
 * (no account, no tracking, open source). Requires INTERNET permission.
 *
 * Returns null gracefully if offline, location unavailable, or request fails.
 *
 * https://open-meteo.com/en/docs
 */
class WeatherContextProvider {

    companion object {
        private const val TAG = "WeatherProvider"
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    }

    suspend fun getWeather(location: LocationContext): WeatherContext? {
        val lat = location.lat ?: return null
        val lon = location.lon ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&forecast_days=1"

                val response = URL(url).readText()
                val json = JSONObject(response)
                val current = json.getJSONObject("current")

                val tempC = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")

                WeatherContext(
                    temperatureCelsius = tempC,
                    conditionDescription = wmoCodeToDescription(code),
                    conditionCode = code
                ).also {
                    Log.d(TAG, "Weather: ${it.summary} at ($lat, $lon)")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Weather fetch failed (offline?): ${e.message}")
                null
            }
        }
    }
}
