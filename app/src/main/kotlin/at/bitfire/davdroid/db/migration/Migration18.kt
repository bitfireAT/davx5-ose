/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.room.migration.Migration
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.ical4android.TaskProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Renames syncstats.authority to dataType, and maps values to SyncDataType enum names.
 */
val Migration18 = Migration(17, 18) { db ->
    // Remove orphaned syncstats entries referencing non-existent collections
    // Note: Required because of https://github.com/bitfireAT/davx5-ose/issues/1578
    // Note2: Needs to be done in a manual migration (instead of AutoMigration) because
    // deletion apparently needs to happen before renaming.
    db.execSQL("DELETE FROM syncstats WHERE collectionId NOT IN (SELECT id FROM collection)")

    // Rename column authority to dataType
    // Note: SQLite in older android versions does not support ALTER TABLE RENAME COLUMN
    db.execSQL("CREATE TABLE syncstats_tmp (" +
            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
            "collectionId INTEGER NOT NULL REFERENCES collection(id) ON DELETE CASCADE," +
            "dataType TEXT NOT NULL," + // authority is now called dataType
            "lastSync INTEGER NOT NULL)")
    db.execSQL("INSERT INTO syncstats_tmp(id, collectionId, dataType, lastSync)\n" +
            "SELECT id, collectionId, authority, lastSync\n" +
            "FROM syncstats")
    db.execSQL("DROP TABLE syncstats")
    db.execSQL("ALTER TABLE syncstats_tmp RENAME TO syncstats")

    // Drop old unique index
    db.execSQL("DROP INDEX IF EXISTS index_syncstats_collectionId_authority")

    // Map values to SyncDataType enum names
    val seen = mutableSetOf<Pair<Long, String>>() // (collectionId, dataType)
    db.query(
        "SELECT id, collectionId, dataType, lastSync FROM syncstats ORDER BY lastSync DESC"
    ).use { cursor ->
        val idIndex = cursor.getColumnIndex("id")
        val collectionIdIndex = cursor.getColumnIndex("collectionId")
        val authorityIndex = cursor.getColumnIndex("dataType")

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val collectionId = cursor.getLong(collectionIdIndex)
            val authority = cursor.getString(authorityIndex)

            val dataType = when (authority) {
                ContactsContract.AUTHORITY -> SyncDataType.CONTACTS.name
                CalendarContract.AUTHORITY -> SyncDataType.EVENTS.name
                TaskProvider.ProviderName.JtxBoard.authority,
                TaskProvider.ProviderName.TasksOrg.authority,
                TaskProvider.ProviderName.OpenTasks.authority -> SyncDataType.TASKS.name

                else -> {
                    db.execSQL("DELETE FROM syncstats WHERE id = ?", arrayOf(id))
                    continue
                }
            }

            val keyValue = collectionId to dataType
            if (seen.contains(keyValue)) {
                db.execSQL("DELETE FROM syncstats WHERE id = ?", arrayOf(id))
            } else {
                db.execSQL("UPDATE syncstats SET dataType = ? WHERE id = ?", arrayOf<Any>(dataType, id))
                seen.add(keyValue)
            }
        }
    }

    // Create new unique index
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_syncstats_collectionId_dataType ON syncstats (collectionId, dataType)")
}

@Module
@InstallIn(SingletonComponent::class)
internal object Migration18Module {
    @Provides
    @IntoSet
    fun provide(): Migration = Migration18
}
