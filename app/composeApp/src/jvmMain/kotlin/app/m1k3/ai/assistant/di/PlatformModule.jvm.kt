package app.m1k3.ai.assistant.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import org.koin.dsl.module

/**
 * JVM platform module
 *
 * Provides JVM-specific dependencies:
 * - SQLDelight JDBC driver (for desktop/testing)
 */
actual val platformModule = module {
    /**
     * DatabaseFactory for JVM
     *
     * Uses JdbcSqliteDriver with in-memory database for testing.
     */
    single {
        DatabaseFactory(
            driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                MaDatabase.Schema.create(it)
            }
        )
    }
}
