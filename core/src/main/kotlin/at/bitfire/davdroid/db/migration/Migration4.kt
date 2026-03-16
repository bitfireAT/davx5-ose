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

val Migration4 = Migration(3, 4) { db ->
    db.execSQL("ALTER TABLE collections ADD COLUMN forceReadOnly INTEGER DEFAULT 0 NOT NULL")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration4Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration4
}