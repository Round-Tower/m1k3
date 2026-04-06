package app.m1k3.ai.domain.context

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD: RED phase — define expected ContextualGreetingBuilder behaviour.
 *
 * The greeting builder is a pure function. No platform deps,
 * no side effects. Everything testable in commonTest.
 */
class ContextualGreetingBuilderTest {

    private val builder = ContextualGreetingBuilder()

    // ── Time-based greeting ────────────────────────────────────

    @Test fun `morning greeting 6am`() {
        val result = builder.build(context(hour = 6))
        assertTrue(result.greeting.contains("morning", ignoreCase = true))
    }

    @Test fun `afternoon greeting 2pm`() {
        val result = builder.build(context(hour = 14))
        assertTrue(result.greeting.contains("afternoon", ignoreCase = true))
    }

    @Test fun `evening greeting 8pm`() {
        val result = builder.build(context(hour = 20))
        assertTrue(result.greeting.contains("evening", ignoreCase = true))
    }

    @Test fun `night greeting midnight`() {
        val result = builder.build(context(hour = 0))
        assertTrue(result.greeting.contains("night", ignoreCase = true) ||
                   result.greeting.contains("late", ignoreCase = true))
    }

    // ── Name personalisation ───────────────────────────────────

    @Test fun `greeting includes name when available`() {
        val result = builder.build(context(hour = 9, userName = "Kev"))
        assertTrue(result.greeting.contains("Kev"))
    }

    @Test fun `greeting works without name`() {
        val result = builder.build(context(hour = 9, userName = null))
        assertFalse(result.greeting.contains("null"))
    }

    // ── Location line ──────────────────────────────────────────

    @Test fun `location line shows city when available`() {
        val result = builder.build(context(
            hour = 9,
            location = LocationContext(city = "Dublin", country = "Ireland", lat = 53.3, lon = -6.2)
        ))
        assertTrue(result.locationLine?.contains("Dublin") == true)
    }

    @Test fun `location line is null when no location`() {
        val result = builder.build(context(hour = 9, location = null))
        assertEquals(null, result.locationLine)
    }

    // ── Health line ────────────────────────────────────────────

    @Test fun `health line shows steps when above threshold`() {
        val result = builder.build(context(
            hour = 14,
            health = HealthContext(stepsToday = 5280)
        ))
        assertTrue(result.healthLine?.contains("5,280") == true ||
                   result.healthLine?.contains("5280") == true)
    }

    @Test fun `health line shows sleep in morning`() {
        val result = builder.build(context(
            hour = 8,
            health = HealthContext(sleepLastNightMinutes = 420)
        ))
        assertTrue(result.healthLine?.contains("7h") == true)
    }

    @Test fun `health line is null when no health data`() {
        val result = builder.build(context(hour = 9, health = null))
        assertEquals(null, result.healthLine)
    }

    // ── Screen time line ───────────────────────────────────────

    @Test fun `screen time shown when available`() {
        val result = builder.build(context(
            hour = 16,
            screenTime = ScreenTimeContext(todayMinutes = 142)
        ))
        assertTrue(result.screenTimeLine?.contains("2h") == true)
    }

    @Test fun `screen time null when not available`() {
        val result = builder.build(context(hour = 9, screenTime = null))
        assertEquals(null, result.screenTimeLine)
    }

    // ── Notification line ──────────────────────────────────────

    @Test fun `notification line shows count when non-zero`() {
        val result = builder.build(context(
            hour = 9,
            notifications = NotificationContext(unreadCount = 4)
        ))
        assertTrue(result.notificationLine?.contains("4") == true)
    }

    @Test fun `notification line null when zero`() {
        val result = builder.build(context(
            hour = 9,
            notifications = NotificationContext(unreadCount = 0)
        ))
        assertEquals(null, result.notificationLine)
    }

    @Test fun `notification line null when no access`() {
        val result = builder.build(context(hour = 9, notifications = null))
        assertEquals(null, result.notificationLine)
    }

    // ── Closing line ───────────────────────────────────────────

    @Test fun `closing line is always present`() {
        val result = builder.build(context(hour = 9))
        assertTrue(result.closingLine.isNotBlank())
    }

    // ── Sleep formatting ───────────────────────────────────────

    @Test fun `sleep 7h 30m formatted correctly`() {
        val result = builder.build(context(
            hour = 8,
            health = HealthContext(sleepLastNightMinutes = 450)
        ))
        assertTrue(result.healthLine?.contains("7h 30m") == true ||
                   result.healthLine?.contains("7h30") == true)
    }

    @Test fun `sleep exactly 8h formatted correctly`() {
        val result = builder.build(context(
            hour = 8,
            health = HealthContext(sleepLastNightMinutes = 480)
        ))
        assertTrue(result.healthLine?.contains("8h") == true)
    }

    // ── Smart context: morning vs evening priorities ───────────

    @Test fun `morning prioritises sleep over steps`() {
        val result = builder.build(context(
            hour = 7,
            health = HealthContext(sleepLastNightMinutes = 360, stepsToday = 500)
        ))
        val healthLine = result.healthLine ?: ""
        // Sleep should appear before or instead of steps in morning
        assertTrue(healthLine.contains("6h") || healthLine.contains("sleep", ignoreCase = true))
    }

    // ── Helper ─────────────────────────────────────────────────

    private fun context(
        hour: Int = 9,
        userName: String? = null,
        location: LocationContext? = null,
        health: HealthContext? = null,
        screenTime: ScreenTimeContext? = null,
        notifications: NotificationContext? = null
    ) = UserContext(
        hourOfDay = hour,
        userName = userName,
        location = location,
        health = health,
        screenTime = screenTime,
        notifications = notifications
    )
}
