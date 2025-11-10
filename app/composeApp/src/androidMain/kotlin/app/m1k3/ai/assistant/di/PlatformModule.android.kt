package app.m1k3.ai.assistant.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import org.koin.dsl.module

/**
 * Android platform module
 *
 * Provides Android-specific dependencies:
 * - SQLDelight Android driver
 * - Android Context
 */
actual val platformModule = module {
    /**
     * DatabaseFactory for Android
     *
     * Uses AndroidSqliteDriver with application context.
     */
    single {
        DatabaseFactory(
            driver = AndroidSqliteDriver(
                schema = MaDatabase.Schema,
                context = get<Context>(),
                name = "ma_ai.db"
            )
        )
    }
}
