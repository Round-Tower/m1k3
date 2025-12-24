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
     *
     * **ORDER MATTERS:** More specific intents should come before general ones.
     * The first matching intent wins, so:
     * 1. CONVERSATIONAL greetings/thanks (most specific, before SYSTEM)
     * 2. Specific domains (SCIENCE, WIFI_NETWORK, etc.)
     * 3. General patterns (TECHNICAL_EXPLANATION last)
     */
    enum class Intent(val category: String, val keywords: List<String>) {
        // Conversational FIRST - greetings and social niceties
        // Must be before SYSTEM to avoid "thank you" matching "you"
        CONVERSATIONAL(
            category = "Casual Conversation",
            keywords = listOf("hello", "hi", "hey", "thanks", "thank you", "bye", "goodbye", "how are you", "good morning", "good night")
        ),

        // System Knowledge (M1K3-specific)
        // More specific phrases to avoid false positives
        SYSTEM(
            category = "M1K3 System Knowledge",
            keywords = listOf("m1k3", "ma ai", "what is m1k3", "yourself", "your capabilities", "what can you do", "tell me about yourself", "about you")
        ),

        // Technical Expertise - specific first
        MATH(
            category = "Mathematical Calculations",
            keywords = listOf("calculate", "math", "equation", "solve", "formula", "algebra", "geometry", "trigonometry", "calculus")
        ),
        CODE_DEBUG(
            category = "Code Debugging",
            keywords = listOf("code", "debug", "error", "bug", "programming", "function", "crash", "exception", "syntax", "compile")
        ),

        // AI & Machine Learning - BEFORE generic TECHNOLOGY/TECHNICAL
        AI_ML(
            category = "AI & Machine Learning",
            keywords = listOf("ai", "artificial intelligence", "machine learning", "neural network", "deep learning", "llm", "chatgpt", "gpt", "model training", "natural language")
        ),

        // Educational & General Knowledge - BEFORE TECHNICAL_EXPLANATION
        HISTORY(
            category = "Historical Facts",
            keywords = listOf("history", "historical", "ancient", "war", "civilization", "empire", "revolution", "century", "era", "invented", "when was")
        ),
        SCIENCE(
            category = "Science Facts",
            keywords = listOf("science", "physics", "chemistry", "biology", "atom", "molecule", "species", "experiment", "theory", "photosynthesis", "evolution", "quantum")
        ),
        GEOGRAPHY(
            category = "Geography Facts",
            keywords = listOf("geography", "country", "city", "mountain", "river", "ocean", "continent", "capital", "location", "where is", "everest", "amazon")
        ),
        MOVIES_TV(
            category = "Movies & TV",
            keywords = listOf("movie", "movies", "film", "tv", "series", "actor", "director", "cinema", "episode", "season", "cast", "directed")
        ),
        MUSIC(
            category = "Music Culture",
            keywords = listOf("music", "song", "artist", "album", "concert", "band", "singer", "instrument", "genre", "melody", "jazz", "classical")
        ),
        SPORTS(
            category = "Sports & Recreation",
            keywords = listOf("sport", "game", "team", "player", "tournament", "championship", "fitness", "exercise", "athletic")
        ),
        FOOD(
            category = "Food Culture",
            keywords = listOf("food", "recipe", "cook", "cuisine", "dish", "restaurant", "ingredient", "meal", "taste")
        ),
        LIFESTYLE(
            category = "Lifestyle & Wellness",
            keywords = listOf("lifestyle", "wellness", "health", "habit", "routine", "meditation", "mindfulness", "balance", "self-care")
        ),

        // Advanced Expertise - more specific keywords
        // DEVICE_TECH before WIFI_NETWORK since "phone" is more common
        DEVICE_TECH(
            category = "Device Technology",
            keywords = listOf("phone", "phones", "device", "smartphone", "tablet", "laptop", "battery", "screen", "overheating", "reset my")
        ),
        WIFI_NETWORK(
            category = "WiFi & Networking",
            keywords = listOf("wifi", "network", "router", "internet connection", "ip address", "dns", "modem", "bandwidth", "wifi slow", "setup router")
        ),
        SECURITY(
            category = "Security & Privacy",
            keywords = listOf("security", "privacy", "password", "hack", "phishing", "malware", "vpn", "firewall", "secure")
        ),
        TROUBLESHOOTING(
            category = "Diagnostic & Troubleshooting",
            keywords = listOf("problem", "issue", "fix", "troubleshoot", "diagnose", "repair", "broken", "not working")
        ),
        EDUCATION(
            category = "Educational & Tutoring",
            keywords = listOf("learn", "study", "education", "teach", "tutor", "lesson", "homework", "exam", "practice", "learning", "effectively")
        ),
        TRIVIA(
            category = "Trivia & Fun Facts",
            keywords = listOf("trivia", "interesting fact", "did you know", "quiz", "curious", "amazing fact", "fun fact")
        ),

        // TECHNOLOGY after device-specific, catches general tech queries
        TECHNOLOGY(
            category = "Technology Trends",
            keywords = listOf("technology", "innovation", "digital", "software", "hardware", "gadget")
        ),

        // TECHNICAL_EXPLANATION last - catches general "how does X work" patterns
        // Only after specific domains have been checked
        TECHNICAL_EXPLANATION(
            category = "Technical Explanations",
            keywords = listOf("how does", "explain", "what is", "technical", "architecture", "protocol", "encryption")
        ),

        // Fallback
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
                SPORTS, FOOD, TECHNOLOGY, LIFESTYLE, AI_ML
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

        // Check each intent's keywords
        for (intent in Intent.values()) {
            if (intent == Intent.GENERAL) continue // Skip catch-all

            // Check if any keyword matches
            for (keyword in intent.keywords) {
                if (matchesKeyword(normalizedQuery, keyword.lowercase())) {
                    return intent
                }
            }
        }

        // Fallback to general
        return Intent.GENERAL
    }

    /**
     * Check if query matches keyword with appropriate boundary rules.
     *
     * - Multi-word phrases: Use contains (e.g., "how are you", "thank you")
     * - Single words: Use word start boundary matching (allows suffixes like -ing, -ed, -s)
     */
    private fun matchesKeyword(query: String, keyword: String): Boolean {
        // Multi-word phrases can use simple contains
        if (keyword.contains(" ")) {
            return query.contains(keyword)
        }

        // Single words: check that keyword appears at word start boundary
        // This allows "crash" to match "crashing" but not "recrash"
        // Split by non-alphanumeric characters and check each word starts with keyword
        val words = query.split(Regex("[^a-z0-9]+"))
        return words.any { word ->
            word == keyword || word.startsWith(keyword)
        }
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

        // Count keyword matches for each intent (using same matching as classify())
        for (intent in Intent.values()) {
            if (intent == Intent.GENERAL) continue

            var matchCount = 0
            for (keyword in intent.keywords) {
                if (matchesKeyword(normalizedQuery, keyword.lowercase())) {
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
            Intent.AI_ML -> 4 // Rich AI/ML educational content
            Intent.MATH, Intent.CODE_DEBUG -> 3 // Focused technical help
            Intent.TRIVIA -> 1 // Single fun fact
            Intent.CONVERSATIONAL, Intent.GENERAL -> 0 // No retrieval
            else -> 2 // Default: 2 relevant facts
        }
    }
}
