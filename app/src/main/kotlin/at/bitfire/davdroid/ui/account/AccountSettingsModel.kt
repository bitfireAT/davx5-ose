/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.di.DefaultDispatcher
import at.bitfire.davdroid.network.OAuthIntegration
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.ResyncType
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountSettingsModel.Factory::class)
class AccountSettingsModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    db: AppDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val logger: Logger,
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
        val status: String? = null,

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
        val allowCredentialsChange: Boolean = true,

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
        authService.dispose()
        settings.removeOnChangeListener(this)
    }

    override fun onSettingsChanged() {
        viewModelScope.launch {
            reload()
        }
    }

    private suspend fun reload() = withContext(defaultDispatcher) {
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
            allowCredentialsChange = accountSettings.changingCredentialsAllowed(),

            timeRangePastDays = accountSettings.getTimeRangePastDays(),
            defaultAlarmMinBefore = accountSettings.getDefaultAlarm(),
            manageCalendarColors = accountSettings.getManageCalendarColors(),
            eventColors = accountSettings.getEventColors(),

            contactGroupMethod = accountSettings.getGroupMethod(),
        )
    }


    fun updateContactsSyncInterval(syncInterval: Long) {
        CoroutineScope(defaultDispatcher).launch {
            accountSettings.setSyncInterval(SyncDataType.CONTACTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateCalendarSyncInterval(syncInterval: Long) {
        CoroutineScope(defaultDispatcher).launch {
            accountSettings.setSyncInterval(SyncDataType.EVENTS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateTasksSyncInterval(syncInterval: Long) {
        CoroutineScope(defaultDispatcher).launch {
            accountSettings.setSyncInterval(SyncDataType.TASKS, syncInterval.takeUnless { it == -1L })
            reload()
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setSyncWiFiOnly(wifiOnly)
        reload()
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setSyncWifiOnlySSIDs(ssids)
        reload()
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setIgnoreVpns(ignoreVpns)
        reload()
    }


    fun authorizationContract() = OAuthIntegration.AuthorizationContract(authService)

    fun newAuthorizationRequest(): AuthorizationRequest? =
        accountSettings.credentials().authState?.lastAuthorizationResponse?.request

    fun authenticate(authResponse: AuthorizationResponse) {
        CoroutineScope(defaultDispatcher).launch {
            try {
                // save new credentials
                val authState = OAuthIntegration.authenticate(authService, authResponse)
                accountSettings.updateAuthState(authState)

                _uiState.update {
                    it.copy(status = context.getString(R.string.settings_reauthorize_oauth_success))
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Authentication failed", e)
                _uiState.update {
                    it.copy(status = e.localizedMessage)
                }
            }
        }
    }

    fun authCodeFailed() {
        _uiState.update {
            it.copy(status = context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
        }
    }

    fun updateCredentials(credentials: Credentials) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.credentials(credentials)
        reload()
    }


    fun updateTimeRangePastDays(days: Int?) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setTimeRangePastDays(days)
        reload()

        /* If the new setting is a certain number of days, no full resync is required,
        because every sync will cause a REPORT calendar-query with the given number of days.
        However, if the new setting is "all events", collection sync may/should be used, so
        the last sync-token has to be reset, which is done by setting fullResync=true.
         */
        resyncCalendars(
            resync = if (days == null) ResyncType.RESYNC_ENTRIES else ResyncType.RESYNC_LIST,
            tasks = false
        )
    }

    fun updateDefaultAlarm(minBefore: Int?) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setDefaultAlarm(minBefore)
        reload()

        resyncCalendars(resync = ResyncType.RESYNC_ENTRIES, tasks = false)
    }

    fun updateManageCalendarColors(manage: Boolean) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setManageCalendarColors(manage)
        reload()

        resyncCalendars(resync = ResyncType.RESYNC_LIST, tasks = true)
    }

    fun updateEventColors(manageColors: Boolean) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setEventColors(manageColors)
        reload()

        resyncCalendars(resync = ResyncType.RESYNC_ENTRIES, tasks = false)
    }


    fun updateContactGroupMethod(groupMethod: GroupMethod) = CoroutineScope(defaultDispatcher).launch {
        accountSettings.setGroupMethod(groupMethod)
        reload()

        resync(SyncDataType.CONTACTS, ResyncType.RESYNC_ENTRIES)
    }

    /**
     * Initiates calendar re-synchronization.
     *
     * @param resync    whether only the list of entries (resync) or also all entries
     *                  themselves (full resync) shall be downloaded again
     * @param tasks     whether tasks shall be synchronized, too (false: only events, true: events and tasks)
     */
    private fun resyncCalendars(resync: ResyncType, tasks: Boolean) {
        resync(SyncDataType.EVENTS, resync)
        if (tasks)
            resync(SyncDataType.TASKS, resync)
    }

    /**
     * Initiates re-synchronization for given authority.
     *
     * @param dataType  type of data to synchronize
     * @param resync    whether only the list of entries (resync) or also all entries
     *                  themselves (full resync) shall be downloaded again
     */
    private fun resync(dataType: SyncDataType, resync: ResyncType) {
        syncWorkerManager.enqueueOneTime(account, dataType = dataType, resync = resync)
    }

}