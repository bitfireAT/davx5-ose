package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.syncadapter.Syncer
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AccountSettingsModel.Factory::class)
class AccountSettingsModel @AssistedInject constructor(
    val context: Application,
    val settings: SettingsManager,
    @Assisted val account: Account
): ViewModel(), SettingsManager.OnChangeListener {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountSettingsModel
    }

    private val accountSettings = AccountSettings(context, account)

    // settings
    var syncIntervalContacts by mutableStateOf<Long?>(null)
    var syncIntervalCalendars by mutableStateOf<Long?>(null)

    private val tasksProvider = TaskUtils.currentProvider(context)
    var syncIntervalTasks by mutableStateOf<Long?>(null)

    var syncWifiOnly by mutableStateOf(false)
    var syncWifiOnlySSIDs by mutableStateOf<List<String>?>(null)
    var ignoreVpns by mutableStateOf(false)

    var credentials by mutableStateOf(Credentials())

    var timeRangePastDays by mutableStateOf<Int?>(null)
    var defaultAlarmMinBefore by mutableStateOf<Int?>(null)
    var manageCalendarColors by mutableStateOf(false)
    var eventColors by mutableStateOf(false)

    var contactGroupMethod by mutableStateOf(GroupMethod.GROUP_VCARDS)


    init {
        settings.addOnChangeListener(this)
        reload()
    }

    override fun onCleared() {
        super.onCleared()
        settings.removeOnChangeListener(this)
    }

    override fun onSettingsChanged() {
        reload()
    }

    private fun reload() {
        Logger.log.info("Reloading settings")

        Snapshot.withMutableSnapshot {
            syncIntervalContacts = accountSettings.getSyncInterval(context.getString(R.string.address_books_authority))
            syncIntervalCalendars = accountSettings.getSyncInterval(CalendarContract.AUTHORITY)
            syncIntervalTasks = tasksProvider?.let { accountSettings.getSyncInterval(it.authority) }

            syncWifiOnly = accountSettings.getSyncWifiOnly()
            syncWifiOnlySSIDs = accountSettings.getSyncWifiOnlySSIDs()
            ignoreVpns = accountSettings.getIgnoreVpns()

            credentials = accountSettings.credentials()

            timeRangePastDays = accountSettings.getTimeRangePastDays()
            defaultAlarmMinBefore = accountSettings.getDefaultAlarm()
            manageCalendarColors = accountSettings.getManageCalendarColors()
            eventColors = accountSettings.getEventColors()

            contactGroupMethod = accountSettings.getGroupMethod()
        }
    }


    fun updateContactsSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(context.getString(R.string.address_books_authority), syncInterval)
            reload()
        }
    }

    fun updateCalendarSyncInterval(syncInterval: Long) {
        CoroutineScope(Dispatchers.Default).launch {
            accountSettings.setSyncInterval(CalendarContract.AUTHORITY, syncInterval)
            reload()
        }
    }

    fun updateTasksSyncInterval(syncInterval: Long) {
        tasksProvider?.authority?.let { tasksAuthority ->
            CoroutineScope(Dispatchers.Default).launch {
                accountSettings.setSyncInterval(tasksAuthority, syncInterval)
                reload()
            }
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) {
        accountSettings.setSyncWiFiOnly(wifiOnly)
        reload()
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
        reload()
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) {
        accountSettings.setIgnoreVpns(ignoreVpns)
        reload()
    }

    fun updateCredentials(credentials: Credentials) {
        accountSettings.credentials(credentials)
        reload()
    }

    fun updateTimeRangePastDays(days: Int?) {
        accountSettings.setTimeRangePastDays(days)
        reload()

        /* If the new setting is a certain number of days, no full resync is required,
        because every sync will cause a REPORT calendar-query with the given number of days.
        However, if the new setting is "all events", collection sync may/should be used, so
        the last sync-token has to be reset, which is done by setting fullResync=true.
         */
        resyncCalendars(fullResync = days == null, tasks = false)
    }

    fun updateDefaultAlarm(minBefore: Int?) {
        accountSettings.setDefaultAlarm(minBefore)
        reload()

        resyncCalendars(fullResync = true, tasks = false)
    }

    fun updateManageCalendarColors(manage: Boolean) {
        accountSettings.setManageCalendarColors(manage)
        reload()

        resyncCalendars(fullResync = false, tasks = true)
    }

    fun updateEventColors(manageColors: Boolean) {
        accountSettings.setEventColors(manageColors)
        reload()

        resyncCalendars(fullResync = true, tasks = false)
    }

    fun updateContactGroupMethod(groupMethod: GroupMethod) {
        accountSettings.setGroupMethod(groupMethod)
        reload()

        resync(
            authority = context.getString(R.string.address_books_authority),
            fullResync = true
        )
    }

    /**
     * Initiates calendar re-synchronization.
     *
     * @param fullResync whether sync shall download all events again
     * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
     * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
     * @param tasks whether tasks shall be synchronized, too (false: only events, true: events and tasks)
     */
    private fun resyncCalendars(fullResync: Boolean, tasks: Boolean) {
        resync(CalendarContract.AUTHORITY, fullResync)
        if (tasks)
            resync(TaskProvider.ProviderName.OpenTasks.authority, fullResync)
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param authority authority to re-sync
     * @param fullResync whether sync shall download all events again
     * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
     * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
     */
    private fun resync(authority: String, fullResync: Boolean) {
        val resync =
            if (fullResync)
                OneTimeSyncWorker.FULL_RESYNC
            else
                OneTimeSyncWorker.RESYNC
        OneTimeSyncWorker.enqueue(context, account, authority, resync = resync)
    }

}