package app.m1k3.ai.assistant.context

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.m1k3.ai.domain.context.HealthContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Provides health context from Android Health Connect (API 26+, built-in on API 34+).
 *
 * Reads steps, sleep, and heart rate — each independently optional.
 * Returns null fields for anything not permitted or not available.
 *
 * Permissions required (runtime, each optional):
 * - android.permission.health.READ_STEPS
 * - android.permission.health.READ_SLEEP
 * - android.permission.health.READ_HEART_RATE
 */
class HealthContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "HealthContextProvider"

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun getHealth(): HealthContext? {
        if (!isAvailable()) {
            Log.d(TAG, "Health Connect not available on this device")
            return null
        }

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()

            val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                readStepsToday(client)
            } else null

            val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                readSleepLastNight(client)
            } else null

            val heartRate = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
                readLatestHeartRate(client)
            } else null

            val health = HealthContext(
                stepsToday = steps,
                sleepLastNightMinutes = sleep,
                heartRateLatestBpm = heartRate
            )

            if (health.isEmpty) null else health

        } catch (e: Exception) {
            Log.w(TAG, "Health data fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun readStepsToday(client: HealthConnectClient): Long? {
        val midnight = ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(midnight, Instant.now())
                )
            )
            response.records.sumOf { it.count }.takeIf { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "Steps read failed: ${e.message}")
            null
        }
    }

    private suspend fun readSleepLastNight(client: HealthConnectClient): Int? {
        // Look at sleep from 8pm yesterday to now (captures overnight sleep)
        val start = ZonedDateTime.now(ZoneId.systemDefault())
            .minusDays(1).toLocalDate()
            .atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
                )
            )
            val totalMinutes = response.records.sumOf { record ->
                val durationMs = record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
                durationMs / 60_000
            }.toInt()
            totalMinutes.takeIf { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "Sleep read failed: ${e.message}")
            null
        }
    }

    private suspend fun readLatestHeartRate(client: HealthConnectClient): Int? {
        val oneHourAgo = Instant.now().minusSeconds(3600)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneHourAgo, Instant.now())
                )
            )
            response.records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Heart rate read failed: ${e.message}")
            null
        }
    }
}
