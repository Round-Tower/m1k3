package app.m1k3.ai.domain.context

/**
 * Current weather at the user's location.
 *
 * Sourced from Open-Meteo (free, no API key, privacy-respecting).
 * Only fetched when location permission is granted.
 */
data class WeatherContext(
    val temperatureCelsius: Double,
    val conditionDescription: String,
    val conditionCode: Int    // WMO weather interpretation code
) {
    val displayTemperature: String
        get() = "${temperatureCelsius.toInt()}°C"

    val summary: String
        get() = "$conditionDescription · $displayTemperature"
}

/**
 * Maps WMO weather codes to human-readable descriptions.
 * https://open-meteo.com/en/docs#weathervariables
 */
fun wmoCodeToDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow grains"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Variable"
}
