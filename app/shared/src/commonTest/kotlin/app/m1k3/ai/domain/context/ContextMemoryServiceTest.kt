package app.m1k3.ai.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ContextMemoryService — converts user context into embeddable memory chunks.
 *
 * M1K3 doesn't just see context in the moment — he REMEMBERS it.
 * Every notification, health snapshot, screen time pattern becomes
 * a searchable memory. "What was that delivery notification?" just works.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.9 | context: context embedding pipeline
 */
class ContextMemoryServiceTest {

    private val service = ContextMemoryService()

    @Test
    fun emptyContextProducesNoChunks() {
        val ctx = UserContext()
        val chunks = service.createContextChunks(ctx, timestamp = 1000L)
        assertTrue(chunks.isEmpty(), "Empty context should produce no chunks")
    }

    @Test
    fun notificationContentCreatesChunks() {
        val ctx = UserContext(
            notifications = NotificationContext(
                unreadCount = 2,
                recentNotifications = listOf(
                    NotificationContent("Gmail", "Meeting", "Standup at 10am", 1000L),
                    NotificationContent("Amazon", "Delivery", "Package arrived", 2000L)
                )
            )
        )
        val chunks = service.createContextChunks(ctx, timestamp = 3000L)
        assertTrue(chunks.any { it.category == "notification" }, "Should create notification chunks")
        assertTrue(
            chunks.any { it.text.contains("Gmail") && it.text.contains("Standup") },
            "Notification chunk should contain app and content"
        )
    }

    @Test
    fun healthSnapshotCreatesChunk() {
        val ctx = UserContext(
            health = HealthContext(
                stepsToday = 4200,
                sleepLastNightMinutes = 420,
                heartRateLatestBpm = 68
            )
        )
        val chunks = service.createContextChunks(ctx, timestamp = 1000L)
        assertTrue(chunks.any { it.category == "health" }, "Should create health chunk")
        assertTrue(
            chunks.any { it.text.contains("4200") || it.text.contains("4,200") },
            "Health chunk should contain step count"
        )
    }

    @Test
    fun screenTimeSnapshotCreatesChunk() {
        val ctx = UserContext(
            screenTime = ScreenTimeContext(
                todayMinutes = 222,
                topApps = listOf(
                    AppUsage("com.twitter", "Twitter", 72),
                    AppUsage("com.vscode", "VS Code", 58)
                )
            )
        )
        val chunks = service.createContextChunks(ctx, timestamp = 1000L)
        assertTrue(chunks.any { it.category == "screen_time" }, "Should create screen time chunk")
        assertTrue(
            chunks.any { it.text.contains("Twitter") },
            "Screen time chunk should contain top app"
        )
    }

    @Test
    fun fullContextCreatesMultipleChunks() {
        val ctx = UserContext(
            hourOfDay = 22,
            userName = "Kev",
            location = LocationContext(city = "Cork", country = "Ireland"),
            health = HealthContext(stepsToday = 8000, sleepLastNightMinutes = 450),
            screenTime = ScreenTimeContext(todayMinutes = 180),
            notifications = NotificationContext(
                unreadCount = 3,
                recentNotifications = listOf(
                    NotificationContent("Slack", "Kev", "PR approved!", 1000L)
                )
            )
        )
        val chunks = service.createContextChunks(ctx, timestamp = 2000L)
        assertTrue(chunks.size >= 3, "Full context should produce multiple chunks, got ${chunks.size}")
        val categories = chunks.map { it.category }.toSet()
        assertTrue("health" in categories, "Should have health chunk")
        assertTrue("screen_time" in categories, "Should have screen time chunk")
        assertTrue("notification" in categories, "Should have notification chunk")
    }

    @Test
    fun chunkImportanceReflectsContent() {
        val ctx = UserContext(
            notifications = NotificationContext(
                unreadCount = 1,
                hasUrgent = true,
                recentNotifications = listOf(
                    NotificationContent("Phone", "Missed call", "Mom", 1000L)
                )
            )
        )
        val chunks = service.createContextChunks(ctx, timestamp = 2000L)
        val notifChunk = chunks.first { it.category == "notification" }
        assertTrue(notifChunk.importance >= 0.4f, "Urgent notification should have higher importance")
    }

    @Test
    fun emptyNotificationsNoContent() {
        val ctx = UserContext(
            notifications = NotificationContext(unreadCount = 3, recentNotifications = emptyList())
        )
        val chunks = service.createContextChunks(ctx, timestamp = 1000L)
        // Count-only notifications don't produce embeddable content
        assertTrue(
            chunks.none { it.category == "notification" },
            "Count-only notifications without content shouldn't create chunks"
        )
    }
}
