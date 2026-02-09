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
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.logging.Logger
import javax.inject.Inject

@ProvidedAutoMigrationSpec
@DeleteColumn(tableName = "collection", columnName = "owner")
class AutoMigration12 @Inject constructor(
    @ApplicationContext val context: Context,
    val logger: Logger
): AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        logger.info("Database update to v12, refreshing services to get display names of owners")
        db.query("SELECT id FROM service", arrayOf()).use { cursor ->
            while (cursor.moveToNext()) {
                val serviceId = cursor.getLong(0)
                RefreshCollectionsWorker.enqueue(context, serviceId)
            }
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AutoMigrationModule {
        @Binds @IntoSet
        abstract fun provide(impl: AutoMigration12): AutoMigrationSpec
    }

}