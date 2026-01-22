package app.m1k3.ai.domain.rag

/**
 * User Intent Classification
 *
 * Represents the detected intent category from user queries.
 * Used by IntentClassifier for RAG retrieval decisions.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * **ORDER MATTERS:** More specific intents should come before general ones.
 * The first matching intent wins, so:
 * 1. CONVERSATIONAL greetings/thanks (most specific, before SYSTEM)
 * 2. Specific domains (SCIENCE, WIFI_NETWORK, etc.)
 * 3. General patterns (TECHNICAL_EXPLANATION last)
 *
 * @property category Human-readable category name
 * @property keywords List of keywords that trigger this intent
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
