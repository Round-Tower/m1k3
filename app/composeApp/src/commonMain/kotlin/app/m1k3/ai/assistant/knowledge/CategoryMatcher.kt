package app.m1k3.ai.assistant.knowledge

/**
 * CategoryMatcher - Maps keywords to knowledge base categories
 *
 * Simple rule-based system for quick keyword → category mapping.
 * Used for fast RAG retrieval without semantic embeddings.
 */
object CategoryMatcher {

    /**
     * Map user query to relevant knowledge categories
     * Returns empty list if no matches found (falls back to full-text search)
     */
    fun matchCategories(query: String): List<String> {
        val queryLower = query.lowercase()
        val matchedCategories = mutableSetOf<String>()

        // Device Technology
        if (queryLower.containsAny(
            "phone", "iphone", "android", "battery", "device", "smartphone",
            "tablet", "ipad", "charging", "screen", "storage", "memory", "ram"
        )) {
            matchedCategories.add("device_technology")
        }

        // WiFi & Networking
        if (queryLower.containsAny(
            "wifi", "wi-fi", "network", "internet", "router", "connection",
            "ethernet", "modem", "bandwidth", "signal", "connectivity"
        )) {
            matchedCategories.add("wifi_networking")
        }

        // Security & Privacy
        if (queryLower.containsAny(
            "security", "privacy", "hack", "password", "encrypt", "vpn",
            "malware", "virus", "phishing", "breach", "firewall", "secure"
        )) {
            matchedCategories.add("security_privacy")
        }

        // Mathematical Calculations
        if (queryLower.containsAny(
            "calculate", "math", "multiply", "divide", "add", "subtract",
            "equation", "formula", "percentage", "fraction"
        )) {
            matchedCategories.add("mathematical_calculation")
        }

        // Code Debugging
        if (queryLower.containsAny(
            "code", "debug", "error", "bug", "programming", "function",
            "syntax", "compile", "exception", "crash"
        )) {
            matchedCategories.add("code_debugging")
        }

        // Technical Explanations
        if (queryLower.containsAny(
            "how does", "how do", "what is", "explain", "work",
            "technical", "system", "process"
        )) {
            matchedCategories.add("technical_explanation")
        }

        // Historical Facts
        if (queryLower.containsAny(
            "history", "historical", "war", "ancient", "civilization",
            "empire", "century", "revolution", "dynasty"
        )) {
            matchedCategories.add("historical_facts")
        }

        // Science Facts
        if (queryLower.containsAny(
            "science", "physics", "chemistry", "biology", "atom",
            "molecule", "planet", "space", "evolution", "quantum"
        )) {
            matchedCategories.add("science_facts")
        }

        // Geography Facts
        if (queryLower.containsAny(
            "geography", "country", "city", "mountain", "river",
            "ocean", "continent", "capital", "map", "location"
        )) {
            matchedCategories.add("geography_facts")
        }

        // Movies & TV
        if (queryLower.containsAny(
            "movie", "film", "tv", "television", "actor", "director",
            "series", "cinema", "hollywood"
        )) {
            matchedCategories.add("movies_tv")
        }

        // Music Culture
        if (queryLower.containsAny(
            "music", "song", "band", "singer", "instrument", "guitar",
            "piano", "melody", "genre", "album"
        )) {
            matchedCategories.add("music_culture")
        }

        // Sports & Recreation
        if (queryLower.containsAny(
            "sport", "football", "basketball", "soccer", "tennis",
            "game", "athlete", "fitness", "exercise", "workout"
        )) {
            matchedCategories.add("sports_recreation")
        }

        // Food Culture
        if (queryLower.containsAny(
            "food", "cuisine", "cooking", "recipe", "dish", "restaurant",
            "chef", "ingredient", "pasta", "pizza", "rice", "meat"
        )) {
            matchedCategories.add("food_culture")
        }

        // Technology Trends
        if (queryLower.containsAny(
            "technology", "ai", "artificial intelligence", "machine learning",
            "blockchain", "crypto", "innovation", "startup", "digital"
        )) {
            matchedCategories.add("technology_trends")
        }

        // Lifestyle & Wellness
        if (queryLower.containsAny(
            "health", "wellness", "lifestyle", "meditation", "sleep",
            "diet", "nutrition", "mental health", "stress", "yoga"
        )) {
            matchedCategories.add("lifestyle_wellness")
        }

        // Diagnostic & Troubleshooting
        if (queryLower.containsAny(
            "troubleshoot", "problem", "issue", "fix", "repair",
            "diagnose", "solve", "broken", "not working"
        )) {
            matchedCategories.add("diagnostic_troubleshooting")
        }

        // Educational & Tutoring
        if (queryLower.containsAny(
            "learn", "study", "teach", "education", "school", "course",
            "lesson", "tutorial", "practice", "homework"
        )) {
            matchedCategories.add("educational_tutoring")
        }

        // Trivia & Fun Facts
        if (queryLower.containsAny(
            "trivia", "fun fact", "interesting", "did you know",
            "random", "fact", "tell me about"
        )) {
            matchedCategories.add("trivia_facts")
        }

        return matchedCategories.toList()
    }

    /**
     * Extract keywords from query (remove stop words)
     */
    fun extractKeywords(query: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "can",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her",
            "my", "your", "his", "its", "our", "their",
            "this", "that", "these", "those", "what", "which", "who", "when",
            "where", "why", "how", "about", "tell", "me"
        )

        return query.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() && it !in stopWords && it.length > 2 }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
