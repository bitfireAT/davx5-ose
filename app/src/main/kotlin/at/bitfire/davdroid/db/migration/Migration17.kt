/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

internal val Migration17 = Migration(16, 17) { db ->
    // Add account table
    db.execSQL("CREATE TABLE account (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
            "name TEXT NOT NULL)")
    db.execSQL("CREATE UNIQUE INDEX index_account_name ON account(name)")

    // Fill account names from services
    db.execSQL("INSERT INTO account (name) SELECT DISTINCT accountName FROM service")

    // AutoMigration18 adds the foreign key of service(accountName)
    // because it's not possible to add it with a simple SQL statement
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration17Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration17
}