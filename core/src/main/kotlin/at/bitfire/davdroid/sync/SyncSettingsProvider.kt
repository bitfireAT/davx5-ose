/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.sync

import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads a [SyncSettings] snapshot from [AccountSettings].
 */
class SyncSettingsProvider @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun create(accountSettings: AccountSettings): SyncSettings =
        withContext(ioDispatcher) {
            SyncSettings(
                syncWifiOnly = accountSettings.getSyncWifiOnly(),
                syncWifiOnlySSIDs = accountSettings.getSyncWifiOnlySSIDs(),
                ignoreVpns = accountSettings.getIgnoreVpns(),
                timeRangePastDays = accountSettings.getTimeRangePastDays(),
                defaultAlarm = accountSettings.getDefaultAlarm(),
                eventColors = accountSettings.getEventColors(),
                groupMethod = accountSettings.getGroupMethod()
            )
        }

}
