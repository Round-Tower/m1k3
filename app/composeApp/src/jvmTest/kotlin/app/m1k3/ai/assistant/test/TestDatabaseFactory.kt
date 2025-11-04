package app.m1k3.ai.assistant.test

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.m1k3.ai.assistant.database.MaDatabase

/**
 * Test Database Factory - JVM Implementation (JDBC SQLite Driver)
 *
 * Provides fast in-memory SQLite databases for JVM unit tests.
 * Uses JDBC driver for hermetic, fast test execution.
 */
actual object TestDatabaseFactory {

    /**
     * Create an in-memory SQLite database for testing (JVM implementation).
     *
     * Uses JDBC SQLite driver for fast, hermetic test execution.
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
