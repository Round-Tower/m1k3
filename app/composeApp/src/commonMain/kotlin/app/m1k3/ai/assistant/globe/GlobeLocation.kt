package app.m1k3.ai.assistant.globe

/**
 * A named geographic location for the globe background.
 *
 * @param name Display name shown briefly on transition
 * @param lat Latitude in degrees (-90 to 90)
 * @param lon Longitude in degrees (-180 to 180)
 * @param zoom Relative zoom level (1.0 = globe, 2.0 = closer)
 */
data class GlobeLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoom: Double = 1.0
)

/**
 * 間 AI curated locations — 20 handpicked spots that feel special.
 *
 * Each place chosen for visual beauty on the globe, cultural resonance,
 * or alignment with 間's philosophy of mindful technology.
 *
 * Rubin-inspired: data as light, places as meaning.
 */
object CuratedLocations {

    val all = listOf(
        GlobeLocation("Tokyo", 35.6762, 139.6503),
        GlobeLocation("Reykjavik", 64.1466, -21.9426),
        GlobeLocation("Machu Picchu", -13.1631, -72.5450),
        GlobeLocation("Patagonia", -50.9423, -73.4068),
        GlobeLocation("Kyoto", 35.0116, 135.7681),
        GlobeLocation("Svalbard", 78.2232, 15.6469),
        GlobeLocation("Atacama Desert", -24.5, -69.2),
        GlobeLocation("Norwegian Fjords", 61.2, 6.8),
        GlobeLocation("Amazon Basin", -3.4, -62.2),
        GlobeLocation("Sahara", 23.4, 25.6),
        GlobeLocation("Siberia", 62.0, 100.5),
        GlobeLocation("New Zealand", -44.0, 170.5),
        GlobeLocation("Iceland Glaciers", 65.0, -18.0),
        GlobeLocation("Himalayas", 28.0, 84.5),
        GlobeLocation("Great Barrier Reef", -18.3, 147.7),
        GlobeLocation("Scottish Highlands", 57.1, -4.7),
        GlobeLocation("Serengeti", -2.3, 34.8),
        GlobeLocation("Galapagos", -0.9, -89.6),
        GlobeLocation("Faroe Islands", 61.9, -6.9),
        GlobeLocation("Borneo Rainforest", 1.0, 114.0)
    )

    fun random(): GlobeLocation = all.random()

    fun next(current: GlobeLocation): GlobeLocation {
        val idx = all.indexOf(current)
        return all[(idx + 1) % all.size]
    }
}
