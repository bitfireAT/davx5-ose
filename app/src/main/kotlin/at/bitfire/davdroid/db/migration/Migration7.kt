/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration

val Migration7 = Migration(6, 7) { db ->
    db.execSQL("ALTER TABLE homeset ADD COLUMN privBind INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE homeset ADD COLUMN displayName TEXT DEFAULT NULL")
}