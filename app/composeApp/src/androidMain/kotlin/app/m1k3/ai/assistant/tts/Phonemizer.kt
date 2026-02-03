package app.m1k3.ai.assistant.tts

/**
 * Phonemizer - Text to Kokoro token IDs via IPA phonemes
 *
 * Converts English text to IPA phoneme strings, then maps each
 * IPA character to Kokoro's vocabulary token IDs from config.json.
 *
 * Approach:
 * 1. Dictionary lookup for common English words → IPA
 * 2. Rule-based fallback for unknown words
 * 3. Character-level mapping through Kokoro vocab
 *
 * TODO: Upgrade to misaki G2P JNI for production-quality phonemization.
 */
class Phonemizer {

    companion object {
        // Kokoro vocabulary from config.json (IPA character → token ID)
        // Source: https://huggingface.co/hexgrad/Kokoro-82M/raw/main/config.json
        private val KOKORO_VOCAB: Map<Char, Int> = buildMap {
            // Punctuation & special
            put(';', 1); put(':', 2); put(',', 3); put('.', 4)
            put('!', 5); put('?', 6); put('—', 9); put('…', 10)
            put('"', 11); put('(', 12); put(')', 13)
            put('\u201C', 14); put('\u201D', 15) // " "
            put(' ', 16); put('\u0303', 17) // combining tilde

            // Affricates & special IPA
            put('\u02A3', 18) // ʣ
            put('\u02A5', 19) // ʥ
            put('\u02A6', 20) // ʦ
            put('\u02A8', 21) // ʨ
            put('\u1D5D', 22) // ᵝ

            // Latin letters (uppercase used for special Kokoro phonemes)
            put('A', 24); put('I', 25); put('O', 31); put('Q', 33)
            put('S', 35); put('T', 36); put('W', 39); put('Y', 41)
            put('\u1D4A', 42) // ᵊ

            // Lowercase a-z
            put('a', 43); put('b', 44); put('c', 45); put('d', 46)
            put('e', 47); put('f', 48); put('h', 50); put('i', 51)
            put('j', 52); put('k', 53); put('l', 54); put('m', 55)
            put('n', 56); put('o', 57); put('p', 58); put('q', 59)
            put('r', 60); put('s', 61); put('t', 62); put('u', 63)
            put('v', 64); put('w', 65); put('x', 66); put('y', 67)
            put('z', 68)

            // IPA vowels
            put('\u0251', 69)  // ɑ
            put('\u0250', 70)  // ɐ
            put('\u0252', 71)  // ɒ
            put('\u00E6', 72)  // æ
            put('\u03B2', 75)  // β
            put('\u0254', 76)  // ɔ
            put('\u0255', 77)  // ɕ
            put('\u00E7', 78)  // ç
            put('\u0256', 80)  // ɖ
            put('\u00F0', 81)  // ð
            put('\u02A4', 82)  // ʤ
            put('\u0259', 83)  // ə
            put('\u025A', 85)  // ɚ
            put('\u025B', 86)  // ɛ
            put('\u025C', 87)  // ɜ
            put('\u025F', 90)  // ɟ
            put('\u0261', 92)  // ɡ
            put('\u0265', 99)  // ɥ
            put('\u0268', 101) // ɨ
            put('\u026A', 102) // ɪ
            put('\u029D', 103) // ʝ
            put('\u026F', 110) // ɯ
            put('\u0270', 111) // ɰ
            put('\u014B', 112) // ŋ
            put('\u0273', 113) // ɳ
            put('\u0272', 114) // ɲ
            put('\u0274', 115) // ɴ
            put('\u00F8', 116) // ø
            put('\u0278', 118) // ɸ
            put('\u03B8', 119) // θ
            put('\u0153', 120) // œ
            put('\u0279', 123) // ɹ
            put('\u027E', 125) // ɾ
            put('\u027B', 126) // ɻ
            put('\u0281', 128) // ʁ
            put('\u027D', 129) // ɽ
            put('\u0282', 130) // ʂ
            put('\u0283', 131) // ʃ
            put('\u0288', 132) // ʈ
            put('\u02A7', 133) // ʧ
            put('\u028A', 135) // ʊ
            put('\u028B', 136) // ʋ
            put('\u028C', 138) // ʌ
            put('\u0263', 139) // ɣ
            put('\u0264', 140) // ɤ
            put('\u03C7', 142) // χ
            put('\u028E', 143) // ʎ
            put('\u0292', 147) // ʒ
            put('\u0294', 148) // ʔ

            // Prosodic markers
            put('\u02C8', 156) // ˈ (primary stress)
            put('\u02CC', 157) // ˌ (secondary stress)
            put('\u02D0', 158) // ː (length)
            put('\u02B0', 162) // ʰ (aspiration)
            put('\u02B2', 164) // ʲ (palatalization)

            // Intonation
            put('\u2193', 169) // ↓
            put('\u2192', 171) // →
            put('\u2197', 172) // ↗
            put('\u2198', 173) // ↘
            put('\u1D7B', 177) // ᵻ
        }

        /**
         * English word → IPA pronunciation dictionary.
         *
         * Common English words with IPA transcription.
         * Stress marks (ˈ) included for natural prosody.
         */
        private val IPA_DICT: Map<String, String> = buildMap {
            // Articles & determiners
            put("a", "ə"); put("an", "æn"); put("the", "ðə")
            put("this", "ðɪs"); put("that", "ðæt")
            put("these", "ðiːz"); put("those", "ðoʊz")
            put("my", "maɪ"); put("your", "jɔːɹ"); put("his", "hɪz")
            put("her", "hɝː"); put("our", "aʊɚ"); put("their", "ðɛɹ")

            // Pronouns
            put("i", "aɪ"); put("you", "juː"); put("he", "hiː")
            put("she", "ʃiː"); put("it", "ɪt"); put("we", "wiː")
            put("they", "ðeɪ"); put("me", "miː"); put("him", "hɪm")
            put("us", "ʌs"); put("them", "ðɛm")
            put("who", "huː"); put("what", "wʌt"); put("which", "wɪʧ")
            put("where", "wɛɹ"); put("when", "wɛn"); put("how", "haʊ")
            put("why", "waɪ")

            // Common verbs
            put("is", "ɪz"); put("am", "æm"); put("are", "ɑːɹ")
            put("was", "wʌz"); put("were", "wɝː"); put("be", "biː")
            put("been", "bɪn"); put("being", "ˈbiːɪŋ")
            put("have", "hæv"); put("has", "hæz"); put("had", "hæd")
            put("do", "duː"); put("does", "dʌz"); put("did", "dɪd")
            put("will", "wɪl"); put("would", "wʊd")
            put("can", "kæn"); put("could", "kʊd")
            put("should", "ʃʊd"); put("may", "meɪ"); put("might", "maɪt")
            put("shall", "ʃæl"); put("must", "mʌst")
            put("go", "ɡoʊ"); put("going", "ˈɡoʊɪŋ"); put("gone", "ɡɔːn")
            put("get", "ɡɛt"); put("got", "ɡɑːt")
            put("come", "kʌm"); put("came", "keɪm")
            put("make", "meɪk"); put("made", "meɪd")
            put("take", "teɪk"); put("took", "tʊk"); put("taken", "ˈteɪkən")
            put("give", "ɡɪv"); put("gave", "ɡeɪv")
            put("know", "noʊ"); put("knew", "njuː"); put("known", "noʊn")
            put("think", "θɪŋk"); put("thought", "θɔːt")
            put("say", "seɪ"); put("said", "sɛd")
            put("tell", "tɛl"); put("told", "toʊld")
            put("see", "siː"); put("saw", "sɔː"); put("seen", "siːn")
            put("look", "lʊk"); put("looking", "ˈlʊkɪŋ")
            put("find", "faɪnd"); put("found", "faʊnd")
            put("want", "wɑːnt"); put("need", "niːd")
            put("like", "laɪk"); put("love", "lʌv")
            put("use", "juːz"); put("used", "juːzd")
            put("try", "tɹaɪ"); put("help", "hɛlp")
            put("let", "lɛt"); put("keep", "kiːp")
            put("put", "pʊt"); put("set", "sɛt")
            put("run", "ɹʌn"); put("read", "ɹiːd")
            put("work", "wɝːk"); put("call", "kɔːl")
            put("ask", "æsk"); put("turn", "tɝːn")
            put("feel", "fiːl"); put("leave", "liːv")
            put("play", "pleɪ"); put("move", "muːv")
            put("live", "lɪv"); put("start", "stɑːɹt")
            put("mean", "miːn"); put("open", "ˈoʊpən")
            put("close", "kloʊz"); put("stop", "stɑːp")
            put("talk", "tɔːk"); put("speak", "spiːk")
            put("hear", "hɪɹ"); put("listen", "ˈlɪsən")
            put("write", "ɹaɪt"); put("learn", "lɝːn")
            put("change", "ʧeɪnʤ"); put("follow", "ˈfɑːloʊ")
            put("show", "ʃoʊ"); put("bring", "bɹɪŋ")

            // Prepositions & conjunctions
            put("in", "ɪn"); put("on", "ɑːn"); put("at", "æt")
            put("to", "tuː"); put("for", "fɔːɹ"); put("with", "wɪð")
            put("from", "fɹʌm"); put("by", "baɪ"); put("about", "əˈbaʊt")
            put("up", "ʌp"); put("out", "aʊt"); put("into", "ˈɪntuː")
            put("over", "ˈoʊvɚ"); put("after", "ˈæftɚ")
            put("under", "ˈʌndɚ"); put("between", "bɪˈtwiːn")
            put("through", "θɹuː"); put("before", "bɪˈfɔːɹ")
            put("and", "ænd"); put("but", "bʌt"); put("or", "ɔːɹ")
            put("if", "ɪf"); put("so", "soʊ"); put("because", "bɪˈkʌz")
            put("than", "ðæn"); put("then", "ðɛn"); put("also", "ˈɔːlsoʊ")

            // Common nouns
            put("time", "taɪm"); put("day", "deɪ"); put("year", "jɪɹ")
            put("people", "ˈpiːpəl"); put("way", "weɪ")
            put("man", "mæn"); put("woman", "ˈwʊmən")
            put("world", "wɝːld"); put("life", "laɪf")
            put("hand", "hænd"); put("part", "pɑːɹt")
            put("place", "pleɪs"); put("thing", "θɪŋ")
            put("name", "neɪm"); put("home", "hoʊm")
            put("water", "ˈwɔːtɚ"); put("room", "ɹuːm")
            put("mother", "ˈmʌðɚ"); put("number", "ˈnʌmbɚ")
            put("night", "naɪt"); put("point", "pɔɪnt")
            put("story", "ˈstɔːɹi"); put("word", "wɝːd")
            put("money", "ˈmʌni"); put("fact", "fækt")
            put("question", "ˈkwɛsʧən"); put("answer", "ˈænsɚ")

            // Adjectives
            put("good", "ɡʊd"); put("new", "njuː"); put("first", "fɝːst")
            put("last", "læst"); put("long", "lɔːŋ"); put("great", "ɡɹeɪt")
            put("little", "ˈlɪtəl"); put("own", "oʊn")
            put("other", "ˈʌðɚ"); put("old", "oʊld")
            put("right", "ɹaɪt"); put("big", "bɪɡ")
            put("high", "haɪ"); put("small", "smɔːl")
            put("large", "lɑːɹʤ"); put("next", "nɛkst")
            put("important", "ɪmˈpɔːɹtənt"); put("same", "seɪm")
            put("different", "ˈdɪfɹənt"); put("able", "ˈeɪbəl")
            put("sure", "ʃʊɹ"); put("true", "tɹuː")

            // Adverbs
            put("not", "nɑːt"); put("just", "ʤʌst"); put("very", "ˈvɛɹi")
            put("here", "hɪɹ"); put("there", "ðɛɹ")
            put("now", "naʊ"); put("well", "wɛl")
            put("only", "ˈoʊnli"); put("really", "ˈɹɪːli")
            put("still", "stɪl"); put("already", "ɔːlˈɹɛdi")
            put("never", "ˈnɛvɚ"); put("always", "ˈɔːlweɪz")
            put("too", "tuː"); put("even", "ˈiːvən")
            put("again", "əˈɡɛn"); put("yes", "jɛs"); put("no", "noʊ")
            put("maybe", "ˈmeɪbiː"); put("today", "təˈdeɪ")
            put("much", "mʌʧ"); put("more", "mɔːɹ"); put("most", "moʊst")

            // Numbers
            put("one", "wʌn"); put("two", "tuː"); put("three", "θɹiː")
            put("four", "fɔːɹ"); put("five", "faɪv"); put("six", "sɪks")
            put("seven", "ˈsɛvən"); put("eight", "eɪt"); put("nine", "naɪn")
            put("ten", "tɛn"); put("hundred", "ˈhʌndɹəd")
            put("thousand", "ˈθaʊzənd")

            // Greetings & common phrases
            put("hello", "hɛˈloʊ"); put("hi", "haɪ"); put("hey", "heɪ")
            put("okay", "ˌoʊˈkeɪ"); put("ok", "ˌoʊˈkeɪ")
            put("thanks", "θæŋks"); put("thank", "θæŋk")
            put("please", "pliːz"); put("sorry", "ˈsɑːɹi")
            put("welcome", "ˈwɛlkəm")

            // AI/Tech context
            put("ai", "ˌeɪˈaɪ"); put("machine", "məˈʃiːn")
            put("learning", "ˈlɝːnɪŋ"); put("model", "ˈmɑːdəl")
            put("data", "ˈdeɪtə"); put("system", "ˈsɪstəm")
            put("computer", "kəmˈpjuːtɚ"); put("information", "ˌɪnfɚˈmeɪʃən")
            put("privacy", "ˈpɹaɪvəsi"); put("local", "ˈloʊkəl")
            put("device", "dɪˈvaɪs"); put("assistant", "əˈsɪstənt")

            // Common contractions
            put("don't", "doʊnt"); put("can't", "kænt")
            put("won't", "woʊnt"); put("isn't", "ˈɪzənt")
            put("it's", "ɪts"); put("i'm", "aɪm")
            put("i'll", "aɪl"); put("i've", "aɪv")
            put("that's", "ðæts"); put("there's", "ðɛɹz")
            put("what's", "wʌts"); put("let's", "lɛts")
            put("didn't", "ˈdɪdənt"); put("doesn't", "ˈdʌzənt")
            put("couldn't", "ˈkʊdənt"); put("wouldn't", "ˈwʊdənt")
            put("shouldn't", "ˈʃʊdənt")
        }

        /**
         * Basic English letter → IPA approximation for unknown words.
         * Not accurate, but produces recognizable English phonemes.
         */
        private val LETTER_TO_IPA: Map<Char, String> = mapOf(
            'a' to "æ", 'b' to "b", 'c' to "k", 'd' to "d",
            'e' to "ɛ", 'f' to "f", 'g' to "ɡ", 'h' to "h",
            'i' to "ɪ", 'j' to "ʤ", 'k' to "k", 'l' to "l",
            'm' to "m", 'n' to "n", 'o' to "ɑː", 'p' to "p",
            'q' to "k", 'r' to "ɹ", 's' to "s", 't' to "t",
            'u' to "ʌ", 'v' to "v", 'w' to "w", 'x' to "ks",
            'y' to "j", 'z' to "z"
        )

        /**
         * Common English digraph → IPA mappings for fallback G2P.
         */
        private val DIGRAPH_TO_IPA: Map<String, String> = mapOf(
            "th" to "θ", "sh" to "ʃ", "ch" to "ʧ", "ng" to "ŋ",
            "ph" to "f", "wh" to "w", "ck" to "k", "gh" to "",
            "ee" to "iː", "oo" to "uː", "ea" to "iː", "ou" to "aʊ",
            "ai" to "eɪ", "ay" to "eɪ", "oi" to "ɔɪ", "oy" to "ɔɪ",
            "ow" to "oʊ", "aw" to "ɔː", "au" to "ɔː",
            "er" to "ɝː", "ir" to "ɝː", "ur" to "ɝː",
            "ar" to "ɑːɹ", "or" to "ɔːɹ",
            "tion" to "ʃən", "sion" to "ʒən",
            "ight" to "aɪt", "ough" to "oʊ",
        )
    }

    /**
     * Convert text to Kokoro token IDs.
     *
     * @param text Input text to phonemize
     * @return IntArray of Kokoro token IDs, empty if text is blank
     */
    fun phonemize(text: String): IntArray {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return intArrayOf()

        val ipaString = textToIpa(normalized)
        return ipaToTokenIds(ipaString)
    }

    /**
     * Convert English text to IPA phoneme string.
     */
    private fun textToIpa(text: String): String = buildString {
        val words = text.split(Regex("\\s+"))

        words.forEachIndexed { index, word ->
            if (index > 0) append(' ')

            // Separate trailing punctuation
            val (cleanWord, punctuation) = splitPunctuation(word)

            if (cleanWord.isNotEmpty()) {
                // Try dictionary first
                val ipa = IPA_DICT[cleanWord]
                if (ipa != null) {
                    append(ipa)
                } else {
                    // Fallback: rule-based G2P
                    append(wordToIpa(cleanWord))
                }
            }

            // Append punctuation as-is (Kokoro vocab includes , . ! ?)
            append(punctuation)
        }
    }

    /**
     * Split a word into (letters, trailing punctuation).
     */
    private fun splitPunctuation(word: String): Pair<String, String> {
        val letters = word.takeWhile { it.isLetter() || it == '\'' }
        val punct = word.drop(letters.length)
        return letters to punct
    }

    /**
     * Rule-based grapheme-to-IPA for unknown words.
     */
    private fun wordToIpa(word: String): String = buildString {
        var i = 0
        while (i < word.length) {
            // Try 4-char patterns (tion, sion, ight, ough)
            if (i + 3 < word.length) {
                val quad = word.substring(i, i + 4)
                val ipa = DIGRAPH_TO_IPA[quad]
                if (ipa != null) {
                    append(ipa)
                    i += 4
                    continue
                }
            }

            // Try 3-char patterns (igh)
            if (i + 2 < word.length) {
                val tri = word.substring(i, i + 3)
                val ipa = DIGRAPH_TO_IPA[tri]
                if (ipa != null) {
                    append(ipa)
                    i += 3
                    continue
                }
            }

            // Try 2-char patterns
            if (i + 1 < word.length) {
                val digraph = word.substring(i, i + 2)
                val ipa = DIGRAPH_TO_IPA[digraph]
                if (ipa != null) {
                    append(ipa)
                    i += 2
                    continue
                }
            }

            // Silent e at end of word
            if (word[i] == 'e' && i == word.length - 1 && word.length > 2) {
                i++
                continue
            }

            // Single character
            val ch = word[i]
            if (ch.isLetter()) {
                append(LETTER_TO_IPA[ch] ?: ch.toString())
            }
            i++
        }
    }

    /**
     * Convert IPA string to Kokoro token IDs.
     *
     * Maps each character through the Kokoro vocabulary.
     * Characters not in vocab are skipped.
     */
    private fun ipaToTokenIds(ipa: String): IntArray {
        val tokens = mutableListOf<Int>()

        for (ch in ipa) {
            val tokenId = KOKORO_VOCAB[ch]
            if (tokenId != null) {
                tokens.add(tokenId)
            }
            // Skip characters not in Kokoro vocab
        }

        return tokens.toIntArray()
    }
}
