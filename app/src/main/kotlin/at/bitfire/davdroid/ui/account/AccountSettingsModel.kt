/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.BaseSyncWorker
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = AccountSettingsModel.Factory::class)
class AccountSettingsModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext val context: Context,
    db: AppDatabase,
    private val settings: SettingsManager,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: TasksAppManager
): ViewModel(), SettingsManager.OnChangeListener {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountSettingsModel
    }

    // settings
    data class UiState(
        val hasContactsSync: Boolean = false,
        val syncIntervalContacts: Long? = null,
        val hasCalendarsSync: Boolean = false,
        val syncIntervalCalendars: Long? = null,
        val hasTasksSync: Boolean = false,
        val syncIntervalTasks: Long? = null,

        val syncWifiOnly: Boolean = false,
        val syncWifiOnlySSIDs: List<String>? = null,
        val ignoreVpns: Boolean = false,

        val credentials: Credentials = Credentials(),

        val timeRangePastDays: Int? = null,
        val defaultAlarmMinBefore: Int? = null,
        val manageCalendarColors: Boolean = false,
        val eventColors: Boolean = false,

        val contactGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val serviceDao = db.serviceDao()
    private val tasksProvider
        get() = tasksAppManager.currentProvider()

    /**
     * Only acquire account settings on a worker thread!
     */
    private val accountSettings by lazy { accountSettingsFactory.create(account) }


    init {
        settings.addOnChangeListener(this)
        viewModelScope.launch {
            reload()
        }
    }

    override fun onCleared() {
        super.onCleared()
        settings.removeOnChangeListener(this)
    }

    override fun onSettingsChanged() {
        viewModelScope.launch {
            reload()
        }
    }

    private suspend fun reload() = withContext(Dispatchers.Default) {
        val hasContactsSync = serviceDao.getByAccountAndType(account.name, Service.TYPE_CARDDAV) != null
        val hasCalendarSync = serviceDao.getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
        val hasTasksSync = hasCalendarSync && tasksProvider != null

        _uiState.value = UiState(
            hasContactsSync = hasContactsSync,
            syncIntervalContacts = accountSettings.getSyncInterval(SyncDataType.CONTACTS),
            hasCalendarsSync = hasCalendarSync,
            syncIntervalCalendars = accountSettings.getSyncInterval(SyncDataType.EVENTS),
            hasTasksSync = hasTasksSync,
            syncIntervalTasks = accountSettings.getSyncInterval(SyncDataType.TASKS),

            syncWifiOnly = accountSettings.getSyncWifiOnly(),
            syncWifiOnlySSIDs = accountSettings.getSyncWifiOnlySSIDs(),
            ignoreVpns = accountSettings.getIgnoreVpns(),

            credentials = accountSettings.credentials(),

            timeRangePastDays = accountSettings.getTimeRangePastDays(),
            defaultAlarmMinBefore = accountSettings.getDefaultAlarm(),
            manageCalendarColors = accountSettings.getManageCalendarColors(),
            eventColors = accountSettings.getEventColors(),

            contactGroupMethod = accountSettings.getGroupMethod(),
        )
    }

    fun updateContactsSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(SyncDataType.CONTACTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateCalendarSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(SyncDataType.EVENTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateTasksSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(SyncDataType.TASKS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setSyncWiFiOnly(wifiOnly)
        reload()
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
        reload()
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setIgnoreVpns(ignoreVpns)
        reload()
    }

    fun updateCredentials(credentials: Credentials) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.credentials(credentials)
        reload()
    }

    fun updateTimeRangePastDays(days: Int?) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setTimeRangePastDays(days)
        reload()

        /* If the new setting is a certain number of days, no full resync is required,
        because every sync will cause a REPORT calendar-query with the given number of days.
        However, if the new setting is "all events", collection sync may/should be used, so
        the last sync-token has to be reset, which is done by setting fullResync=true.
         */
        resyncCalendars(fullResync = days == null, tasks = false)
    }

    fun updateDefaultAlarm(minBefore: Int?) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setDefaultAlarm(minBefore)
        reload()

        resyncCalendars(fullResync = true, tasks = false)
    }

    fun updateManageCalendarColors(manage: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setManageCalendarColors(manage)
        reload()

        resyncCalendars(fullResync = false, tasks = true)
    }

    fun updateEventColors(manageColors: Boolean) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setEventColors(manageColors)
        reload()

        resyncCalendars(fullResync = true, tasks = false)
    }

    fun updateContactGroupMethod(groupMethod: GroupMethod) = CoroutineScope(Dispatchers.Default).launch {
        accountSettings.setGroupMethod(groupMethod)
        reload()

        resync(SyncDataType.CONTACTS, fullResync = true)
    }

    /**
     * Initiates calendar re-synchronization.
     *
     * @param fullResync whether sync shall download all events again
     * (_true_: sets [BaseSyncWorker.FULL_RESYNC],
     * _false_: sets [BaseSyncWorker.RESYNC])
     * @param tasks whether tasks shall be synchronized, too (false: only events, true: events and tasks)
     */
    private fun resyncCalendars(fullResync: Boolean, tasks: Boolean) {
        resync(SyncDataType.EVENTS, fullResync)
        if (tasks)
            resync(SyncDataType.TASKS, fullResync)
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param dataType      type of data to synchronize
     * @param fullResync    whether sync shall download all events again
     * (_true_: sets [BaseSyncWorker.FULL_RESYNC],
     * _false_: sets [BaseSyncWorker.RESYNC])
     */
    private fun resync(dataType: SyncDataType, fullResync: Boolean) {
        val resync: Int =
            if (fullResync)
                BaseSyncWorker.FULL_RESYNC
            else
                BaseSyncWorker.RESYNC
        syncWorkerManager.enqueueOneTime(account, dataType, resync = resync)
    }

}