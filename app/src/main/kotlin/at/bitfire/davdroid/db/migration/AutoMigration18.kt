/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.ical4android.TaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * Renames syncstats.authority to dataType, and maps values to SyncDataType enum names.
 */
@ProvidedAutoMigrationSpec
@RenameColumn(tableName = "syncstats", fromColumnName = "authority", toColumnName = "dataType")
class AutoMigration18 @Inject constructor() : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // Drop old unique index
        db.execSQL("DROP INDEX IF EXISTS index_syncstats_collectionId_authority")

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
                    else -> "UNKNOWN"
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
    abstract class AutoMigrationModule {
        @Binds
        @IntoSet
        abstract fun provide(impl: AutoMigration18): AutoMigrationSpec
    }

}
