package app.m1k3.ai.assistant.rag

import kotlin.test.*

/**
 * Tests for IntentClassifier - Intent detection for RAG system
 *
 * Validates that queries are correctly classified into 20 knowledge categories.
 */
class IntentClassifierTest {

    private lateinit var classifier: IntentClassifier

    @BeforeTest
    fun setup() {
        classifier = IntentClassifier()
    }

    // ========================================
    // Technical Expertise Tests
    // ========================================

    @Test
    fun `classify mathematical queries as MATH`() {
        assertEquals(IntentClassifier.Intent.MATH, classifier.classify("Calculate 2 + 2"))
        assertEquals(IntentClassifier.Intent.MATH, classifier.classify("solve this equation"))
        assertEquals(IntentClassifier.Intent.MATH, classifier.classify("What is the formula for area of circle?"))
        assertEquals(IntentClassifier.Intent.MATH, classifier.classify("help me with trigonometry"))
    }

    @Test
    fun `classify code debugging queries as CODE_DEBUG`() {
        assertEquals(IntentClassifier.Intent.CODE_DEBUG, classifier.classify("My code has an error"))
        assertEquals(IntentClassifier.Intent.CODE_DEBUG, classifier.classify("debug this function"))
        assertEquals(IntentClassifier.Intent.CODE_DEBUG, classifier.classify("why is my program crashing?"))
        assertEquals(IntentClassifier.Intent.CODE_DEBUG, classifier.classify("syntax error in Python"))
    }

    @Test
    fun `classify technical explanation queries as TECHNICAL_EXPLANATION`() {
        assertEquals(IntentClassifier.Intent.TECHNICAL_EXPLANATION, classifier.classify("How does encryption work?"))
        assertEquals(IntentClassifier.Intent.TECHNICAL_EXPLANATION, classifier.classify("Explain what is a TCP protocol"))
        assertEquals(IntentClassifier.Intent.TECHNICAL_EXPLANATION, classifier.classify("What is cloud architecture?"))
    }

    // ========================================
    // Educational & General Knowledge Tests
    // ========================================

    @Test
    fun `classify historical queries as HISTORY`() {
        assertEquals(IntentClassifier.Intent.HISTORY, classifier.classify("Tell me about World War II"))
        assertEquals(IntentClassifier.Intent.HISTORY, classifier.classify("When did the Roman Empire fall?"))
        assertEquals(IntentClassifier.Intent.HISTORY, classifier.classify("ancient civilizations"))
    }

    @Test
    fun `classify science queries as SCIENCE`() {
        assertEquals(IntentClassifier.Intent.SCIENCE, classifier.classify("How does photosynthesis work?"))
        assertEquals(IntentClassifier.Intent.SCIENCE, classifier.classify("What is quantum physics?"))
        assertEquals(IntentClassifier.Intent.SCIENCE, classifier.classify("Explain the theory of evolution"))
    }

    @Test
    fun `classify geography queries as GEOGRAPHY`() {
        assertEquals(IntentClassifier.Intent.GEOGRAPHY, classifier.classify("Where is Mount Everest?"))
        assertEquals(IntentClassifier.Intent.GEOGRAPHY, classifier.classify("What is the capital of France?"))
        assertEquals(IntentClassifier.Intent.GEOGRAPHY, classifier.classify("Tell me about the Amazon river"))
    }

    @Test
    fun `classify movie queries as MOVIES_TV`() {
        assertEquals(IntentClassifier.Intent.MOVIES_TV, classifier.classify("Who directed Inception?"))
        assertEquals(IntentClassifier.Intent.MOVIES_TV, classifier.classify("Tell me about this TV series"))
        assertEquals(IntentClassifier.Intent.MOVIES_TV, classifier.classify("Best movies of 2024"))
    }

    @Test
    fun `classify music queries as MUSIC`() {
        assertEquals(IntentClassifier.Intent.MUSIC, classifier.classify("Who is the best jazz artist?"))
        assertEquals(IntentClassifier.Intent.MUSIC, classifier.classify("Tell me about this song"))
        assertEquals(IntentClassifier.Intent.MUSIC, classifier.classify("What instrument is used in classical music?"))
    }

    // ========================================
    // Advanced Expertise Tests
    // ========================================

    @Test
    fun `classify device troubleshooting as DEVICE_TECH`() {
        assertEquals(IntentClassifier.Intent.DEVICE_TECH, classifier.classify("My phone battery drains quickly"))
        assertEquals(IntentClassifier.Intent.DEVICE_TECH, classifier.classify("How do I reset my smartphone?"))
        assertEquals(IntentClassifier.Intent.DEVICE_TECH, classifier.classify("My device is overheating"))
        assertEquals(IntentClassifier.Intent.DEVICE_TECH, classifier.classify("Screen is cracked, what to do?"))
    }

    @Test
    fun `classify WiFi queries as WIFI_NETWORK`() {
        assertEquals(IntentClassifier.Intent.WIFI_NETWORK, classifier.classify("My WiFi is slow"))
        assertEquals(IntentClassifier.Intent.WIFI_NETWORK, classifier.classify("How to setup router?"))
        assertEquals(IntentClassifier.Intent.WIFI_NETWORK, classifier.classify("Internet connection issues"))
        assertEquals(IntentClassifier.Intent.WIFI_NETWORK, classifier.classify("What is my IP address?"))
    }

    @Test
    fun `classify security queries as SECURITY`() {
        assertEquals(IntentClassifier.Intent.SECURITY, classifier.classify("How to protect from phishing?"))
        assertEquals(IntentClassifier.Intent.SECURITY, classifier.classify("What is a strong password?"))
        assertEquals(IntentClassifier.Intent.SECURITY, classifier.classify("How to use VPN?"))
        assertEquals(IntentClassifier.Intent.SECURITY, classifier.classify("Is this website secure?"))
    }

    @Test
    fun `classify troubleshooting queries as TROUBLESHOOTING`() {
        assertEquals(IntentClassifier.Intent.TROUBLESHOOTING, classifier.classify("I have a problem with my app"))
        assertEquals(IntentClassifier.Intent.TROUBLESHOOTING, classifier.classify("How to fix this issue?"))
        assertEquals(IntentClassifier.Intent.TROUBLESHOOTING, classifier.classify("Something is broken"))
        assertEquals(IntentClassifier.Intent.TROUBLESHOOTING, classifier.classify("Not working properly"))
    }

    @Test
    fun `classify educational queries as EDUCATION`() {
        assertEquals(IntentClassifier.Intent.EDUCATION, classifier.classify("How to study effectively?"))
        assertEquals(IntentClassifier.Intent.EDUCATION, classifier.classify("Best learning methods"))
        assertEquals(IntentClassifier.Intent.EDUCATION, classifier.classify("Help me with my homework"))
        assertEquals(IntentClassifier.Intent.EDUCATION, classifier.classify("How to prepare for exam?"))
    }

    @Test
    fun `classify trivia queries as TRIVIA`() {
        assertEquals(IntentClassifier.Intent.TRIVIA, classifier.classify("Tell me an interesting fact"))
        assertEquals(IntentClassifier.Intent.TRIVIA, classifier.classify("What's a fun trivia?"))
        assertEquals(IntentClassifier.Intent.TRIVIA, classifier.classify("Did you know any amazing facts?"))
        assertEquals(IntentClassifier.Intent.TRIVIA, classifier.classify("Random quiz question"))
    }

    // ========================================
    // System & Fallback Tests
    // ========================================

    @Test
    fun `classify system queries as SYSTEM`() {
        assertEquals(IntentClassifier.Intent.SYSTEM, classifier.classify("What is M1K3?"))
        assertEquals(IntentClassifier.Intent.SYSTEM, classifier.classify("Tell me about yourself"))
        assertEquals(IntentClassifier.Intent.SYSTEM, classifier.classify("What can you do?"))
        assertEquals(IntentClassifier.Intent.SYSTEM, classifier.classify("Your capabilities"))
    }

    @Test
    fun `classify conversational queries as CONVERSATIONAL`() {
        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, classifier.classify("Hello"))
        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, classifier.classify("Hi there!"))
        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, classifier.classify("Thank you"))
        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, classifier.classify("Goodbye"))
        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, classifier.classify("How are you?"))
    }

    @Test
    fun `classify unknown queries as GENERAL`() {
        assertEquals(IntentClassifier.Intent.GENERAL, classifier.classify("random text without keywords"))
        assertEquals(IntentClassifier.Intent.GENERAL, classifier.classify("xyzabc"))
        assertEquals(IntentClassifier.Intent.GENERAL, classifier.classify(""))
    }

    // ========================================
    // Confidence Scoring Tests
    // ========================================

    @Test
    fun `classifyWithConfidence returns high confidence for multiple keyword matches`() {
        val (intent, confidence) = classifier.classifyWithConfidence(
            "My phone device smartphone is broken and needs troubleshooting"
        )

        assertEquals(IntentClassifier.Intent.DEVICE_TECH, intent)
        assertTrue(confidence >= 0.8f, "Multiple keyword matches should yield high confidence, got $confidence")
    }

    @Test
    fun `classifyWithConfidence returns medium confidence for single keyword match`() {
        val (intent, confidence) = classifier.classifyWithConfidence("Tell me about phones")

        assertEquals(IntentClassifier.Intent.DEVICE_TECH, intent)
        assertTrue(confidence in 0.3f..0.6f, "Single match should yield medium confidence, got $confidence")
    }

    @Test
    fun `classifyWithConfidence returns low confidence for general queries`() {
        val (intent, confidence) = classifier.classifyWithConfidence("random text")

        assertEquals(IntentClassifier.Intent.GENERAL, intent)
        assertEquals(0.1f, confidence, 0.01f, "General queries should have low confidence")
    }

    @Test
    fun `classifyWithConfidence confidence increases with more matches`() {
        val (_, conf1) = classifier.classifyWithConfidence("phone")
        val (_, conf2) = classifier.classifyWithConfidence("phone device")
        val (_, conf3) = classifier.classifyWithConfidence("phone device smartphone")

        assertTrue(conf1 < conf2, "Two keywords should have higher confidence than one")
        assertTrue(conf2 <= conf3, "Three keywords should have equal or higher confidence than two")
    }

    // ========================================
    // Retrieval Requirement Tests
    // ========================================

    @Test
    fun `requiresKnowledgeRetrieval returns false for conversational queries`() {
        assertFalse(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.CONVERSATIONAL))
        assertFalse(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.GENERAL))
    }

    @Test
    fun `requiresKnowledgeRetrieval returns true for knowledge queries`() {
        assertTrue(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.DEVICE_TECH))
        assertTrue(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.SECURITY))
        assertTrue(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.TRIVIA))
        assertTrue(classifier.requiresKnowledgeRetrieval(IntentClassifier.Intent.MATH))
    }

    // ========================================
    // Retrieval Limit Tests
    // ========================================

    @Test
    fun `getRetrievalLimit returns correct limits for different intents`() {
        assertEquals(5, classifier.getRetrievalLimit(IntentClassifier.Intent.TROUBLESHOOTING), "Troubleshooting needs 5 docs")
        assertEquals(5, classifier.getRetrievalLimit(IntentClassifier.Intent.DEVICE_TECH), "Device tech needs 5 docs")
        assertEquals(3, classifier.getRetrievalLimit(IntentClassifier.Intent.MATH), "Math needs 3 docs")
        assertEquals(3, classifier.getRetrievalLimit(IntentClassifier.Intent.CODE_DEBUG), "Code debug needs 3 docs")
        assertEquals(1, classifier.getRetrievalLimit(IntentClassifier.Intent.TRIVIA), "Trivia needs 1 doc")
        assertEquals(0, classifier.getRetrievalLimit(IntentClassifier.Intent.CONVERSATIONAL), "Conversational needs 0 docs")
        assertEquals(0, classifier.getRetrievalLimit(IntentClassifier.Intent.GENERAL), "General needs 0 docs")
        assertEquals(2, classifier.getRetrievalLimit(IntentClassifier.Intent.HISTORY), "Default should be 2 docs")
    }

    // ========================================
    // Edge Cases & Disambiguation Tests
    // ========================================

    @Test
    fun `distinguish device troubleshooting from device trivia`() {
        // Troubleshooting (contains "problem", "fix", etc.)
        val troubleshoot = classifier.classify("My phone has a problem")
        assertTrue(
            troubleshoot == IntentClassifier.Intent.DEVICE_TECH || troubleshoot == IntentClassifier.Intent.TROUBLESHOOTING,
            "Troubleshooting query should be DEVICE_TECH or TROUBLESHOOTING"
        )

        // Historical fact (no troubleshooting keywords)
        val trivia = classifier.classify("When was the first smartphone invented?")
        assertNotEquals(IntentClassifier.Intent.DEVICE_TECH, trivia, "Historical fact should not be DEVICE_TECH")
    }

    @Test
    fun `case insensitivity works correctly`() {
        assertEquals(
            classifier.classify("MY PHONE BATTERY DRAINS QUICKLY"),
            classifier.classify("my phone battery drains quickly")
        )
    }

    @Test
    fun `whitespace handling works correctly`() {
        assertEquals(
            classifier.classify("   my phone battery drains    "),
            classifier.classify("my phone battery drains")
        )
    }

    @Test
    fun `first matching intent wins for overlapping keywords`() {
        // "phone" appears in DEVICE_TECH (earlier) and TECHNOLOGY (later)
        val intent = classifier.classify("Tell me about phone technology")

        // Should match DEVICE_TECH first (earlier in enum order)
        assertEquals(IntentClassifier.Intent.DEVICE_TECH, intent)
    }
}
