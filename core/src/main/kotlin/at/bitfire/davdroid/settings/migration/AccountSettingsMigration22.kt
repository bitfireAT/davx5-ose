/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.provider.CalendarContract.Calendars
import androidx.annotation.OpenForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.resource.local.SyncState
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * The per-calendar sync state used to be stored in [Calendars.CAL_SYNC1]. That column now contains
 * the collection URL, and the sync state lives in [Calendars.CAL_SYNC2].
 *
 * This migration moves the legacy sync state to the new column so that it isn't lost (which would
 * cause a full re-sync). The URL column is filled again by the next collection update.
 */
class AccountSettingsMigration22 @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val calendarStore: LocalCalendarStore
): AccountSettingsMigration {

    override fun migrate(accountId: AccountId, store: AccountSettingsStore) {
        val account = androidAccountManager.getAndroidAccount(accountId)
        migrateCalendars(account)
    }

    @OpenForTesting
    internal fun migrateCalendars(account: Account) {
        calendarStore.acquireLocalStorageClient()?.use { client ->
            val calendarProvider = AndroidCalendarProvider(account, client)
            for (calendar in calendarProvider.findCalendars()) {
                val legacySyncState = client.query(
                    url = calendarProvider.calendarUri(calendar.id),
                    projection = arrayOf(Calendars.CAL_SYNC1)
                )?.use { cursor ->
                    if (cursor.moveToNext()) cursor.getString(0) else null
                }

                // Only move values that really are a sync state (not, for instance, an already written URL)
                if (SyncState.fromString(legacySyncState) == null)
                    continue

                calendarProvider.updateCalendar(calendar.id, contentValuesOf(
                    Calendars.CAL_SYNC1 to null,
                    Calendars.CAL_SYNC2 to legacySyncState
                ))
            }
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(22)
        abstract fun provide(impl: AccountSettingsMigration22): AccountSettingsMigration
    }

}
