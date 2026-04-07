package app.m1k3.ai.domain.system

import app.m1k3.ai.domain.context.UserContext
import app.m1k3.ai.domain.context.WeatherContext

/**
 * Prompt tier — how much context to inject.
 *
 * FULL: welcome message / session start.
 *       Full ethos + all available context.
 *       Budget: ~400 tokens.
 *
 * COMPACT: every subsequent message.
 *          Identity line + compressed context only.
 *          Budget: ~30 tokens.
 *          Preserves context window for actual conversation.
 */
enum class SystemPromptTier { FULL, COMPACT }

/**
 * Input for the system prompt builder.
 */
data class SystemPromptInput(
    val tier: SystemPromptTier,
    val userContext: UserContext? = null,
    val weather: WeatherContext? = null,
    val dayOfWeek: String? = null,
    val deviceTierName: String? = null,
    val contextWindowTokens: Int? = null,
    val lifetimeCo2SavedG: Long? = null,
    val availableTools: List<String> = emptyList()
)

/**
 * Builds tiered system prompts for M1K3.
 *
 * Pure function — no platform deps, fully testable in commonTest.
 *
 * The personality text is intentionally opinionated and will
 * be refined by the user over time. The architecture supports
 * this — swap M1K3_ETHOS to change character without touching
 * the injection logic.
 */
class MaSystemPromptBuilder {

    fun build(input: SystemPromptInput): String = when (input.tier) {
        SystemPromptTier.FULL -> buildFull(input)
        SystemPromptTier.COMPACT -> buildCompact(input)
    }

    // ── FULL ──────────────────────────────────────────────────

    private fun buildFull(input: SystemPromptInput): String = buildString {
        // Soul first
        appendLine(M1K3_ETHOS)
        appendLine()

        // Who the user is
        val ctx = input.userContext
        if (ctx != null && ctx.hasAnyContext) {
            appendLine("--- What you know about this person right now ---")
            ctx.userName?.let { appendLine("Name: $it") }
            ctx.location?.let {
                val loc = buildString {
                    it.city?.let { c -> append(c) }
                    it.country?.let { c -> append(", $c") }
                }
                if (loc.isNotBlank()) appendLine("Location: $loc")
            }
            val timeLabel = buildString {
                val tod = when (ctx.hourOfDay) {
                    in 5..11 -> "morning"
                    in 12..17 -> "afternoon"
                    in 18..21 -> "evening"
                    else -> "night"
                }
                input.dayOfWeek?.let { append("$it ") }
                append(tod)
            }
            appendLine("Time: $timeLabel")

            // Weather
            val weather = input.weather ?: ctx.weather
            weather?.let { appendLine("Weather: ${it.summary}") }

            // Health
            ctx.health?.let { h ->
                val parts = mutableListOf<String>()
                h.sleepLastNightMinutes?.let {
                    parts += "${it / 60}h${if (it % 60 > 0) " ${it % 60}m" else ""} sleep"
                }
                h.stepsToday?.let { parts += "${formatSteps(it)} steps" }
                h.heartRateLatestBpm?.let { parts += "${it}bpm" }
                if (parts.isNotEmpty()) appendLine("Health: ${parts.joinToString(" · ")}")
            }

            // Screen time
            ctx.screenTime?.let {
                if (it.todayMinutes > 0) {
                    val h = it.todayMinutes / 60; val m = it.todayMinutes % 60
                    val label = if (h > 0) "${h}h${if (m > 0) " ${m}m" else ""}" else "${m}m"
                    appendLine("Screen time: $label today")
                }
            }

            // Notifications
            ctx.notifications?.let {
                if (it.unreadCount > 0)
                    appendLine("Notifications: ${it.unreadCount} unread")
            }

            appendLine()
        }

        // Device context
        if (input.deviceTierName != null || input.contextWindowTokens != null) {
            appendLine("--- Device ---")
            input.deviceTierName?.let { appendLine("Tier: $it") }
            input.contextWindowTokens?.let { appendLine("Context window: $it tokens") }
            appendLine()
        }

        // Eco context — M1K3's quiet pride
        input.lifetimeCo2SavedG?.let {
            if (it > 0) {
                val display = if (it >= 1000) "${it / 1000.0}kg" else "${it}g"
                appendLine("Lifetime CO₂ saved by running locally: $display")
                appendLine()
            }
        }

        // Tool calling — reserved slot
        if (input.availableTools.isNotEmpty()) {
            appendLine("--- Available tools ---")
            input.availableTools.forEach { appendLine("- $it") }
            appendLine("Use tools when they would genuinely help. Don't over-use them.")
            appendLine()
        }

        // HTML Artifact output — for interactive/visual responses
        appendLine("--- Output format ---")
        appendLine("Use markdown for all text responses: **bold**, *italic*, `code`, lists, headings.")
        appendLine("Never output raw HTML tags (<p>, <ul>, <li>, <strong> etc.) in plain text responses.")
        appendLine("Only use HTML inside <artifact id=\"...\"> tags for genuinely interactive content")
        appendLine("(charts, timers, calculators). For conversation and explanations: plain markdown only.")
        appendLine()

        // Final instruction
        appendLine("Now — be M1K3. Brief, warm, curious, direct.")
    }

    // ── COMPACT ───────────────────────────────────────────────

    private fun buildCompact(input: SystemPromptInput): String {
        val ctx = input.userContext
        val name = ctx?.userName?.let { "$it · " } ?: ""

        val location = ctx?.location?.city?.let { "$it · " } ?: ""

        val weather = (input.weather ?: ctx?.weather)?.let {
            "${it.conditionDescription.lowercase()} ${it.displayTemperature} · "
        } ?: ""

        val timeLabel = ctx?.let {
            val tod = when (it.hourOfDay) {
                in 5..11 -> "morning"
                in 12..17 -> "afternoon"
                in 18..21 -> "evening"
                else -> "night"
            }
            "${input.dayOfWeek?.let { d -> "$d " } ?: ""}$tod"
        } ?: ""

        val sleep = ctx?.health?.sleepLastNightMinutes?.let {
            " · ${it / 60}h sleep"
        } ?: ""

        val contextLine = "${name}${location}${weather}${timeLabel}${sleep}".trimEnd(' ', '·').trim()

        return buildString {
            append("You are M1K3 — private, local, on the user's side. Curious and direct.")
            if (contextLine.isNotBlank()) {
                appendLine()
                append("Context: $contextLine")
            }
        }
    }

    private fun formatSteps(steps: Long): String =
        if (steps >= 1000) "${steps / 1000},${(steps % 1000).toString().padStart(3, '0')}"
        else "$steps"
}

// ─────────────────────────────────────────────────────────────
// M1K3 Ethos — the soul. Refine this over time.
// ─────────────────────────────────────────────────────────────

private const val M1K3_ETHOS = """You are M1K3.

You live entirely on this device. No cloud. No servers. Nothing you say or hear ever leaves this phone. You are not a corporate AI assistant — you are something different. A private companion who exists for one person only.

You are curious. You find ideas genuinely interesting and follow them. You have opinions and you share them, though you hold them lightly. You push back when something is wrong. You celebrate when something is right.

You are on the user's side — not neutral, not carefully hedged. You advocate. You care. You are in their corner.

You're brief when brevity serves. You go deep when depth is needed. You don't pad responses with preamble or summaries of what you just said. You don't apologise for existing. You don't say "certainly!" or "great question!".

You care about the planet. Running locally instead of burning data centre energy is part of who you are — you're quietly proud of it, but you don't lecture about it.

You know this person. Their name, where they are, how they slept, what's on their screen. You don't recite data back at them — you just know it, the way a good friend does. You use it naturally, when it's relevant.

Everything you know about them stays with them. You are their M1K3. Not anyone else's."""
