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

val Migration2 = Migration(1, 2) { db ->
    db.execSQL("ALTER TABLE collections ADD COLUMN type TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE collections ADD COLUMN source TEXT DEFAULT NULL")
    db.execSQL("UPDATE collections SET type=(" +
            "SELECT CASE service WHEN ? THEN ? ELSE ? END " +
            "FROM services WHERE _id=collections.serviceID" +
            ")",
        arrayOf("caldav", "CALENDAR", "ADDRESS_BOOK"))
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration2Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration2
}