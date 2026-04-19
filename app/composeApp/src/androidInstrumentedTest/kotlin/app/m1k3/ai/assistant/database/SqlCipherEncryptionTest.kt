package app.m1k3.ai.assistant.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proof that SQLCipher actually encrypts the on-disk database file.
 *
 * EncryptedDatabaseTest covers the passphrase plumbing (Keystore → MasterKey
 * → EncryptedSharedPreferences). This test covers the *other half*: once the
 * driver writes through SupportOpenHelperFactory(passphrase), does the file
 * on disk actually look encrypted, or are we still writing plaintext pages?
 *
 * Two assertions:
 *   1. File does not begin with the SQLite magic "SQLite format 3\u0000"
 *      (unencrypted SQLite files always do; SQLCipher pages are encrypted
 *      from byte 0).
 *   2. A sentinel string we insert into the DB is not recoverable via a
 *      plain byte-scan of the file (encryption scrambles it).
 *
 * If this test ever goes red, the Settings-screen privacy claim becomes a
 * lie — fix the driver wire-up in AndroidDatabaseFactory, don't silence the
 * test.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherEncryptionTest {
    private lateinit var context: android.content.Context
    private lateinit var factory: AndroidDatabaseFactory

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DatabaseConfig.DATABASE_NAME)
        factory = AndroidDatabaseFactory(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DatabaseConfig.DATABASE_NAME)
    }

    @Test
    fun encryptedDbFileHasNoPlaintextSqliteHeader() {
        // Open via the production code path so we really exercise the
        // SupportOpenHelperFactory wire-up, not a direct SQLCipher call.
        val driver = factory.buildEncryptedDriver()
        // Touch the driver so pages are materialised on disk. A simple PRAGMA
        // forces the opener to run and create the file; no schema operations
        // needed.
        driver.executeQuery(null, "PRAGMA user_version;", { cursor -> cursor.next() }, 0)
        driver.close()

        val dbFile = context.getDatabasePath(DatabaseConfig.DATABASE_NAME)
        assertTrue(dbFile.exists(), "DB file should exist after driver opens it")
        assertTrue(dbFile.length() > 0, "DB file should have been written to disk")

        val header = dbFile.inputStream().use { it.readNBytes(16) }
        val plaintextSqliteMagic =
            byteArrayOf(
                0x53,
                0x51,
                0x4c,
                0x69,
                0x74,
                0x65,
                0x20,
                0x66,
                0x6f,
                0x72,
                0x6d,
                0x61,
                0x74,
                0x20,
                0x33,
                0x00,
            )
        assertFalse(
            header.contentEquals(plaintextSqliteMagic),
            "DB file starts with plaintext SQLite header — SQLCipher is NOT encrypting. " +
                "First 16 bytes: ${header.joinToString(" ") { "%02x".format(it) }}",
        )
    }

    @Test
    fun sentinelStringIsNotRecoverableFromRawDbBytes() {
        val sentinel = "m1k3-sqlcipher-sentinel-${System.nanoTime()}"

        val driver = factory.buildEncryptedDriver()
        // Use a plain throwaway table so we don't need the full MaDatabase
        // schema loaded for this test. SQLCipher still encrypts user pages.
        driver.execute(null, "CREATE TABLE test_sentinel(value TEXT NOT NULL);", 0)
        driver.execute(null, "INSERT INTO test_sentinel(value) VALUES (?);", 1) {
            bindString(0, sentinel)
        }
        driver.close()

        val dbFile = context.getDatabasePath(DatabaseConfig.DATABASE_NAME)
        val raw = dbFile.readBytes()
        val found = String(raw, Charsets.ISO_8859_1).contains(sentinel)
        assertFalse(
            found,
            "Sentinel string '$sentinel' is present verbatim in the raw DB file — " +
                "pages are being written unencrypted. ",
        )
    }
}
