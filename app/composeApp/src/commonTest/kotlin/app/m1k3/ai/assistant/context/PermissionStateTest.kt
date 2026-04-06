package app.m1k3.ai.assistant.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD: PermissionState — models what the user has/hasn't granted.
 */
class PermissionStateTest {

    @Test fun `all denied by default`() {
        val state = ContextPermissionState()
        assertFalse(state.hasLocation)
        assertFalse(state.hasHealth)
        assertFalse(state.hasScreenTime)
        assertFalse(state.hasNotifications)
    }

    @Test fun `grantedCount zero when all denied`() {
        assertEquals(0, ContextPermissionState().grantedCount)
    }

    @Test fun `grantedCount counts correctly`() {
        val state = ContextPermissionState(hasLocation = true, hasHealth = true)
        assertEquals(2, state.grantedCount)
    }

    @Test fun `allGranted false when any missing`() {
        val state = ContextPermissionState(hasLocation = true, hasHealth = false)
        assertFalse(state.allGranted)
    }

    @Test fun `allGranted true when all present`() {
        val state = ContextPermissionState(
            hasLocation = true, hasHealth = true,
            hasScreenTime = true, hasNotifications = true
        )
        assertTrue(state.allGranted)
    }

    @Test fun `nudge label for location`() {
        val state = ContextPermissionState(hasLocation = false)
        assertTrue(state.nudgeLabels().any { it.contains("location", ignoreCase = true) })
    }

    @Test fun `nudge label for health`() {
        val state = ContextPermissionState(hasHealth = false)
        assertTrue(state.nudgeLabels().any { it.contains("health", ignoreCase = true) })
    }

    @Test fun `no nudges when all granted`() {
        val state = ContextPermissionState(
            hasLocation = true, hasHealth = true,
            hasScreenTime = true, hasNotifications = true
        )
        assertTrue(state.nudgeLabels().isEmpty())
    }
}
