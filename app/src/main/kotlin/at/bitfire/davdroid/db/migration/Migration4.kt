/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import androidx.room.migration.Migration

val Migration4 = Migration(3, 4) { db ->
    db.execSQL("ALTER TABLE collections ADD COLUMN forceReadOnly INTEGER DEFAULT 0 NOT NULL")
}