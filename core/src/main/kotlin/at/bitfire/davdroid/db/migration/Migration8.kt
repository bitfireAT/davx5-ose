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

val Migration8 = Migration(7, 8) { db ->
    db.execSQL("ALTER TABLE homeset ADD COLUMN personal INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE collection ADD COLUMN homeSetId INTEGER DEFAULT NULL REFERENCES homeset(id) ON DELETE SET NULL")
    db.execSQL("ALTER TABLE collection ADD COLUMN owner TEXT DEFAULT NULL")
    db.execSQL("CREATE INDEX index_collection_homeSetId_type ON collection(homeSetId, type)")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration8Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration8
}