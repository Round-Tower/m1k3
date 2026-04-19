package app.m1k3.ai.assistant.database

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * Android Database Factory — SQLCipher-backed encrypted DB at rest.
 *
 * The SQLite DB is opened via SQLCipher's [SupportOpenHelperFactory], which
 * encrypts every page on disk using AES-256. The passphrase is a 256-bit
 * random value generated once on first launch and stored in
 * [EncryptedSharedPreferences], which wraps it with a Keystore-backed
 * [MasterKey]. The raw DB file is never readable off-device (e.g. via `adb
 * run-as` or device backup) — a stolen encrypted prefs blob without the
 * device's Keystore is useless.
 *
 * Layer cake:
 *   - DB pages  : AES-256 via SQLCipher (this class).
 *   - Passphrase: EncryptedSharedPreferences (AES-256-GCM values, SIV keys).
 *   - Master key: Android Keystore (hardware-backed where available).
 *   - Fallback  : Android app-private storage sandbox + device FDE.
 */
@Suppress("DEPRECATION")
class AndroidDatabaseFactory(
    private val context: Context,
) : OldDatabaseFactory {
    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "ma_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Build an encrypted SqlDriver using the stored passphrase, generating
     * one on first launch if needed. This is the convenience Koin calls.
     */
    fun buildEncryptedDriver(): SqlDriver = createDriver(getDatabasePassphrase())

    override fun createDriver(passphrase: String): SqlDriver {
        // sqlcipher-android 4.x does not auto-load its native lib on class-load
        // (unlike the older android-database-sqlcipher artifact). The caller
        // must loadLibrary before opening any connection. System.loadLibrary
        // is idempotent — the JVM caches the successful load, so this is a
        // per-process one-liner, not per-call work.
        System.loadLibrary("sqlcipher")

        val factory = SupportOpenHelperFactory(passphrase.toByteArray())
        return AndroidSqliteDriver(
            schema = MaDatabase.Schema,
            context = context,
            name = DatabaseConfig.DATABASE_NAME,
            factory = factory,
        )
    }

    override fun getDatabasePassphrase(): String {
        var passphrase = encryptedPrefs.getString(DatabaseConfig.PASSPHRASE_KEY, null)

        if (passphrase == null) {
            // First launch: generate new passphrase
            passphrase = generateAndStorePassphrase()
        }

        return passphrase
    }

    override fun generateAndStorePassphrase(): String {
        // Generate cryptographically secure random passphrase
        val random = SecureRandom()
        val passphraseBytes = ByteArray(32) // 256 bits
        random.nextBytes(passphraseBytes)

        // Encode as Base64 for storage
        val passphrase =
            Base64.encodeToString(
                passphraseBytes,
                Base64.NO_WRAP or Base64.NO_PADDING,
            )

        // Store encrypted passphrase
        encryptedPrefs
            .edit()
            .putString(DatabaseConfig.PASSPHRASE_KEY, passphrase)
            .apply()

        return passphrase
    }
}
