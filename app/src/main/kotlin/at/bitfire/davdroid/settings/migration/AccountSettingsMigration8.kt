/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.CommonSyncColumns
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class AccountSettingsMigration8 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
): AccountSettingsMigration {

    /**
     * There is a mistake in this method. [TaskContract.Tasks.SYNC_VERSION] is used to store the
     * SEQUENCE and should not be used for the eTag.
     */
    override fun migrate(account: Account) {
        TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.use { provider ->
            // ETag is now in sync_version instead of sync1
            // UID  is now in _uid         instead of sync2
            provider.client.query(provider.tasksUri().asSyncAdapter(account),
                arrayOf(TaskContract.Tasks._ID, TaskContract.Tasks.SYNC1, TaskContract.Tasks.SYNC2),
                "${TaskContract.Tasks.ACCOUNT_TYPE}=? AND ${TaskContract.Tasks.ACCOUNT_NAME}=?",
                arrayOf(account.type, account.name), null)!!.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val eTag = cursor.getString(1)
                    val uid = cursor.getString(2)
                    val values = ContentValues(4)
                    values.put(TaskContract.Tasks._UID, uid)
                    values.put(TaskContract.Tasks.SYNC_VERSION, eTag)
                    values.putNull(TaskContract.Tasks.SYNC1)
                    values.putNull(TaskContract.Tasks.SYNC2)
                    logger.log(Level.FINER, "Updating task $id", values)
                    provider.client.update(
                        ContentUris.withAppendedId(provider.tasksUri(), id).asSyncAdapter(account),
                        values, null, null)
                }
            }
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(8)
        abstract fun provide(impl: AccountSettingsMigration8): AccountSettingsMigration
    }

}