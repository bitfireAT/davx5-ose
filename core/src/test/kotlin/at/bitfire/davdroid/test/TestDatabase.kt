/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.test

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.migration.AutoMigration12
import at.bitfire.davdroid.db.migration.AutoMigration16
import at.bitfire.davdroid.db.migration.AutoMigration18
import java.util.logging.Logger

/**
 * Creates an in-memory Room database for testing with all required migrations pre-configured.
 * This centralizes the database setup boilerplate that was previously duplicated in multiple test classes.
 */
fun createTestDatabase(): AppDatabase {
    return Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries()
        .addAutoMigrationSpec(AutoMigration18())
        .addAutoMigrationSpec(AutoMigration16())
        .addAutoMigrationSpec(AutoMigration12(ApplicationProvider.getApplicationContext(), Logger.getLogger("test")))
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
