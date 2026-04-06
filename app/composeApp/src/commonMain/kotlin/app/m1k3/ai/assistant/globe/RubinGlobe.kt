package app.m1k3.ai.assistant.globe

/**
 * Signed: Kev + claude-sonnet-4-6, 2026-04-06
 * Format: MurphySig v0.1 (https://murphysig.dev/spec)
 *
 * Context: Rubin-inspired dot-globe for 間 AI chat background. Inspired by
 * the Vera C. Rubin Observatory's Orbitviewer — data as light on a dark void.
 * Chose pure Compose Canvas over Filament sphere to stay in commonMain (KMP-portable,
 * zero GPU contention with avatar engine, no extra deps). Performance tiers
 * borrow Rubin's tiered rendering approach (16K mobile → 1M desktop → we do
 * 800/2000/4500). Continent dots are procedurally generated from seed clusters,
 * not a real GeoJSON dataset — visually convincing, not cartographically precise.
 *
 * Confidence: 0.75 — maths solid, continent shapes approximate. Upgrade path:
 * replace generateLandDots() with a real ne_110m_land GeoJSON point set.
 * Open: Does Canvas at 4500 points + infinite rotation cause jank on low-end
 * devices? Needs real-device profiling. Consider skipping frames on thermal throttle.
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rubin-style dot-globe composable.
 *
 * Inspired by the Vera C. Rubin Observatory's Orbitviewer —
 * data as light on a dark void. Each dot is a piece of Earth's
 * landmass rendered as a glowing point in 3D space.
 *
 * Technique: Orthographic projection with depth-based alpha.
 * Near dots are bright, far dots fade to nothing. The globe
 * rotates slowly, revealing the planet like a living sculpture.
 *
 * Performance tiers (Rubin-inspired):
 * - LOW:    ~800 points  (older devices)
 * - MEDIUM: ~2000 points (mid-range)
 * - HIGH:   ~4500 points (Pixel 9a and above)
 *
 * @param modifier Compose modifier
 * @param focusLocation Location to orient the globe toward
 * @param alpha Overall opacity (0.0 to 1.0) — dim during generation
 * @param performanceTier Number of dots to render
 * @param accentColor Color of land dots (default Ma orange glow)
 * @param gridColor Color of lat/lon grid lines
 */
@Composable
fun RubinGlobe(
    modifier: Modifier = Modifier.fillMaxSize(),
    focusLocation: GlobeLocation? = null,
    alpha: Float = 0.35f,
    performanceTier: Int = TIER_MEDIUM,
    accentColor: Color = Color(0xFFD97706),    // Ma orange
    gridColor: Color = Color(0xFF2A2A2A),
) {
    // Infinite rotation — the globe never stops turning
    val infiniteTransition = rememberInfiniteTransition(label = "globe_rotation")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "globe_lon_rotation"
    )

    // Focus animation — drift toward the target location
    var targetLon by remember { mutableStateOf(focusLocation?.lon?.toFloat() ?: 0f) }
    var targetLat by remember { mutableStateOf(focusLocation?.lat?.toFloat() ?: 20f) }

    LaunchedEffect(focusLocation) {
        focusLocation?.let {
            targetLon = it.lon.toFloat()
            targetLat = it.lat.toFloat().coerceIn(-60f, 60f)
        }
    }

    // Auto-cycle through locations every 25s
    var locationIndex by remember { mutableStateOf(0) }
    LaunchedEffect(focusLocation) {
        if (focusLocation == null) {
            while (true) {
                delay(25_000)
                locationIndex = (locationIndex + 1) % CuratedLocations.all.size
                val loc = CuratedLocations.all[locationIndex]
                targetLon = loc.lon.toFloat()
                targetLat = loc.lat.toFloat().coerceIn(-60f, 60f)
            }
        }
    }

    val animatedLat by animateFloatAsState(
        targetValue = targetLat,
        animationSpec = tween(durationMillis = 8000, easing = LinearEasing),
        label = "globe_lat"
    )

    // Generate land dots once — deterministic from seed
    val landDots = remember(performanceTier) { generateLandDots(performanceTier) }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.42f

        val lonRad = (baseRotation - targetLon) * (PI / 180.0).toFloat()
        val latRad = animatedLat * (PI / 180.0).toFloat()

        // --- Grid lines (very subtle) ---
        drawGlobeGrid(cx, cy, radius, lonRad, latRad, gridColor.copy(alpha = alpha * 0.3f))

        // --- Land dots ---
        for (dot in landDots) {
            val (x, y, depth) = projectPoint(dot.lat, dot.lon, lonRad, latRad, cx, cy, radius)
            if (depth < 0f) continue // Back of globe — skip

            // Rubin aesthetic: depth drives everything
            val depthAlpha = depth.coerceIn(0f, 1f)
            val dotAlpha = (depthAlpha * depthAlpha * alpha).coerceIn(0f, 1f)
            val dotRadius = (1.2f + depthAlpha * 1.8f)

            // Glow: draw twice — large soft, small bright
            if (depthAlpha > 0.6f) {
                drawCircle(
                    color = accentColor.copy(alpha = dotAlpha * 0.25f),
                    radius = dotRadius * 2.5f,
                    center = Offset(x, y)
                )
            }
            drawCircle(
                color = accentColor.copy(alpha = dotAlpha),
                radius = dotRadius,
                center = Offset(x, y)
            )
        }

        // --- Atmosphere glow ring ---
        drawCircle(
            color = accentColor.copy(alpha = alpha * 0.04f),
            radius = radius * 1.08f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = accentColor.copy(alpha = alpha * 0.02f),
            radius = radius * 1.18f,
            center = Offset(cx, cy)
        )
    }
}

/**
 * Project a lat/lon point to screen XY with depth.
 *
 * Returns Triple(x, y, depth) where depth 1.0 = facing camera, -1.0 = behind.
 */
private fun projectPoint(
    lat: Float, lon: Float,
    lonRad: Float, latRad: Float,
    cx: Float, cy: Float, radius: Float
): Triple<Float, Float, Float> {
    val latR = lat * (PI / 180.0).toFloat()
    val lonR = lon * (PI / 180.0).toFloat()

    // Spherical to Cartesian
    var px = cos(latR) * sin(lonR)
    var py = sin(latR)
    var pz = cos(latR) * cos(lonR)

    // Rotate around Y (longitude)
    val cosLon = cos(lonRad)
    val sinLon = sin(lonRad)
    val rx = px * cosLon + pz * sinLon
    val rz = -px * sinLon + pz * cosLon
    px = rx; pz = rz

    // Rotate around X (latitude tilt)
    val cosLat = cos(-latRad)
    val sinLat = sin(-latRad)
    val ry = py * cosLat - pz * sinLat
    val rz2 = py * sinLat + pz * cosLat
    py = ry; pz = rz2

    return Triple(
        cx + px * radius,
        cy - py * radius,
        pz  // depth: positive = front
    )
}

/**
 * Draw a subtle lat/lon grid.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlobeGrid(
    cx: Float, cy: Float, radius: Float,
    lonRad: Float, latRad: Float,
    color: Color
) {
    // Latitude lines at 30° intervals
    for (lat in listOf(-60f, -30f, 0f, 30f, 60f)) {
        val points = (0..72).map { i ->
            val lon = i * 5f
            projectPoint(lat, lon, lonRad, latRad, cx, cy, radius)
        }
        for (i in 0 until points.size - 1) {
            val (x1, y1, d1) = points[i]
            val (x2, y2, d2) = points[i + 1]
            if (d1 > 0 && d2 > 0) {
                drawLine(
                    color = color.copy(alpha = color.alpha * minOf(d1, d2).coerceIn(0f, 1f)),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 0.5f
                )
            }
        }
    }

    // Longitude lines at 30° intervals
    for (lon in listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f)) {
        val points = (0..36).map { i ->
            val lat = -90f + i * 5f
            projectPoint(lat, lon, lonRad, latRad, cx, cy, radius)
        }
        for (i in 0 until points.size - 1) {
            val (x1, y1, d1) = points[i]
            val (x2, y2, d2) = points[i + 1]
            if (d1 > 0 && d2 > 0) {
                drawLine(
                    color = color.copy(alpha = color.alpha * minOf(d1, d2).coerceIn(0f, 1f)),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 0.5f
                )
            }
        }
    }
}

/**
 * Seed data for continent clusters.
 *
 * Each entry: [centerLat, centerLon, latSpread, lonSpread, dotCount]
 * Dots are scattered within the spread using a deterministic pseudo-random.
 */
private val CONTINENT_SEEDS = listOf(
    // North America (east coast + west coast dense, central sparse)
    floatArrayOf(45f, -75f, 20f, 20f, 1f),
    floatArrayOf(37f, -95f, 12f, 25f, 0.4f),
    floatArrayOf(48f, -120f, 15f, 12f, 0.8f),
    floatArrayOf(55f, -105f, 12f, 20f, 0.5f),
    floatArrayOf(30f, -98f, 10f, 18f, 0.6f),
    floatArrayOf(20f, -100f, 8f, 12f, 0.9f),  // Mexico
    floatArrayOf(62f, -110f, 12f, 30f, 0.3f), // Canada north
    floatArrayOf(44f, -63f, 8f, 8f, 0.7f),    // Maritime Canada
    // South America
    floatArrayOf(-3f, -60f, 15f, 18f, 0.9f),  // Amazon
    floatArrayOf(-15f, -50f, 12f, 15f, 0.7f), // Brazil highlands
    floatArrayOf(-34f, -65f, 10f, 10f, 0.8f), // Argentina
    floatArrayOf(-20f, -70f, 18f, 6f, 0.7f),  // Chile/Andes
    floatArrayOf(5f, -73f, 6f, 6f, 0.8f),     // Colombia/Venezuela
    floatArrayOf(-50f, -72f, 6f, 6f, 0.7f),   // Patagonia
    // Europe
    floatArrayOf(48f, 10f, 10f, 20f, 1f),     // Central Europe
    floatArrayOf(55f, -3f, 6f, 6f, 0.9f),     // UK
    floatArrayOf(62f, 15f, 8f, 15f, 0.7f),    // Scandinavia
    floatArrayOf(40f, -5f, 6f, 8f, 0.9f),     // Spain/Portugal
    floatArrayOf(45f, 25f, 8f, 12f, 0.8f),    // Eastern Europe
    floatArrayOf(37f, 15f, 5f, 8f, 0.8f),     // Italy
    floatArrayOf(64f, -20f, 4f, 8f, 0.7f),    // Iceland
    // Africa
    floatArrayOf(5f, 20f, 20f, 25f, 0.6f),    // Central Africa
    floatArrayOf(15f, 25f, 15f, 20f, 0.5f),   // North Africa / Sahara sparse
    floatArrayOf(-25f, 25f, 15f, 15f, 0.8f),  // Southern Africa
    floatArrayOf(5f, 38f, 10f, 8f, 0.8f),     // East Africa
    floatArrayOf(7f, -5f, 6f, 10f, 0.9f),     // West Africa
    floatArrayOf(30f, 5f, 8f, 12f, 0.5f),     // Maghreb
    // Asia (west)
    floatArrayOf(35f, 45f, 10f, 15f, 0.7f),   // Middle East
    floatArrayOf(30f, 70f, 12f, 18f, 0.7f),   // Pakistan/India
    floatArrayOf(20f, 80f, 12f, 12f, 0.9f),   // India
    floatArrayOf(40f, 60f, 10f, 18f, 0.6f),   // Central Asia
    floatArrayOf(55f, 60f, 12f, 20f, 0.5f),   // Russia (Ural)
    // Asia (east)
    floatArrayOf(35f, 105f, 15f, 20f, 0.8f),  // China
    floatArrayOf(50f, 110f, 12f, 25f, 0.5f),  // Mongolia/Siberia
    floatArrayOf(62f, 120f, 12f, 25f, 0.4f),  // East Siberia
    floatArrayOf(36f, 128f, 8f, 6f, 0.9f),    // Korea/Japan
    floatArrayOf(35f, 138f, 6f, 4f, 1f),      // Japan
    floatArrayOf(28f, 87f, 6f, 8f, 0.7f),     // Nepal/Tibet
    // Southeast Asia
    floatArrayOf(15f, 100f, 10f, 12f, 0.8f),  // Indochina
    floatArrayOf(5f, 110f, 8f, 12f, 0.8f),    // Malaysia/Indonesia (partial)
    floatArrayOf(-5f, 120f, 6f, 18f, 0.7f),   // Indonesia
    floatArrayOf(12f, 122f, 8f, 6f, 0.8f),    // Philippines
    // Australia
    floatArrayOf(-27f, 133f, 14f, 18f, 0.7f), // Central Australia
    floatArrayOf(-33f, 151f, 5f, 4f, 0.9f),   // East coast
    floatArrayOf(-31f, 116f, 5f, 4f, 0.9f),   // West coast
    floatArrayOf(-20f, 140f, 8f, 10f, 0.6f),  // Queensland
    floatArrayOf(-43f, 172f, 3f, 3f, 0.9f),   // New Zealand (S)
    floatArrayOf(-37f, 175f, 2f, 2f, 0.9f),   // New Zealand (N)
    // Greenland
    floatArrayOf(72f, -40f, 8f, 18f, 0.5f),
    // Antarctica (sparse)
    floatArrayOf(-75f, 0f, 8f, 90f, 0.2f),
    floatArrayOf(-75f, 90f, 8f, 90f, 0.2f)
)

private data class LatLon(val lat: Float, val lon: Float)

/**
 * Generate land dots using continent seeds with pseudo-random scatter.
 *
 * Uses a deterministic LCG so dots are stable across recompositions.
 */
private fun generateLandDots(tier: Int): List<LatLon> {
    val dots = mutableListOf<LatLon>()
    var seed = 31337L

    fun nextFloat(): Float {
        seed = (seed * 6364136223846793005L + 1442695040888963407L) and 0x7FFFFFFFFFFFFFFFL
        return (seed and 0xFFFF).toFloat() / 65535f
    }

    val totalDots = tier

    // Distribute dots proportionally across continent seeds
    val totalWeight = CONTINENT_SEEDS.sumOf { it[4].toDouble() }.toFloat()

    for (seed_entry in CONTINENT_SEEDS) {
        val (centerLat, centerLon, latSpread, lonSpread, weight) = seed_entry
        val count = (totalDots * weight / totalWeight).toInt().coerceAtLeast(1)

        repeat(count) {
            val lat = centerLat + (nextFloat() - 0.5f) * latSpread * 2f
            val lon = centerLon + (nextFloat() - 0.5f) * lonSpread * 2f
            dots.add(LatLon(lat.coerceIn(-85f, 85f), lon))
        }
    }

    return dots
}

private operator fun FloatArray.component5(): Float = this[4]

// Performance tiers
const val TIER_LOW = 800
const val TIER_MEDIUM = 2000
const val TIER_HIGH = 4500
