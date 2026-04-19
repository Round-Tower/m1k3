package app.m1k3.ai.assistant.privacy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Privacy trip-wire: no production code writes debug-prompt dumps to disk.
 *
 * Background: commit `cc649e06` removed a `debug_prompt.txt` dump from
 * `LlamaCppEngine.kt` that had been leaking every inference prompt to
 * app-private storage. That was private-to-the-process but still on disk,
 * which violates the "your device is the cloud" privacy narrative — backups,
 * logcat tooling, or accidental `adb pull` would surface content the model
 * saw.
 *
 * Invariant: no production Kotlin file in `commonMain` / `androidMain`
 * references a path matching `debug_*.txt`. If one resurfaces, this test
 * breaks the build before merge.
 *
 * Intentionally narrow: it matches the specific pattern of the previous
 * leak rather than "any file write", so it stays useful (tight signal,
 * no churn from legit disk I/O).
 */
class NoDebugDumpsTest {
    @Test
    fun `production code does not reference debug dump paths`() {
        val sourceRoots =
            listOf(
                File("src/commonMain/kotlin"),
                File("src/androidMain/kotlin"),
            )

        val existing = sourceRoots.filter { it.isDirectory }
        assertTrue(
            existing.isNotEmpty(),
            "No source roots found — cwd=${File(".").absolutePath}, expected one of $sourceRoots",
        )

        val offenders = mutableListOf<String>()
        val leakPattern = Regex("""["']debug_[A-Za-z0-9_]*\.txt["']""")

        existing.forEach { root ->
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (leakPattern.containsMatchIn(line)) {
                                offenders += "${file.relativeTo(File("."))}:${idx + 1}: $line"
                            }
                        }
                    }
                }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "Production code references debug dump path(s) — privacy trip-wire.\n" +
                    "Remove the dump or route it through an opt-in debug-only channel.\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
    }
}
