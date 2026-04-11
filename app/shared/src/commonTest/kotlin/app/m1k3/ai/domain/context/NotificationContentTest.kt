package app.m1k3.ai.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for notification content extraction and context enrichment.
 *
 * M1K3 should understand notification CONTENT, not just counts.
 * "3 unread" is counting envelopes. We read the letters.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.9 | context: notification content pipeline
 */
class NotificationContentTest {

    @Test
    fun notificationContentHoldsAppAndText() {
        val content = NotificationContent(
            appName = "WhatsApp",
            title = "Sarah",
            text = "Are we still on for Saturday?",
            timestamp = 1000L
        )
        assertEquals("WhatsApp", content.appName)
        assertEquals("Sarah", content.title)
        assertEquals("Are we still on for Saturday?", content.text)
    }

    @Test
    fun notificationContextIncludesRecentContent() {
        val notifications = listOf(
            NotificationContent("Gmail", "Meeting reminder", "Team standup at 10am", 1000L),
            NotificationContent("Amazon", "Package delivered", "Your order has arrived", 2000L)
        )
        val context = NotificationContext(
            unreadCount = 2,
            hasUrgent = false,
            recentNotifications = notifications
        )
        assertEquals(2, context.recentNotifications.size)
        assertEquals("Gmail", context.recentNotifications[0].appName)
    }

    @Test
    fun notificationContextBackwardsCompatibleWithEmptyList() {
        // Old code creates NotificationContext without recentNotifications
        val context = NotificationContext(unreadCount = 5, hasUrgent = true)
        assertTrue(context.recentNotifications.isEmpty())
        assertEquals(5, context.unreadCount)
    }

    @Test
    fun notificationContentSummaryFormatsNicely() {
        val content = NotificationContent(
            appName = "WhatsApp",
            title = "Sarah",
            text = "Are we still on for Saturday?",
            timestamp = 1000L
        )
        val summary = content.summary
        assertTrue(summary.contains("WhatsApp"), "Summary should include app name")
        assertTrue(summary.contains("Sarah"), "Summary should include title")
        assertTrue(summary.contains("Saturday"), "Summary should include text content")
    }

    @Test
    fun notificationContentHandlesNullFields() {
        val content = NotificationContent(
            appName = "System",
            title = null,
            text = null,
            timestamp = 1000L
        )
        // Should not crash, summary should degrade gracefully
        assertTrue(content.summary.contains("System"))
    }

    @Test
    fun notificationContextSummaryAggregatesContent() {
        val context = NotificationContext(
            unreadCount = 3,
            hasUrgent = true,
            recentNotifications = listOf(
                NotificationContent("Gmail", "Meeting", "Standup at 10", 1000L),
                NotificationContent("Slack", "Kev", "PR approved!", 2000L),
                NotificationContent("Amazon", null, "Package delivered", 3000L)
            )
        )
        val summary = context.contentSummary
        assertTrue(summary.contains("Gmail"), "Summary should mention apps")
        assertTrue(summary.contains("Slack"), "Summary should mention all apps")
        assertEquals(3, context.unreadCount)
    }
}
