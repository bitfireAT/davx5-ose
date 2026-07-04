/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentUris
import android.content.Context
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.TaskProvider
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import org.dmfs.tasks.contract.TaskContract
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
        val providerName = TaskProvider.ProviderName.OpenTasks
        TaskProvider.acquireRecentClient(context, providerName)?.use { client ->
            // ETag is now in sync_version instead of sync1
            // UID  is now in _uid         instead of sync2
            val tasksUri = TaskContract.Tasks.getContentUri(providerName.authority)!!
            client.query(
                tasksUri.asSyncAdapter(account),
                arrayOf(TaskContract.Tasks._ID, TaskContract.Tasks.SYNC1, TaskContract.Tasks.SYNC2),
                "${TaskContract.Tasks.ACCOUNT_TYPE}=? AND ${TaskContract.Tasks.ACCOUNT_NAME}=?",
                arrayOf(account.type, account.name), null)!!.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val eTag = cursor.getString(1)
                    val uid = cursor.getString(2)
                    val values = contentValuesOf(
                        TaskContract.Tasks._UID to uid,
                        TaskContract.Tasks.SYNC_VERSION to eTag,
                        TaskContract.Tasks.SYNC1 to null,
                        TaskContract.Tasks.SYNC2 to null
                    )
                    logger.fine("Updating task $id: $values")
                    client.update(
                        ContentUris.withAppendedId(tasksUri, id).asSyncAdapter(account),
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