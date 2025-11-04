package app.m1k3.ai.assistant.test

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.m1k3.ai.assistant.database.MaDatabase

/**
 * Test Database Factory - In-Memory Database for Unit Tests
 *
 * Provides simple in-memory SQLite databases for fast unit testing.
 * No encryption needed for test databases.
 *
 * **Usage:**
 * ```kotlin
 * val database = TestDatabaseFactory.createInMemoryDatabase()
 * // Use database for testing
 * ```
 */
object TestDatabaseFactory {

    /**
     * Create an in-memory SQLite database for testing.
     *
     * Database is fully initialized with schema and ready to use.
     * Each call creates a new, independent database instance.
     *
     * @return MaDatabase instance backed by in-memory SQLite
     */
    fun createInMemoryDatabase(): MaDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        // Create schema
        MaDatabase.Schema.create(driver)

        return MaDatabase(driver)
    }

    /**
     * Create and populate a test database with sample data.
     *
     * Useful for integration tests that need pre-populated data.
     *
     * @return MaDatabase with sample data
     */
    fun createPopulatedDatabase(): MaDatabase {
        val database = createInMemoryDatabase()

        // TODO: Add sample data population methods
        // populateSampleProjects(database)
        // populateSampleMessages(database)
        // populateSampleTrivia(database)

        return database
    }
}
