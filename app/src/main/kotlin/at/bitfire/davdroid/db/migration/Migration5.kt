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

internal val Migration5 = Migration(4, 5) { db ->
    db.execSQL("ALTER TABLE collections ADD COLUMN privWriteContent INTEGER DEFAULT 0 NOT NULL")
    db.execSQL("UPDATE collections SET privWriteContent=NOT readOnly")

    db.execSQL("ALTER TABLE collections ADD COLUMN privUnbind INTEGER DEFAULT 0 NOT NULL")
    db.execSQL("UPDATE collections SET privUnbind=NOT readOnly")

    // there's no DROP COLUMN in SQLite, so just keep the "readOnly" column
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration5Module {
    @Provides @IntoSet
    fun provide(): Migration = Migration5
}