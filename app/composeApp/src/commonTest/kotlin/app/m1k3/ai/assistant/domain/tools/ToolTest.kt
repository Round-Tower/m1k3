package app.m1k3.ai.assistant.domain.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for Tool domain entities
 *
 * TDD: These tests define the contract for Tool, ToolParameter, ToolCategory.
 * Run tests first (RED), then implement (GREEN), then refactor.
 */
class ToolTest {

    // ===== ToolCategory Tests =====

    @Test
    fun `ToolCategory has expected categories`() {
        val categories = ToolCategory.entries

        assertTrue(categories.any { it.name == "SYSTEM" })
        assertTrue(categories.any { it.name == "APPS" })
        assertTrue(categories.any { it.name == "DEVICE_INFO" })
        assertTrue(categories.any { it.name == "FILES" })
    }

    @Test
    fun `ToolCategory has display names`() {
        assertEquals("System Controls", ToolCategory.SYSTEM.displayName)
        assertEquals("App Launcher", ToolCategory.APPS.displayName)
        assertEquals("Device Info", ToolCategory.DEVICE_INFO.displayName)
        assertEquals("File Operations", ToolCategory.FILES.displayName)
    }

    // ===== ToolParameter Tests =====

    @Test
    fun `ToolParameter with string type`() {
        val param = ToolParameter(
            name = "url",
            type = ParameterType.STRING,
            description = "The URL to open",
            required = false,
            defaultValue = "https://google.com"
        )

        assertEquals("url", param.name)
        assertEquals(ParameterType.STRING, param.type)
        assertEquals("The URL to open", param.description)
        assertFalse(param.required)
        assertEquals("https://google.com", param.defaultValue)
    }

    @Test
    fun `ToolParameter with boolean type`() {
        val param = ToolParameter(
            name = "enable",
            type = ParameterType.BOOLEAN,
            description = "Turn on or off",
            required = true
        )

        assertEquals("enable", param.name)
        assertEquals(ParameterType.BOOLEAN, param.type)
        assertTrue(param.required)
    }

    @Test
    fun `ToolParameter with enum type has values`() {
        val param = ToolParameter(
            name = "section",
            type = ParameterType.ENUM,
            description = "Settings section to open",
            required = false,
            enumValues = listOf("wifi", "bluetooth", "display", "sound")
        )

        assertEquals(ParameterType.ENUM, param.type)
        assertEquals(4, param.enumValues?.size)
        assertTrue(param.enumValues?.contains("wifi") == true)
    }

    // ===== Tool Tests =====

    @Test
    fun `Tool with no parameters`() {
        val tool = Tool(
            id = "open_camera",
            name = "Open Camera",
            description = "Opens the device camera app",
            parameters = emptyList(),
            category = ToolCategory.APPS
        )

        assertEquals("open_camera", tool.id)
        assertEquals("Open Camera", tool.name)
        assertEquals("Opens the device camera app", tool.description)
        assertTrue(tool.parameters.isEmpty())
        assertEquals(ToolCategory.APPS, tool.category)
        assertFalse(tool.requiresConfirmation)
    }

    @Test
    fun `Tool with parameters`() {
        val tool = Tool(
            id = "toggle_flashlight",
            name = "Flashlight",
            description = "Toggle device flashlight",
            parameters = listOf(
                ToolParameter(
                    name = "enable",
                    type = ParameterType.BOOLEAN,
                    description = "Turn on (true) or off (false)",
                    required = true
                )
            ),
            category = ToolCategory.SYSTEM
        )

        assertEquals(1, tool.parameters.size)
        assertEquals("enable", tool.parameters[0].name)
    }

    @Test
    fun `Tool requiring confirmation`() {
        val tool = Tool(
            id = "write_note",
            name = "Write Note",
            description = "Writes content to a note file",
            parameters = listOf(
                ToolParameter(
                    name = "content",
                    type = ParameterType.STRING,
                    description = "Content to write",
                    required = true
                )
            ),
            category = ToolCategory.FILES,
            requiresConfirmation = true
        )

        assertTrue(tool.requiresConfirmation)
    }

    // ===== toSchemaString Tests =====

    @Test
    fun `toSchemaString formats tool without parameters`() {
        val tool = Tool(
            id = "get_battery_level",
            name = "Battery Level",
            description = "Gets the current battery level",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        )

        val schema = tool.toSchemaString()

        assertTrue(schema.contains("get_battery_level"))
        assertTrue(schema.contains("Gets the current battery level"))
        assertFalse(schema.contains("Parameters:"))
    }

    @Test
    fun `toSchemaString formats tool with parameters`() {
        val tool = Tool(
            id = "toggle_flashlight",
            name = "Flashlight",
            description = "Toggle device flashlight",
            parameters = listOf(
                ToolParameter(
                    name = "enable",
                    type = ParameterType.BOOLEAN,
                    description = "Turn on (true) or off (false)",
                    required = true
                )
            ),
            category = ToolCategory.SYSTEM
        )

        val schema = tool.toSchemaString()

        assertTrue(schema.contains("toggle_flashlight"))
        assertTrue(schema.contains("Toggle device flashlight"))
        assertTrue(schema.contains("Parameters:"))
        assertTrue(schema.contains("enable"))
        assertTrue(schema.contains("boolean"))
        assertTrue(schema.contains("(required)"))
    }

    @Test
    fun `toSchemaString marks optional parameters`() {
        val tool = Tool(
            id = "open_browser",
            name = "Open Browser",
            description = "Opens web browser",
            parameters = listOf(
                ToolParameter(
                    name = "url",
                    type = ParameterType.STRING,
                    description = "URL to open",
                    required = false
                )
            ),
            category = ToolCategory.APPS
        )

        val schema = tool.toSchemaString()

        assertTrue(schema.contains("(optional)"))
    }
}
