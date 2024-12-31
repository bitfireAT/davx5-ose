/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration

val Migration5 = Migration(4, 5) { db ->
    db.execSQL("ALTER TABLE collections ADD COLUMN privWriteContent INTEGER DEFAULT 0 NOT NULL")
    db.execSQL("UPDATE collections SET privWriteContent=NOT readOnly")

    db.execSQL("ALTER TABLE collections ADD COLUMN privUnbind INTEGER DEFAULT 0 NOT NULL")
    db.execSQL("UPDATE collections SET privUnbind=NOT readOnly")

    // there's no DROP COLUMN in SQLite, so just keep the "readOnly" column
}