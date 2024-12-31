/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db.migration

import android.content.Context
import androidx.room.DeleteColumn
import androidx.room.ProvidedAutoMigrationSpec
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import java.util.logging.Logger

@ProvidedAutoMigrationSpec
@DeleteColumn(tableName = "collection", columnName = "owner")
class AutoMigration12(val context: Context): AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        Logger.getGlobal().info("Database update to v12, refreshing services to get display names of owners")
        db.query("SELECT id FROM service", arrayOf()).use { cursor ->
            while (cursor.moveToNext()) {
                val serviceId = cursor.getLong(0)
                RefreshCollectionsWorker.enqueue(context, serviceId)
            }
        }
    }

}