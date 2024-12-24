/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentResolver
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.util.logging.Logger
import javax.inject.Inject

/**
 * It seems that somehow some non-CalDAV accounts got OpenTasks syncable, which caused battery problems.
 * Disable it on those accounts for the future.
 */
class AccountSettingsMigration9 @Inject constructor(
    private val db: AppDatabase,
    private val logger: Logger
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
        if (!hasCalDAV && ContentResolver.getIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority) != 0) {
            logger.info("Disabling OpenTasks sync for $account")
            ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0)
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(9)
        abstract fun provide(impl: AccountSettingsMigration9): AccountSettingsMigration
    }

}