package app.m1k3.ai.domain.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class VoiceTest {

    @Test fun `Daniel has correct id`() = assertEquals("bm_daniel", Voice.Kokoro.Daniel.id)
    @Test fun `Daniel display name`() = assertEquals("Daniel", Voice.Kokoro.Daniel.displayName)
    @Test fun `Bella has correct id`() = assertEquals("af_bella", Voice.Kokoro.Bella.id)
    @Test fun `Bella display name`() = assertEquals("Bella", Voice.Kokoro.Bella.displayName)
    @Test fun `Emma has correct id`() = assertEquals("bf_emma", Voice.Kokoro.Emma.id)
    @Test fun `Emma display name`() = assertEquals("Emma", Voice.Kokoro.Emma.displayName)
    @Test fun `default is Daniel`() = assertEquals(Voice.Kokoro.Daniel, Voice.default)
    @Test fun `default is Kokoro`() { assertIs<Voice.Kokoro>(Voice.default) }
    @Test fun `all returns 3 voices`() = assertEquals(3, Voice.all().size)
    @Test fun `all contains Daniel`() = assertTrue(Voice.all().contains(Voice.Kokoro.Daniel))
    @Test fun `all contains Bella`() = assertTrue(Voice.all().contains(Voice.Kokoro.Bella))
    @Test fun `all contains Emma`() = assertTrue(Voice.all().contains(Voice.Kokoro.Emma))
    @Test fun `findById Daniel`() = assertEquals(Voice.Kokoro.Daniel, Voice.findById("bm_daniel"))
    @Test fun `findById Bella`() = assertEquals(Voice.Kokoro.Bella, Voice.findById("af_bella"))
    @Test fun `findById Emma`() = assertEquals(Voice.Kokoro.Emma, Voice.findById("bf_emma"))
    @Test fun `findById unknown`() = assertEquals(null, Voice.findById("xx_unknown"))
    @Test fun `all voices have ids`() { Voice.all().forEach { assertTrue(it.id.isNotEmpty()) } }
}
