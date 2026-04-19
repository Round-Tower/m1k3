package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ParameterType
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ToolCallGrammarBuilder — converts a list of Tool schemas into a
 * GBNF (GGML BNF) grammar string for llama.cpp's lazy grammar sampler.
 *
 * The generated grammar constrains tool-call JSON structure AFTER the model
 * emits the `<tool_call>` trigger. The model can freely generate prose up to
 * the trigger; everything between `<tool_call>` and `</tool_call>` is
 * grammar-constrained.
 *
 * This eliminates the classes of malformed output the existing
 * DefaultToolCallParser has to normalize (spaced tokens, dropped underscores,
 * malformed JSON) — the sampler physically cannot emit them.
 */
class ToolCallGrammarBuilderTest {

    private val builder = ToolCallGrammarBuilder()

    private val flashlight = Tool(
        id = "toggle_flashlight",
        name = "Toggle flashlight",
        description = "Turn the device flashlight on or off",
        parameters = listOf(
            ToolParameter("enable", ParameterType.BOOLEAN, "true to turn on, false to turn off")
        ),
        category = ToolCategory.SYSTEM
    )

    private val battery = Tool(
        id = "get_battery_level",
        name = "Get battery level",
        description = "Returns current battery percentage",
        parameters = emptyList(),
        category = ToolCategory.DEVICE_INFO
    )

    private val webSearch = Tool(
        id = "web_search",
        name = "Web search",
        description = "Search the web",
        parameters = listOf(
            ToolParameter("query", ParameterType.STRING, "Search query text")
        ),
        category = ToolCategory.KNOWLEDGE
    )

    // ===== Empty inputs =====

    @Test
    fun `empty tool list produces empty grammar`() {
        val grammar = builder.build(emptyList())
        assertTrue(grammar.isBlank(), "No tools → no grammar")
    }

    // ===== Structure =====

    @Test
    fun `grammar declares a root rule`() {
        val grammar = builder.build(listOf(battery))
        assertTrue(grammar.contains("root ::="), "Grammar must declare a root rule")
    }

    @Test
    fun `grammar root includes both the opening and closing tool_call tags`() {
        val grammar = builder.build(listOf(battery))
        // llama.cpp's lazy grammar receives content from the trigger position
        // INCLUDING the trigger itself, so root must anchor on the opening tag.
        assertTrue(
            grammar.contains("\"<tool_call>\""),
            "Root rule must include the opening <tool_call> terminal"
        )
        assertTrue(
            grammar.contains("\"</tool_call>\""),
            "Root rule must include the closing </tool_call> terminal"
        )
        assertTrue(
            grammar.contains("\"\\\"tool\\\"\""),
            "Grammar must include the literal \"tool\" key"
        )
    }

    // ===== Tool ID enumeration =====

    @Test
    fun `grammar enumerates all tool IDs as terminals`() {
        val grammar = builder.build(listOf(flashlight, battery, webSearch))
        // GBNF terminals for JSON strings look like "\"tool_name\"" — check for
        // the id substring (escaping layers make exact-literal assertions brittle).
        assertTrue(grammar.contains("toggle_flashlight"), "Missing toggle_flashlight terminal")
        assertTrue(grammar.contains("get_battery_level"), "Missing get_battery_level terminal")
        assertTrue(grammar.contains("web_search"), "Missing web_search terminal")
    }

    @Test
    fun `tool IDs appear as alternation in a tool-id rule`() {
        val grammar = builder.build(listOf(flashlight, battery))
        // GBNF alternation uses " | "
        val toolIdLine = grammar.lines().firstOrNull { it.trim().startsWith("tool-id") }
        assertTrue(
            toolIdLine != null && toolIdLine.contains("|"),
            "Tool IDs must be expressed as an alternation in a tool-id rule, got: $toolIdLine"
        )
    }

    // ===== Parameter types =====

    @Test
    fun `boolean parameters constrain to true or false`() {
        val grammar = builder.build(listOf(flashlight))
        assertTrue(grammar.contains("\"true\""), "Boolean grammar must include true")
        assertTrue(grammar.contains("\"false\""), "Boolean grammar must include false")
    }

    @Test
    fun `string parameter has a quoted string rule`() {
        val grammar = builder.build(listOf(webSearch))
        // String rule should have opening and closing escaped quotes
        assertTrue(
            grammar.contains("string") || grammar.contains("\\\""),
            "String parameter must produce a quoted-string rule"
        )
    }

    @Test
    fun `enum parameter constrains to the declared values`() {
        val tool = Tool(
            id = "set_mode",
            name = "Set mode",
            description = "Set device mode",
            parameters = listOf(
                ToolParameter(
                    name = "mode",
                    type = ParameterType.ENUM,
                    description = "Target mode",
                    enumValues = listOf("quiet", "loud", "vibrate")
                )
            ),
            category = ToolCategory.SYSTEM
        )
        val grammar = builder.build(listOf(tool))
        assertTrue(grammar.contains("quiet"), "Enum value 'quiet' missing")
        assertTrue(grammar.contains("loud"), "Enum value 'loud' missing")
        assertTrue(grammar.contains("vibrate"), "Enum value 'vibrate' missing")
    }

    // ===== Args object =====

    @Test
    fun `parameter-less tool accepts an empty args object`() {
        val grammar = builder.build(listOf(battery))
        // Must allow "args":{} for tools with no parameters
        assertTrue(
            grammar.contains("\"{}\""),
            "Grammar must allow empty args object for parameter-less tools"
        )
    }

    @Test
    fun `args keys are enumerated as terminals not free strings`() {
        val grammar = builder.build(listOf(flashlight))
        // The arg name "enable" should appear as a literal in the grammar
        assertTrue(
            grammar.contains("\"\\\"enable\\\"\""),
            "Arg name 'enable' must be a fixed terminal"
        )
    }

    // ===== Determinism / hygiene =====

    @Test
    fun `grammar is deterministic for the same input`() {
        val a = builder.build(listOf(flashlight, battery, webSearch))
        val b = builder.build(listOf(flashlight, battery, webSearch))
        assertEquals(a, b, "Grammar generation must be deterministic")
    }

    @Test
    fun `grammar does not contain GBNF reserved character confusion`() {
        val grammar = builder.build(listOf(flashlight))
        // GBNF uses ::= and | — these should only appear as syntax, not embedded in terminals
        // Every occurrence of ::= should be on a rule-definition line
        grammar.lines().forEach { line ->
            if (line.contains("::=")) {
                // Rule line: should match "name ::= ..." pattern
                assertTrue(
                    line.trim().matches(Regex("""[a-zA-Z-][a-zA-Z0-9-]*\s*::=.*""")),
                    "Malformed rule line: $line"
                )
            }
        }
    }

    @Test
    fun `rule names conform to llama_cpp is_word_char (no underscores)`() {
        // llama.cpp's GBNF parser (src/llama-grammar.cpp:98 is_word_char) accepts
        // ONLY [a-zA-Z0-9-] in rule names — underscore is NOT a word char.
        // Tool IDs contain underscores (e.g. "toggle_flashlight"), so rule names
        // that embed them must sanitize _ → - to survive grammar compilation.
        // MurphySig: kev+claude / confidence 0.98 / 2026-04-18
        // Regression guard: before 2026-04-18, args-toggle_flashlight made
        // llama_sampler_init_grammar_lazy_patterns return null on device.
        val underscoreTools = listOf(
            Tool(
                id = "toggle_flashlight",
                name = "Toggle",
                description = "d",
                category = ToolCategory.SYSTEM,
                parameters = listOf(
                    ToolParameter("enable", ParameterType.BOOLEAN, "d", required = true)
                )
            ),
            Tool(
                id = "get_battery_level",
                name = "Battery",
                description = "d",
                category = ToolCategory.DEVICE_INFO,
                parameters = emptyList()
            )
        )
        val grammar = builder.build(underscoreTools)
        grammar.lines().forEach { line ->
            val name = line.substringBefore("::=").trim()
            if (name.isNotEmpty() && line.contains("::=")) {
                assertFalse(
                    name.contains("_"),
                    "Rule name '$name' contains underscore — llama.cpp GBNF parser will reject it"
                )
                assertTrue(
                    name.matches(Regex("[a-zA-Z][a-zA-Z0-9-]*")),
                    "Rule name '$name' must match llama.cpp is_word_char ([a-zA-Z0-9-]+)"
                )
            }
        }
    }

    @Test
    fun `grammar root rule is the first non-comment line`() {
        val grammar = builder.build(listOf(battery))
        val firstRule = grammar.lines()
            .firstOrNull { it.contains("::=") && !it.trim().startsWith("#") }
        assertTrue(
            firstRule != null && firstRule.trim().startsWith("root"),
            "GBNF convention: root rule must be first. Got: $firstRule"
        )
    }

    // ===== Special characters in GBNF =====

    @Test
    fun `quote characters in JSON literals are properly escaped`() {
        val grammar = builder.build(listOf(battery))
        // JSON uses " as a literal. GBNF strings use " as delimiter.
        // So a literal quote must be escaped as \" inside a GBNF terminal.
        assertFalse(
            grammar.contains("\"\"\""),
            "Three consecutive quotes indicate unescaped quote literals"
        )
    }
}
