package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolParameter
import app.m1k3.ai.domain.tools.ParameterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ToolFilter - Relevance-based tool filtering for small models
 *
 * TDD: These tests define the contract for filtering tools by query relevance.
 * Goal: Reduce prompt bloat from 150 tokens (11 tools) to 0-50 tokens (0-3 tools).
 */
class ToolFilterTest {

    private val filter = ToolFilter()

    // ===== Test Fixtures =====

    private fun createTestTools(): List<Tool> = listOf(
        Tool(
            id = "get_battery_level",
            name = "Get Battery",
            description = "Returns the current battery level as a percentage",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        ),
        Tool(
            id = "get_current_time",
            name = "Get Time",
            description = "Returns the current time in the specified format",
            parameters = listOf(
                ToolParameter(
                    name = "format",
                    description = "Time format",
                    type = ParameterType.STRING,
                    required = false
                )
            ),
            category = ToolCategory.DEVICE_INFO
        ),
        Tool(
            id = "toggle_flashlight",
            name = "Toggle Flashlight",
            description = "Turns the device flashlight on or off",
            parameters = listOf(
                ToolParameter(
                    name = "enable",
                    description = "Turn on (true) or off (false)",
                    type = ParameterType.BOOLEAN,
                    required = true
                )
            ),
            category = ToolCategory.SYSTEM
        ),
        Tool(
            id = "open_camera",
            name = "Open Camera",
            description = "Opens the device camera app",
            parameters = emptyList(),
            category = ToolCategory.APPS
        ),
        Tool(
            id = "open_browser",
            name = "Open Browser",
            description = "Opens a URL in the default web browser",
            parameters = listOf(
                ToolParameter(
                    name = "url",
                    description = "URL to open",
                    type = ParameterType.STRING,
                    required = true
                )
            ),
            category = ToolCategory.APPS
        ),
        Tool(
            id = "set_volume",
            name = "Set Volume",
            description = "Sets the volume level for a stream",
            parameters = listOf(
                ToolParameter(
                    name = "level",
                    description = "Volume level 0-100",
                    type = ParameterType.NUMBER,
                    required = true
                )
            ),
            category = ToolCategory.SYSTEM
        )
    )

    // ===== Keyword Extraction Tests =====

    @Test
    fun `extractToolKeywords parses snake_case ID`() {
        val tool = Tool(
            id = "get_battery_level",
            name = "Test",
            description = "Test tool",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        )

        val keywords = filter.extractToolKeywords(tool)

        assertTrue(keywords.contains("get"))
        assertTrue(keywords.contains("battery"))
        assertTrue(keywords.contains("level"))
    }

    @Test
    fun `extractToolKeywords extracts description words`() {
        val tool = Tool(
            id = "test_tool",
            name = "Test",
            description = "Returns the current battery percentage",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        )

        val keywords = filter.extractToolKeywords(tool)

        assertTrue(keywords.contains("returns"))
        assertTrue(keywords.contains("current"))
        assertTrue(keywords.contains("battery"))
        assertTrue(keywords.contains("percentage"))
    }

    @Test
    fun `extractToolKeywords filters stopwords`() {
        val tool = Tool(
            id = "test_tool",
            name = "Test",
            description = "the tool is for a test of the system",
            parameters = emptyList(),
            category = ToolCategory.SYSTEM
        )

        val keywords = filter.extractToolKeywords(tool)

        // Stopwords should be filtered
        assertTrue(!keywords.contains("the"))
        assertTrue(!keywords.contains("for"))
        assertTrue(!keywords.contains("of"))

        // Real keywords should be kept
        assertTrue(keywords.contains("tool"))
        assertTrue(keywords.contains("test"))
        assertTrue(keywords.contains("system"))
    }

    @Test
    fun `extractToolKeywords handles short words`() {
        val tool = Tool(
            id = "a_b_c",  // Very short words
            name = "Test",
            description = "a to in on",  // All short/stopwords
            parameters = emptyList(),
            category = ToolCategory.SYSTEM
        )

        val keywords = filter.extractToolKeywords(tool)

        // Single-letter and 2-letter words should be filtered
        assertTrue(keywords.isEmpty() || keywords.all { it.length > 2 })
    }

    // ===== Scoring Tests =====

    @Test
    fun `scoreTool returns 0 for no match`() {
        val tool = createTestTools()[2]  // toggle_flashlight
        val query = "teach me about ireland"
        val keywords = filter.extractToolKeywords(tool)

        val score = filter.scoreTool(query, tool, keywords)

        assertEquals(0.0f, score)
    }

    @Test
    fun `scoreTool scores DEVICE_INFO category trigger correctly`() {
        val tool = createTestTools()[0]  // get_battery_level
        val keywords = filter.extractToolKeywords(tool)

        // DEVICE_INFO category should match "what/get/current/show/tell"
        val queries = listOf("what is", "get the", "current status", "show me", "tell me")

        queries.forEach { query ->
            val score = filter.scoreTool(query, tool, keywords)
            assertTrue(score >= 0.4f, "Query '$query' should trigger DEVICE_INFO category (+0.4)")
        }
    }

    @Test
    fun `scoreTool scores APPS category trigger correctly`() {
        val tool = createTestTools()[3]  // open_camera
        val keywords = filter.extractToolKeywords(tool)

        // APPS category should match "open/launch/start"
        val queries = listOf("open the", "launch app", "start camera")

        queries.forEach { query ->
            val score = filter.scoreTool(query, tool, keywords)
            assertTrue(score >= 0.4f, "Query '$query' should trigger APPS category (+0.4)")
        }
    }

    @Test
    fun `scoreTool scores SYSTEM category trigger correctly`() {
        val tool = createTestTools()[2]  // toggle_flashlight
        val keywords = filter.extractToolKeywords(tool)

        // SYSTEM category should match "set/change/turn/toggle/enable/disable"
        val queries = listOf("set the", "change it", "turn on", "toggle this", "enable feature", "disable mode")

        queries.forEach { query ->
            val score = filter.scoreTool(query, tool, keywords)
            assertTrue(score >= 0.4f, "Query '$query' should trigger SYSTEM category (+0.4)")
        }
    }

    @Test
    fun `scoreTool scores keyword match correctly`() {
        val tool = createTestTools()[0]  // get_battery_level
        val keywords = filter.extractToolKeywords(tool)

        // Query with "battery" keyword should add +0.2
        val score = filter.scoreTool("check battery", tool, keywords)

        assertTrue(score >= 0.2f, "Keyword 'battery' should add +0.2")
    }

    @Test
    fun `scoreTool combines category and keyword scores`() {
        val tool = createTestTools()[1]  // get_current_time
        val keywords = filter.extractToolKeywords(tool)

        // "what time is it" should trigger:
        // - DEVICE_INFO category (+0.4)
        // - "time" keyword (+0.2)
        // Total: 0.6+
        val score = filter.scoreTool("what time is it", tool, keywords)

        assertTrue(score >= 0.6f, "Should combine category (0.4) + keyword (0.2) = 0.6+")
    }

    @Test
    fun `scoreTool caps score at 1_0`() {
        val tool = Tool(
            id = "get_current_battery_time_level_status",  // Many keywords
            name = "Test",
            description = "returns shows displays current battery percentage",
            parameters = emptyList(),
            category = ToolCategory.DEVICE_INFO
        )
        val keywords = filter.extractToolKeywords(tool)

        // Query with category match + many keyword matches
        // Keywords: get, current, battery, time, level, status, returns, shows, displays, percentage
        // Query matches: get, current, battery, level (4+ matches = 0.6+ keyword score)
        val score = filter.scoreTool("what current battery level status", tool, keywords)

        // Score: 0.4 (category) + 0.6 (keywords maxed) = 1.0
        assertEquals(1.0f, score, "Score should be capped at 1.0")
    }

    @Test
    fun `scoreTool handles case insensitivity`() {
        val tool = createTestTools()[0]  // get_battery_level
        val keywords = filter.extractToolKeywords(tool)

        val score1 = filter.scoreTool("BATTERY", tool, keywords)
        val score2 = filter.scoreTool("battery", tool, keywords)
        val score3 = filter.scoreTool("Battery", tool, keywords)

        assertEquals(score1, score2)
        assertEquals(score2, score3)
    }

    // ===== Filtering Tests =====

    @Test
    fun `filterByRelevance returns empty for no matches`() {
        val tools = createTestTools()
        val query = "teach me about ireland"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isEmpty(), "Educational query should match no tools")
    }

    @Test
    fun `filterByRelevance returns tools sorted by score`() {
        val tools = createTestTools()
        val query = "what time is it"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        // get_current_time should be first (highest score)
        assertTrue(results.isNotEmpty())
        assertEquals("get_current_time", results[0].first.id)

        // Scores should be descending
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].second >= results[i + 1].second,
                "Scores should be sorted descending")
        }
    }

    @Test
    fun `filterByRelevance respects maxTools limit`() {
        val tools = createTestTools()
        val query = "open"  // Should match multiple APPS tools

        val results = filter.filterByRelevance(query, tools, maxTools = 2)

        assertTrue(results.size <= 2, "Should respect maxTools limit of 2")
    }

    @Test
    fun `filterByRelevance handles empty tools list`() {
        val results = filter.filterByRelevance("query", emptyList(), maxTools = 3)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `filterByRelevance handles empty query`() {
        val tools = createTestTools()

        val results = filter.filterByRelevance("", tools, maxTools = 3)

        assertTrue(results.isEmpty(), "Empty query should match no tools")
    }

    @Test
    fun `filterByRelevance only returns tools with score greater than 0`() {
        val tools = createTestTools()
        val query = "flashlight"  // Should only match toggle_flashlight

        val results = filter.filterByRelevance(query, tools, maxTools = 5)

        // Should filter out 0-score tools
        results.forEach { (tool, score) ->
            assertTrue(score > 0.0f, "Tool ${tool.id} has zero score, should be filtered")
        }
    }

    // ===== Real-World Query Tests =====

    @Test
    fun `What time is it - returns get_current_time only`() {
        val tools = createTestTools()
        val query = "What time is it?"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("get_current_time", results[0].first.id)
        // Should have high score (category + keyword)
        assertTrue(results[0].second >= 0.6f)
    }

    @Test
    fun `Open camera - returns open_camera only`() {
        val tools = createTestTools()
        val query = "Open camera"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("open_camera", results[0].first.id)
        assertTrue(results[0].second >= 0.6f)
    }

    @Test
    fun `Teach me about Ireland - returns no tools`() {
        val tools = createTestTools()
        val query = "Teach me about Ireland"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isEmpty(), "Educational query should return no tools")
    }

    @Test
    fun `Turn on flashlight - returns toggle_flashlight only`() {
        val tools = createTestTools()
        val query = "Turn on flashlight"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("toggle_flashlight", results[0].first.id)
        assertTrue(results[0].second >= 0.6f)
    }

    @Test
    fun `What's my battery - returns get_battery_level only`() {
        val tools = createTestTools()
        val query = "What's my battery"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("get_battery_level", results[0].first.id)
        assertTrue(results[0].second >= 0.6f)
    }

    @Test
    fun `Help me - ambiguous query returns limited tools`() {
        val tools = createTestTools()
        val query = "Help me"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        // May match some tools or none, but should respect maxTools
        assertTrue(results.size <= 3)
    }

    @Test
    fun `Open browser with URL - returns open_browser`() {
        val tools = createTestTools()
        val query = "Open browser to google.com"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("open_browser", results[0].first.id)
    }

    @Test
    fun `Set volume - returns set_volume only`() {
        val tools = createTestTools()
        val query = "Set volume to 50"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find relevant tool")
        assertEquals("set_volume", results[0].first.id)
        assertTrue(results[0].second >= 0.6f)
    }

    // ===== Edge Case Tests (Apostrophes & Contractions) =====

    @Test
    fun `Apostrophe in query - What's matches DEVICE_INFO category`() {
        val tools = createTestTools()
        val query = "What's the time"

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Should find tool despite apostrophe")
        assertEquals("get_current_time", results[0].first.id)
        // Should get category trigger (0.4) + keyword (0.2+) = 0.6+
        assertTrue(results[0].second >= 0.6f, "Expected 0.6+, got ${results[0].second}")
    }

    @Test
    fun `Contraction - Don't matches category trigger`() {
        val tool = Tool(
            id = "disable_notifications",
            name = "Disable",
            description = "Disables notifications",
            parameters = emptyList(),
            category = ToolCategory.SYSTEM
        )
        val keywords = filter.extractToolKeywords(tool)

        // "don't" should match "disable" category (SYSTEM)
        val score = filter.scoreTool("don't disturb me", tool, keywords)

        // Should get SYSTEM category trigger (0.4+)
        assertTrue(score >= 0.4f, "Contraction should trigger category, got $score")
    }

    @Test
    fun `Multiple apostrophes handled correctly`() {
        val tools = createTestTools()
        val query = "What's today's time"  // Two apostrophes

        val results = filter.filterByRelevance(query, tools, maxTools = 3)

        assertTrue(results.isNotEmpty())
        assertEquals("get_current_time", results[0].first.id)
        assertTrue(results[0].second >= 0.6f)
    }

    // ===== Edge Case Tests (Overlapping Keywords) =====

    @Test
    fun `Timer query does not match time tool`() {
        val tools = listOf(
            Tool(
                id = "get_current_time",
                name = "Get Time",
                description = "Returns the current time",
                parameters = emptyList(),
                category = ToolCategory.DEVICE_INFO
            ),
            Tool(
                id = "set_timer",
                name = "Set Timer",
                description = "Creates a countdown timer",
                parameters = emptyList(),
                category = ToolCategory.SYSTEM
            )
        )

        val results = filter.filterByRelevance("Set a timer for 5 minutes", tools, maxTools = 3)

        // set_timer should be first
        assertTrue(results.isNotEmpty())
        assertEquals("set_timer", results[0].first.id)

        // get_current_time should NOT be in results (or have much lower score)
        val timeToolInResults = results.find { it.first.id == "get_current_time" }
        if (timeToolInResults != null) {
            // If it made it in, score should be < 0.3 (below threshold)
            assertTrue(timeToolInResults.second < 0.3f,
                "get_current_time shouldn't match 'timer' query, score: ${timeToolInResults.second}")
        }
    }

    @Test
    fun `Time query does not match timer tool`() {
        val tools = listOf(
            Tool(
                id = "get_current_time",
                name = "Get Time",
                description = "Returns the current time",
                parameters = emptyList(),
                category = ToolCategory.DEVICE_INFO
            ),
            Tool(
                id = "set_timer",
                name = "Set Timer",
                description = "Creates a countdown timer",
                parameters = emptyList(),
                category = ToolCategory.SYSTEM
            )
        )

        val results = filter.filterByRelevance("What time is it", tools, maxTools = 3)

        // get_current_time should be first
        assertTrue(results.isNotEmpty())
        assertEquals("get_current_time", results[0].first.id)

        // set_timer should NOT be in results (or have much lower score)
        val timerToolInResults = results.find { it.first.id == "set_timer" }
        if (timerToolInResults != null) {
            assertTrue(timerToolInResults.second < 0.3f,
                "set_timer shouldn't match 'time' query, score: ${timerToolInResults.second}")
        }
    }

    // ===== Web Search / KNOWLEDGE Category Tests =====

    private fun createToolsWithWebSearch(): List<Tool> = createTestTools() + Tool(
        id = "web_search",
        name = "Web Search",
        description = "Search the web for weather, news, facts, answers, information, directions, and more using DuckDuckGo. No API key, no tracking.",
        parameters = listOf(
            ToolParameter(
                name = "query",
                type = ParameterType.STRING,
                description = "Search query",
                required = true
            )
        ),
        category = ToolCategory.KNOWLEDGE
    )

    @Test
    fun `Weather query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("What's the weather like?", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Weather query should match web_search")
        assertEquals("web_search", results[0].first.id)
        assertTrue(results[0].second >= 0.4f)
    }

    @Test
    fun `Search query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Search for pizza near me", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Search query should match web_search")
        assertEquals("web_search", results[0].first.id)
        assertTrue(results[0].second >= 0.4f)
    }

    @Test
    fun `Look up query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Look up the latest news", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Look up query should match web_search")
        assertEquals("web_search", results[0].first.id)
    }

    @Test
    fun `Who is query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Who is the president of France?", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Who is query should match web_search")
        assertEquals("web_search", results[0].first.id)
    }

    @Test
    fun `Find query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Find me a recipe for pasta", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Find query should match web_search")
        assertEquals("web_search", results[0].first.id)
    }

    @Test
    fun `Google query matches web_search`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Google how to fix a flat tire", tools, maxTools = 3)

        assertTrue(results.isNotEmpty(), "Google query should match web_search")
        assertEquals("web_search", results[0].first.id)
    }

    @Test
    fun `KNOWLEDGE category scores on search and weather triggers`() {
        val tool = Tool(
            id = "web_search",
            name = "Web Search",
            description = "Search the web",
            parameters = emptyList(),
            category = ToolCategory.KNOWLEDGE
        )
        val keywords = filter.extractToolKeywords(tool)

        val searchQueries = listOf("search for", "find me", "look up", "weather today", "who is", "google it", "latest news")
        searchQueries.forEach { query ->
            val score = filter.scoreTool(query, tool, keywords)
            assertTrue(score >= 0.4f, "Query '$query' should trigger KNOWLEDGE category (+0.4), got $score")
        }
    }

    @Test
    fun `web_search does not match pure device queries`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("What's my battery level?", tools, maxTools = 3)

        assertTrue(results.isNotEmpty())
        // Battery should still be first, not web_search
        assertEquals("get_battery_level", results[0].first.id)
    }

    @Test
    fun `web_search does not match educational queries with no search intent`() {
        val tools = createToolsWithWebSearch()
        val results = filter.filterByRelevance("Teach me about Ireland", tools, maxTools = 3)

        // Should NOT include web_search for pure conversational queries
        val webSearch = results.find { it.first.id == "web_search" }
        assertTrue(webSearch == null || webSearch.second < 0.3f,
            "Educational query without search intent should not match web_search")
    }

    @Test
    fun `Minimum score threshold filters weak matches`() {
        val tool = Tool(
            id = "unrelated_tool",
            name = "Unrelated",
            description = "Does something else entirely",
            parameters = emptyList(),
            category = ToolCategory.FILES
        )

        val results = filter.filterByRelevance("random query text", listOf(tool), maxTools = 3)

        // Tool with very low score should be filtered out
        assertTrue(results.isEmpty() || results[0].second >= 0.3f,
            "Tools with score < 0.3 should be filtered")
    }
}
