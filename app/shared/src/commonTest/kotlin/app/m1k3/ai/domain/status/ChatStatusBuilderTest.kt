package app.m1k3.ai.domain.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertContains

/**
 * Tests for ChatStatusBuilder.
 */
class ChatStatusBuilderTest {

    private val builder = ChatStatusBuilder()

    @Test
    fun `getTimeBasedGreeting returns good morning for early hours`() {
        assertEquals("Good morning!", builder.getTimeBasedGreeting(5))
        assertEquals("Good morning!", builder.getTimeBasedGreeting(6))
        assertEquals("Good morning!", builder.getTimeBasedGreeting(11))
    }

    @Test
    fun `getTimeBasedGreeting returns good afternoon for midday hours`() {
        assertEquals("Good afternoon!", builder.getTimeBasedGreeting(12))
        assertEquals("Good afternoon!", builder.getTimeBasedGreeting(14))
        assertEquals("Good afternoon!", builder.getTimeBasedGreeting(17))
    }

    @Test
    fun `getTimeBasedGreeting returns good evening for evening hours`() {
        assertEquals("Good evening!", builder.getTimeBasedGreeting(18))
        assertEquals("Good evening!", builder.getTimeBasedGreeting(21))
        assertEquals("Good evening!", builder.getTimeBasedGreeting(23))
    }

    @Test
    fun `getTimeBasedGreeting returns good evening for late night hours`() {
        assertEquals("Good evening!", builder.getTimeBasedGreeting(0))
        assertEquals("Good evening!", builder.getTimeBasedGreeting(2))
        assertEquals("Good evening!", builder.getTimeBasedGreeting(4))
    }

    @Test
    fun `build creates status with all fields`() {
        val status = builder.build(
            hour = 14,
            engineReady = true,
            memoryCount = 127,
            knowledgeCount = 1842,
            maxContextTokens = 4096,
            deviceTierName = "Flagship",
            lastSessionTokens = 5000,
            lastSessionWaterMl = 2300,
            lastSessionEnergyWh = 45,
            lastSessionCo2G = 12
        )

        assertEquals("Good afternoon!", status.greeting)
        assertEquals(true, status.engineReady)
        assertEquals(127, status.memoryCount)
        assertEquals(1842, status.knowledgeCount)
        assertEquals(4096, status.maxContextTokens)
        assertEquals("Flagship", status.deviceTierName)
        assertEquals(5000, status.lastSessionTokens)
        assertEquals(2300, status.lastSessionWaterMl)
        assertEquals(45, status.lastSessionEnergyWh)
        assertEquals(12, status.lastSessionCo2G)
    }

    @Test
    fun `build handles null last session stats`() {
        val status = builder.build(
            hour = 9,
            engineReady = true,
            memoryCount = 0,
            knowledgeCount = 100,
            maxContextTokens = 2048,
            deviceTierName = "Budget",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        assertEquals("Good morning!", status.greeting)
        assertNull(status.lastSessionTokens)
        assertNull(status.lastSessionWaterMl)
        assertNull(status.lastSessionEnergyWh)
        assertNull(status.lastSessionCo2G)
    }

    @Test
    fun `build with engine not ready`() {
        val status = builder.build(
            hour = 14,
            engineReady = false,
            memoryCount = 0,
            knowledgeCount = 0,
            maxContextTokens = 0,
            deviceTierName = "Unknown",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        assertEquals(false, status.engineReady)
    }

    @Test
    fun `formatStatusText includes greeting`() {
        val status = builder.build(
            hour = 14,
            engineReady = true,
            memoryCount = 127,
            knowledgeCount = 1842,
            maxContextTokens = 4096,
            deviceTierName = "Flagship",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        val text = builder.formatStatusText(status)
        assertContains(text, "Good afternoon!")
    }

    @Test
    fun `formatStatusText includes engine status`() {
        val status = builder.build(
            hour = 10,
            engineReady = true,
            memoryCount = 0,
            knowledgeCount = 100,
            maxContextTokens = 2048,
            deviceTierName = "Budget",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        val text = builder.formatStatusText(status)
        assertContains(text, "Engine: Ready")
    }

    @Test
    fun `formatStatusText includes memory and knowledge counts`() {
        val status = builder.build(
            hour = 10,
            engineReady = true,
            memoryCount = 42,
            knowledgeCount = 1500,
            maxContextTokens = 4096,
            deviceTierName = "High-End",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        val text = builder.formatStatusText(status)
        assertContains(text, "42")
        assertContains(text, "1,500")
    }

    @Test
    fun `formatStatusText includes last session eco stats when present`() {
        val status = builder.build(
            hour = 14,
            engineReady = true,
            memoryCount = 100,
            knowledgeCount = 1000,
            maxContextTokens = 4096,
            deviceTierName = "Flagship",
            lastSessionTokens = 5000,
            lastSessionWaterMl = 2300,
            lastSessionEnergyWh = 45,
            lastSessionCo2G = 12
        )

        val text = builder.formatStatusText(status)
        assertContains(text, "2.3L water")
        assertContains(text, "45 Wh")
        assertContains(text, "12g CO2")
    }

    @Test
    fun `formatStatusText omits last session when no stats`() {
        val status = builder.build(
            hour = 14,
            engineReady = true,
            memoryCount = 100,
            knowledgeCount = 1000,
            maxContextTokens = 4096,
            deviceTierName = "Flagship",
            lastSessionTokens = null,
            lastSessionWaterMl = null,
            lastSessionEnergyWh = null,
            lastSessionCo2G = null
        )

        val text = builder.formatStatusText(status)
        assert(!text.contains("Last session")) { "Should not contain 'Last session' when no stats" }
    }
}
