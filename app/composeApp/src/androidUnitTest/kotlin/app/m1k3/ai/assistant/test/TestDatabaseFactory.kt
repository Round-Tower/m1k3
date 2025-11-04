package app.m1k3.ai.assistant.test

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.m1k3.ai.assistant.database.MaDatabase

/**
 * Test Database Factory - Android Unit Test Implementation (JDBC)
 *
 * Provides fast in-memory SQLite databases for Android unit tests.
 * Uses JDBC driver since Android unit tests run on JVM (not on device).
 *
 * Note: This is for local unit tests (testDebugUnitTest).
 * For instrumented tests running on device, use AndroidSqliteDriver in androidTest source set.
 */
actual object TestDatabaseFactory {

    /**
     * Create an in-memory SQLite database for testing (Android unit test implementation).
     *
     * Uses JDBC SQLite driver since Android unit tests run on JVM with Robolectric.
     * Database is fully initialized with schema and ready to use.
     *
     * @return MaDatabase instance backed by in-memory JDBC SQLite
     */
    actual fun createInMemoryDatabase(): MaDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Create schema
        MaDatabase.Schema.create(driver)

        return MaDatabase(driver)
    }
}
