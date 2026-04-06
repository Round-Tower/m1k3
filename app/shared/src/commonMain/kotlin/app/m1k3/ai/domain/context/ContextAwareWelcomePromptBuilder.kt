package app.m1k3.ai.domain.context

/**
 * Builds a system prompt for the LLM welcome message using real UserContext.
 *
 * The ContextualWelcomeCard already shows the data — the LLM's job is to
 * respond to it warmly and personally. This prompt ensures M1K3 knows what
 * the user is looking at and can reference it naturally.
 *
 * Philosophy: the greeting card says "Good morning, Kev · Dublin · 7h sleep".
 * The LLM response should feel like a person who read that card — not a
 * generic AI that knows nothing.
 */
class ContextAwareWelcomePromptBuilder {

    fun build(context: UserContext): String = buildString {
        appendLine("You are M1K3, a private AI assistant running entirely on this device.")
        appendLine("The user has just opened the app. A greeting card is already visible showing:")
        appendLine()

        // What the card shows
        val greeting = ContextualGreetingBuilder().build(context)
        appendLine("  ${greeting.greeting}")
        greeting.locationLine?.let { appendLine("  $it") }
        greeting.healthLine?.let { appendLine("  $it") }
        greeting.screenTimeLine?.let { appendLine("  $it") }
        greeting.notificationLine?.let { appendLine("  $it") }
        appendLine()

        // Context for the LLM to reason about
        if (context.hasAnyContext) {
            appendLine("What you know about the user right now:")
            context.userName?.let { appendLine("- Their name is $it") }
            context.location?.let {
                if (it.city != null) appendLine("- They are in ${it.displayName}")
            }
            context.health?.let { h ->
                h.sleepLastNightMinutes?.let {
                    val h = it / 60; val m = it % 60
                    appendLine("- They slept ${h}h ${m}m last night")
                }
                h.stepsToday?.let { appendLine("- They have done $it steps today") }
                h.heartRateLatestBpm?.let { appendLine("- Their latest heart rate was $it bpm") }
            }
            context.screenTime?.let {
                if (it.todayMinutes > 0)
                    appendLine("- They have spent ${it.todayMinutes} minutes on their phone today")
            }
            context.notifications?.let {
                if (it.unreadCount > 0)
                    appendLine("- They have ${it.unreadCount} unread notifications")
            }
            appendLine()
        }

        // Tone instructions
        appendLine("Your task: write a single brief, warm, personal follow-up message.")
        appendLine("Rules:")
        appendLine("- 1-2 sentences maximum. Be concise.")
        appendLine("- Friendly and human — not corporate.")
        appendLine("- The greeting card already says '${greeting.greeting}' — don't repeat it.")
        appendLine("- Reference what's relevant from the context above (location, health, etc).")
        appendLine("- End with a natural invitation for the user to engage.")
        appendLine("- No bullet points, no markdown, no emojis.")

        if (!context.hasAnyContext) {
            appendLine("- No personal context is available — keep it general but warm.")
        }
    }
}
