package app.m1k3.ai.assistant.rag

/**
 * 間 AI - Intent Classifier
 *
 * Classifies user queries into knowledge categories for intelligent
 * RAG retrieval. Uses keyword matching and pattern detection.
 *
 * **Philosophy:**
 * Simple, fast, local. No cloud API needed for intent detection.
 * Keyword-based classification is enough for 90% of queries.
 *
 * **Categories (20):**
 * Technical, Educational, Entertainment, Security, Support
 */
class IntentClassifier {

    /**
     * Knowledge categories matching comprehensive_knowledge_base.json
     */
    enum class Intent(val category: String, val keywords: List<String>) {
        // Technical Expertise
        MATH(
            category = "Mathematical Calculations",
            keywords = listOf("calculate", "math", "equation", "solve", "formula", "algebra", "geometry", "trigonometry", "calculus")
        ),
        CODE_DEBUG(
            category = "Code Debugging",
            keywords = listOf("code", "debug", "error", "bug", "programming", "function", "crash", "exception", "syntax", "compile")
        ),
        TECHNICAL_EXPLANATION(
            category = "Technical Explanations",
            keywords = listOf("how does", "explain", "what is", "technical", "system", "works", "architecture", "protocol")
        ),

        // Educational & General Knowledge
        HISTORY(
            category = "Historical Facts",
            keywords = listOf("history", "historical", "ancient", "war", "civilization", "empire", "revolution", "century", "era")
        ),
        SCIENCE(
            category = "Science Facts",
            keywords = listOf("science", "physics", "chemistry", "biology", "atom", "molecule", "species", "experiment", "theory")
        ),
        GEOGRAPHY(
            category = "Geography Facts",
            keywords = listOf("geography", "country", "city", "mountain", "river", "ocean", "continent", "capital", "location")
        ),
        MOVIES_TV(
            category = "Movies & TV",
            keywords = listOf("movie", "film", "tv", "series", "actor", "director", "cinema", "episode", "season", "cast")
        ),
        MUSIC(
            category = "Music Culture",
            keywords = listOf("music", "song", "artist", "album", "concert", "band", "singer", "instrument", "genre", "melody")
        ),
        SPORTS(
            category = "Sports & Recreation",
            keywords = listOf("sport", "game", "team", "player", "tournament", "championship", "fitness", "exercise", "athletic")
        ),
        FOOD(
            category = "Food Culture",
            keywords = listOf("food", "recipe", "cook", "cuisine", "dish", "restaurant", "ingredient", "meal", "taste")
        ),
        TECHNOLOGY(
            category = "Technology Trends",
            keywords = listOf("technology", "tech", "innovation", "digital", "software", "hardware", "internet", "app", "gadget")
        ),
        LIFESTYLE(
            category = "Lifestyle & Wellness",
            keywords = listOf("lifestyle", "wellness", "health", "habit", "routine", "meditation", "mindfulness", "balance", "self-care")
        ),

        // Advanced Expertise (Phase 3 additions)
        DEVICE_TECH(
            category = "Device Technology",
            keywords = listOf("phone", "device", "smartphone", "tablet", "laptop", "battery", "screen", "setup", "configure")
        ),
        WIFI_NETWORK(
            category = "WiFi & Networking",
            keywords = listOf("wifi", "network", "router", "internet", "connection", "ip", "dns", "modem", "bandwidth")
        ),
        SECURITY(
            category = "Security & Privacy",
            keywords = listOf("security", "privacy", "password", "hack", "encryption", "phishing", "malware", "vpn", "firewall")
        ),
        TROUBLESHOOTING(
            category = "Diagnostic & Troubleshooting",
            keywords = listOf("problem", "issue", "fix", "troubleshoot", "solve", "diagnose", "repair", "broken", "not working")
        ),
        EDUCATION(
            category = "Educational & Tutoring",
            keywords = listOf("learn", "study", "education", "teach", "tutor", "lesson", "homework", "exam", "practice")
        ),
        TRIVIA(
            category = "Trivia & Fun Facts",
            keywords = listOf("trivia", "fact", "interesting", "did you know", "quiz", "random", "curious", "amazing")
        ),

        // System Knowledge (M1K3-specific)
        SYSTEM(
            category = "M1K3 System Knowledge",
            keywords = listOf("m1k3", "ma ai", "you", "yourself", "your capabilities", "what can you do", "tell me about you")
        ),

        // Fallback
        CONVERSATIONAL(
            category = "Casual Conversation",
            keywords = listOf("hello", "hi", "hey", "thanks", "thank you", "bye", "goodbye", "how are you")
        ),
        GENERAL(
            category = "General Query",
            keywords = emptyList() // Catch-all
        );

        companion object {
            /**
             * Get all technical intents
             */
            val technicalIntents = listOf(MATH, CODE_DEBUG, TECHNICAL_EXPLANATION)

            /**
             * Get all educational intents
             */
            val educationalIntents = listOf(
                HISTORY, SCIENCE, GEOGRAPHY, MOVIES_TV, MUSIC,
                SPORTS, FOOD, TECHNOLOGY, LIFESTYLE
            )

            /**
             * Get all expertise intents
             */
            val expertiseIntents = listOf(
                DEVICE_TECH, WIFI_NETWORK, SECURITY,
                TROUBLESHOOTING, EDUCATION, TRIVIA
            )
        }
    }

    /**
     * Classify user query into knowledge intent
     *
     * Uses keyword matching with case-insensitive comparison.
     * Returns the first matching intent, or GENERAL as fallback.
     *
     * @param query User's question or statement
     * @return Detected intent
     */
    fun classify(query: String): Intent {
        val normalizedQuery = query.lowercase().trim()

        // Check each intent's keywords with word boundary matching
        for (intent in Intent.values()) {
            if (intent == Intent.GENERAL) continue // Skip catch-all

            // Check if any keyword matches (whole word or at word boundaries)
            for (keyword in intent.keywords) {
                val keywordLower = keyword.lowercase()
                // Match whole word or with word boundaries (space/punctuation)
                if (normalizedQuery == keywordLower ||
                    normalizedQuery.contains(" $keywordLower ") ||
                    normalizedQuery.startsWith("$keywordLower ") ||
                    normalizedQuery.endsWith(" $keywordLower") ||
                    // Also match with punctuation boundaries
                    normalizedQuery.contains("$keywordLower?") ||
                    normalizedQuery.contains("$keywordLower!") ||
                    normalizedQuery.contains("$keywordLower.") ||
                    normalizedQuery.contains("$keywordLower,")
                ) {
                    return intent
                }
            }
        }

        // Fallback to general
        return Intent.GENERAL
    }

    /**
     * Classify query with confidence score
     *
     * @param query User's question
     * @return Pair of (Intent, confidence 0.0-1.0)
     */
    fun classifyWithConfidence(query: String): Pair<Intent, Float> {
        val normalizedQuery = query.lowercase().trim()
        var bestIntent = Intent.GENERAL
        var maxMatches = 0

        // Count keyword matches for each intent (with word boundary matching)
        for (intent in Intent.values()) {
            if (intent == Intent.GENERAL) continue

            var matchCount = 0
            for (keyword in intent.keywords) {
                val keywordLower = keyword.lowercase()
                // Use same word boundary matching as classify()
                if (normalizedQuery == keywordLower ||
                    normalizedQuery.contains(" $keywordLower ") ||
                    normalizedQuery.startsWith("$keywordLower ") ||
                    normalizedQuery.endsWith(" $keywordLower") ||
                    normalizedQuery.contains("$keywordLower?") ||
                    normalizedQuery.contains("$keywordLower!") ||
                    normalizedQuery.contains("$keywordLower.") ||
                    normalizedQuery.contains("$keywordLower,")
                ) {
                    matchCount++
                }
            }

            if (matchCount > maxMatches) {
                maxMatches = matchCount
                bestIntent = intent
            }
        }

        // Calculate confidence based on match count
        val confidence = if (maxMatches == 0) {
            0.1f // Low confidence for general queries
        } else {
            (maxMatches.toFloat() / 3f).coerceIn(0.3f, 1.0f)
        }

        return Pair(bestIntent, confidence)
    }

    /**
     * Check if query requires knowledge retrieval
     *
     * @param intent Classified intent
     * @return true if RAG retrieval should be triggered
     */
    fun requiresKnowledgeRetrieval(intent: Intent): Boolean {
        return intent != Intent.CONVERSATIONAL && intent != Intent.GENERAL
    }

    /**
     * Get suggested retrieval limit based on intent
     *
     * @param intent Classified intent
     * @return Number of documents to retrieve (1-5)
     */
    fun getRetrievalLimit(intent: Intent): Int {
        return when (intent) {
            Intent.TROUBLESHOOTING, Intent.DEVICE_TECH -> 5 // More context for troubleshooting
            Intent.MATH, Intent.CODE_DEBUG -> 3 // Focused technical help
            Intent.TRIVIA -> 1 // Single fun fact
            Intent.CONVERSATIONAL, Intent.GENERAL -> 0 // No retrieval
            else -> 2 // Default: 2 relevant facts
        }
    }
}
