package app.m1k3.ai.assistant.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import org.koin.dsl.module

/**
 * iOS platform module
 *
 * Provides iOS-specific dependencies:
 * - SQLDelight Native driver
 */
actual val platformModule = module {
    /**
     * DatabaseFactory for iOS
     *
     * Uses NativeSqliteDriver with in-memory or file-based database.
     */
    single {
        DatabaseFactory(
            driver = NativeSqliteDriver(
                schema = MaDatabase.Schema,
                name = "ma_ai.db"
            )
        )
    }
}
