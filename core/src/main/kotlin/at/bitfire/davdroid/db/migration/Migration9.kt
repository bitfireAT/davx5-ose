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

val Migration9 = Migration(8, 9) { db ->
    db.execSQL("CREATE TABLE syncstats (" +
            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
            "collectionId INTEGER NOT NULL REFERENCES collection(id) ON DELETE CASCADE," +
            "authority TEXT NOT NULL," +
            "lastSync INTEGER NOT NULL)")
    db.execSQL("CREATE UNIQUE INDEX index_syncstats_collectionId_authority ON syncstats(collectionId, authority)")

    db.execSQL("CREATE INDEX index_collection_url ON collection(url)")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration9Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration9
}