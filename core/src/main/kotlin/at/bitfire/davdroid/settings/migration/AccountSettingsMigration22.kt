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
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlin.use

class AccountSettingsMigration22 @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val calendarStore: LocalCalendarStore
): AccountSettingsMigration {

    /**
     * Cancel any possibly forever pending account syncs of the different authorities
     */
    override fun migrate(accountId: AccountId, store: AccountSettingsStore) {
        val account = androidAccountManager.getAndroidAccount(accountId)

        migrateCalendars(account)
    }

    @OpenForTesting
    internal fun migrateCalendars(account: Account) {
        calendarStore.acquireLocalStorageClient()?.use { client ->
            val calendarProvider = AndroidCalendarProvider(account, client)
            // Iterate all calendars
            for (calendar in calendarProvider.findCalendars()) {
                // Query the legacy sync state value, and move it to CAL_SYNC2
                val newContentValues = client.query(
                    url = calendarProvider.calendarUri(calendar.id),
                    projection = arrayOf(Calendars.CAL_SYNC1),
                )?.use { cursor ->
                    // If there's no row, or the old sync state is null, continue the loop
                    if (!cursor.moveToNext() || cursor.isNull(0)) continue
                    val legacySyncState = cursor.getString(0)
                    contentValuesOf(
                        // Move CAL_SYNC1 to CAL_SYNC2, and clear CAL_SYNC1. Next sync will fill CAL_SYNC1 with new data
                        Calendars.CAL_SYNC1 to null,
                        Calendars.CAL_SYNC2 to legacySyncState
                    )
                } ?: continue

                // Perform the update in the provider
                calendarProvider.updateCalendar(calendar.id, newContentValues)
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
