/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.ResyncType
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState

class SetAccountSettingsUseCase @AssistedInject constructor(
    @Assisted private val accountId: AccountId,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val syncWorkerManager: SyncWorkerManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) {
    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): SetAccountSettingsUseCase
    }

    private val accountSettings by lazy { accountSettingsFactory.create(accountId.toAndroidAccount()) }


    suspend fun setCredentials(credentials: Credentials) = runInAppScope {
        accountSettings.credentials(credentials)
    }

    suspend fun setAuthState(authState: AuthState) = runInAppScope {
        accountSettings.updateAuthState(authState)
    }

    suspend fun setContactsSyncInterval(syncInterval: Long) = runInAppScope {
        accountSettings.setSyncInterval(SyncDataType.CONTACTS, syncInterval.takeUnless { it == -1L })
    }

    suspend fun setCalendarSyncInterval(syncInterval: Long) = runInAppScope {
        accountSettings.setSyncInterval(SyncDataType.EVENTS, syncInterval.takeUnless { it == -1L })
    }

    suspend fun setTasksSyncInterval(syncInterval: Long) = runInAppScope {
        accountSettings.setSyncInterval(SyncDataType.TASKS, syncInterval.takeUnless { it == -1L })
    }

    suspend fun setSyncWiFiOnly(wifiOnly: Boolean) = runInAppScope {
        accountSettings.setSyncWiFiOnly(wifiOnly)
    }

    suspend fun setSyncWifiOnlySSIDs(ssids: List<String>?) = runInAppScope {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
    }

    suspend fun setIgnoreVpns(ignoreVpns: Boolean) = runInAppScope {
        accountSettings.setIgnoreVpns(ignoreVpns)
    }

    suspend fun setTimeRangePastDays(days: Int?) = runInAppScope {
        accountSettings.setTimeRangePastDays(days)

        // If the new setting is a certain number of days, no full resync is required, because every sync will cause a
        // REPORT calendar-query with the given number of days. However, if the new setting is "all events", collection
        // sync may/should be used, so the last sync-token has to be reset, which is done by setting fullResync=true.
        resync(SyncDataType.EVENTS, resync = if (days == null) ResyncType.RESYNC_ENTRIES else ResyncType.RESYNC_LIST)
    }

    suspend fun setDefaultAlarm(minBefore: Int?) = runInAppScope {
        accountSettings.setDefaultAlarm(minBefore)

        resync(SyncDataType.EVENTS, ResyncType.RESYNC_ENTRIES)
    }

    suspend fun setManageCalendarColors(manage: Boolean) = runInAppScope {
        accountSettings.setManageCalendarColors(manage)

        resync(SyncDataType.EVENTS, ResyncType.RESYNC_LIST)
        resync(SyncDataType.TASKS, ResyncType.RESYNC_LIST)
    }

    suspend fun setEventColors(manageColors: Boolean) = runInAppScope {
        accountSettings.setEventColors(manageColors)

        resync(SyncDataType.EVENTS, ResyncType.RESYNC_ENTRIES)
    }

    suspend fun setContactGroupMethod(groupMethod: GroupMethod) = runInAppScope {
        accountSettings.setGroupMethod(groupMethod)

        resync(SyncDataType.CONTACTS, ResyncType.RESYNC_ENTRIES)
    }

    /**
     * Run [block] in [appScope] and wait for the operation to complete.
     *
     * If the calling coroutine is canceled, the operation in `appScope` will remain unaffected, i.e. run to completion.
     */
    private suspend fun runInAppScope(block: suspend CoroutineScope.() -> Unit) {
        appScope.launch(context = ioDispatcher, block = block)
            .join()
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param dataType type of data to synchronize
     * @param resync whether only the list of entries (resync) or also all entries themselves (full resync) shall be
     *   downloaded again
     */
    private suspend fun resync(dataType: SyncDataType, resync: ResyncType) {
        syncWorkerManager.enqueueOneTime(accountId.toAndroidAccount(), dataType = dataType, resync = resync)
    }
}
