/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.synctools.vcard.GroupMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Immutable snapshot of the [at.bitfire.davdroid.settings.AccountSettings] that are relevant for a sync run.
 *
 * Should be created once at the start of a sync run instead of querying [at.bitfire.davdroid.settings.AccountSettings]
 * again and again during the same sync. This way, settings can't change in the middle of
 * a sync run, and expensive/blocking [at.bitfire.davdroid.settings.AccountSettings] access is reduced.
 */
data class SyncSettingsSnapshot(
    // AccountSettings (same order)
    val syncWifiOnly: Boolean,
    val syncWifiOnlySSIDs: List<String>?,
    val ignoreVpns: Boolean,
    val timeRangePastDays: Int?,
    val defaultAlarm: Int?,
    val eventColors: Boolean,
    val groupMethod: GroupMethod
) {

    class Factory @Inject constructor(
        private val accountSettingsFactory: AccountSettings.Factory,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher
    ) {

        suspend fun create(account: Account): SyncSettingsSnapshot =
            withContext(ioDispatcher) {
                val settings = accountSettingsFactory.create(account)
                SyncSettingsSnapshot(
                    syncWifiOnly = settings.getSyncWifiOnly(),
                    syncWifiOnlySSIDs = settings.getSyncWifiOnlySSIDs(),
                    ignoreVpns = settings.getIgnoreVpns(),
                    timeRangePastDays = settings.getTimeRangePastDays(),
                    defaultAlarm = settings.getDefaultAlarm(),
                    eventColors = settings.getEventColors(),
                    groupMethod = settings.getGroupMethod()
                )
            }

    }

}
