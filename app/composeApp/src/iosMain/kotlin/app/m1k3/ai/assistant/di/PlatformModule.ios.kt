package app.m1k3.ai.assistant.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import org.koin.dsl.module

/**
 * iOS platform module.
 *
 * **DB encryption TODO:** Android ships SQLCipher (see AndroidDatabaseFactory);
 * iOS still uses plain NativeSqliteDriver. Privacy here rests on iOS Data
 * Protection (file-level, NSFileProtectionComplete) plus the app-private
 * container. UI copy and the system-knowledge JSON that the LLM reads
 * already claim "SQLCipher AES-256" — on iOS that claim is aspirational
 * until we wire SQLCipher via the CocoaPods `SQLCipher` pod and swap this
 * driver for a SupportFactory-based native driver. Parked because iOS
 * itself is parked (see project-memory.md).
 */
actual val platformModule =
    module {
        single {
            DatabaseFactory(
                driver =
                    NativeSqliteDriver(
                        schema = MaDatabase.Schema,
                        name = "ma_ai.db",
                    ),
            )
        }
    }
