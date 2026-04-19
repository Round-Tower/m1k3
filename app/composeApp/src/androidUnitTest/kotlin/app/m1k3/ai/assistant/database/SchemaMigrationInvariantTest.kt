package app.m1k3.ai.assistant.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Schema/migration drift trip-wire.
 *
 * SQLDelight stores migrations as `N.sqm` — file `N.sqm` migrates the DB
 * from version N to N+1. `MaDatabase.Schema.version` is the current version.
 *
 * Invariant: `Schema.version == (max migration N) + 1`.
 *
 * Why it's load-bearing: a version bump without a matching `.sqm` leaves
 * existing-install users stuck (SQLDelight refuses to migrate) AND new
 * installs still work — so the bug only surfaces after release when real
 * users update. This test breaks the build instead.
 *
 * The counterpart — a `.sqm` file past the current version — is an unused
 * migration; we flag that too so drift in either direction fails fast.
 */
class SchemaMigrationInvariantTest {
    @Test
    fun `Schema version matches highest migration file`() {
        val migrationsDir = File(MIGRATIONS_PATH)
        assertTrue(
            migrationsDir.isDirectory,
            "Expected migrations directory at $MIGRATIONS_PATH (cwd=${File(".").absolutePath})",
        )

        val migrationNumbers =
            migrationsDir
                .listFiles { file -> file.extension == "sqm" }
                ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                ?.sorted()
                .orEmpty()

        assertTrue(
            migrationNumbers.isNotEmpty(),
            "No `.sqm` files found under $MIGRATIONS_PATH — did the path move?",
        )

        // SQLDelight convention: migrations start at 1 and go up sequentially.
        val expected = (1..migrationNumbers.last()).toList()
        assertEquals(
            expected,
            migrationNumbers,
            "Migration numbering must be contiguous starting at 1 — found $migrationNumbers",
        )

        val highestMigration = migrationNumbers.last()
        val schemaVersion = MaDatabase.Schema.version

        assertEquals(
            (highestMigration + 1).toLong(),
            schemaVersion,
            "Schema.version=$schemaVersion but highest migration is $highestMigration.sqm. " +
                "Either add a ${highestMigration + 1}.sqm migration or drop the version bump.",
        )
    }

    private companion object {
        // Unit tests run with cwd = module root (`app/composeApp`).
        const val MIGRATIONS_PATH = "src/commonMain/sqldelight/migrations"
    }
}
