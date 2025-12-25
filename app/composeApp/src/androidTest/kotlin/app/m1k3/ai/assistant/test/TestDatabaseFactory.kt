package app.m1k3.ai.assistant.test

import app.m1k3.ai.assistant.database.MaDatabase

/**
 * Test Database Factory - Cross-platform test database creation
 *
 * Uses expect/actual pattern to provide platform-specific in-memory databases:
 * - JVM: JDBC SQLite driver (fast, hermetic)
 * - Android: Android SQLite driver (matches production)
 */
expect object TestDatabaseFactory {
    /**
     * Create an in-memory database for testing.
     *
     * The database is automatically populated with schema and ready for use.
     * Each call creates a fresh database instance with no persisted data.
     *
     * @return MaDatabase configured for testing
     */
    fun createInMemoryDatabase(): MaDatabase
}
