package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ParameterType
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolParameter

/**
 * Builds a GBNF (GGML BNF) grammar from a list of Tool schemas.
 *
 * The grammar is fed to llama.cpp's lazy grammar sampler
 * (`llama_sampler_init_grammar_lazy_patterns`) with `<tool_call>` as the
 * trigger pattern. The model generates prose freely until it emits the
 * trigger; everything after is grammar-constrained.
 *
 * The sampler physically cannot emit malformed tool-call JSON, so the
 * regex-based DefaultToolCallParser no longer has to normalize spaced
 * tokens, dropped underscores, or malformed structure.
 *
 * ## Grammar shape
 *
 * ```
 * root      ::= "{" "\"tool\"" ":" tool-id "," "\"args\"" ":" args "}" "</tool_call>"
 * tool-id   ::= "\"get_battery_level\"" | "\"toggle_flashlight\"" | ...
 * args      ::= "{}" | <per-tool arg shapes>
 * string    ::= "\"" char* "\""
 * char      ::= [^"\\] | "\\" ["\\nrt/bf]
 * number    ::= "-"? [0-9]+ ("." [0-9]+)?
 * boolean   ::= "true" | "false"
 * ```
 *
 * NOTE: whitespace is NOT inserted between JSON tokens — the sampler
 * produces canonical-form JSON the parser can consume directly.
 *
 * Pure Kotlin, commonMain. No platform deps, fully testable.
 */
class ToolCallGrammarBuilder {

    /**
     * Build a GBNF grammar string for the given tools.
     *
     * @return GBNF source, or empty string if [tools] is empty
     */
    fun build(tools: List<Tool>): String {
        if (tools.isEmpty()) return ""

        return buildString {
            appendLine(rootRule())
            appendLine(toolIdRule(tools))
            appendLine(argsRule(tools))
            tools.forEach { tool ->
                if (tool.parameters.isNotEmpty()) {
                    appendLine(perToolArgsRule(tool))
                }
            }
            appendLine(stringRule())
            appendLine(charRule())
            appendLine(numberRule())
            appendLine(booleanRule())
            appendLine(wsRule())
        }
    }

    /**
     * Sanitize a tool ID into a valid GBNF rule name suffix.
     *
     * llama.cpp's `is_word_char` (src/llama-grammar.cpp:98) accepts only
     * `[a-zA-Z0-9-]` in rule names. Tool IDs use snake_case, so we replace
     * `_` with `-` so `toggle_flashlight` becomes the rule suffix `toggle-flashlight`.
     *
     * The string literal form (inside `"..."` terminals) keeps the underscore —
     * only unquoted rule names are affected.
     *
     * MurphySig: kev+claude / confidence 0.98 / 2026-04-18
     * Discovered on Pixel 9a: grammar compilation silently returned null for
     * `args-toggle_flashlight`, causing fallback to unconstrained generation.
     */
    private fun ruleSuffix(toolId: String): String = toolId.replace('_', '-')

    // ── Rule builders ──────────────────────────────────────────

    /**
     * Root rule — the grammar receives content from the trigger position, which
     * INCLUDES the `<tool_call>` marker (see llama-grammar.cpp:380–408 —
     * `regex_search` without a capture group returns match-position zero).
     *
     * Therefore root starts with the opening tag, permits optional whitespace,
     * then canonical JSON, then the closing tag.
     */
    private fun rootRule(): String =
        """root ::= "<tool_call>" ws? "{" "\"tool\"" ":" tool-id "," "\"args\"" ":" args "}" ws? "</tool_call>""""

    private fun toolIdRule(tools: List<Tool>): String {
        val alternation = tools.joinToString(" | ") { "\"\\\"${it.id}\\\"\"" }
        return "tool-id ::= $alternation"
    }

    private fun argsRule(tools: List<Tool>): String {
        val alternatives = mutableListOf("\"{}\"")
        tools.forEach { tool ->
            if (tool.parameters.isNotEmpty()) {
                alternatives += "args-${ruleSuffix(tool.id)}"
            }
        }
        return "args ::= ${alternatives.joinToString(" | ")}"
    }

    /**
     * Produces a rule constraining args to exactly this tool's parameter shape.
     *
     * Required params appear in order; optional params are treated as required
     * for now (small-model reliability > argument flexibility).
     */
    private fun perToolArgsRule(tool: Tool): String {
        val pairs = tool.parameters.mapIndexed { i, param ->
            val keyLiteral = "\"\\\"${param.name}\\\"\""
            val valueRule = valueRuleFor(param)
            if (i == 0) {
                "$keyLiteral \":\" $valueRule"
            } else {
                "\",\" $keyLiteral \":\" $valueRule"
            }
        }
        return "args-${ruleSuffix(tool.id)} ::= \"{\" ${pairs.joinToString(" ")} \"}\""
    }

    private fun valueRuleFor(param: ToolParameter): String = when (param.type) {
        ParameterType.STRING -> "string"
        ParameterType.NUMBER -> "number"
        ParameterType.BOOLEAN -> "boolean"
        ParameterType.ENUM -> {
            val values = param.enumValues ?: error(
                "ENUM parameter '${param.name}' must declare enumValues"
            )
            val alts = values.joinToString(" | ") { "\"\\\"$it\\\"\"" }
            "($alts)"
        }
    }

    private fun stringRule(): String =
        """string ::= "\"" char* "\"""""

    private fun charRule(): String =
        // Any character except unescaped quote/backslash, OR an escape sequence.
        """char ::= [^"\\] | "\\" ["\\/bfnrt]"""

    private fun numberRule(): String =
        """number ::= "-"? [0-9]+ ("." [0-9]+)?"""

    private fun booleanRule(): String =
        """boolean ::= "true" | "false""""

    private fun wsRule(): String =
        """ws ::= [ \t\n]+"""
}
