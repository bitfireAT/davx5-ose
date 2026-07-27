/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.network.OAuthIntegration
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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

@HiltViewModel(assistedFactory = AccountSettingsViewModel.Factory::class)
class AccountSettingsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val oAuthIntegration: OAuthIntegration,
    setAccountSettingsUseCaseFactory: SetAccountSettingsUseCase.Factory,
    private val settings: SettingsManager,
    private val tasksAppManager: TasksAppManager
): ViewModel(), SettingsManager.OnChangeListener {

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): AccountSettingsViewModel
    }

    // settings
    data class UiState(
        val accountName: String = "",
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
    private val accountSettings by lazy { accountSettingsFactory.create(accountId.toAndroidAccount()) }

    private val setAccountSettingsUseCase = setAccountSettingsUseCaseFactory.create(accountId)

    init {
        settings.addOnChangeListener(this)
        viewModelScope.launch {
            reload()
            
            accountRepository.getAccountNameFlow(accountId).collect { accountName ->
                _uiState.update { 
                    it.copy(accountName = accountName)
                }
            }
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

    private suspend fun reload() = withContext(ioDispatcher) {
        val hasContactsSync = serviceDao.getByAccountAndType(accountId, Service.TYPE_CARDDAV) != null
        val hasCalendarSync = serviceDao.getByAccountAndType(accountId, Service.TYPE_CALDAV) != null
        val hasTasksSync = hasCalendarSync && tasksProvider != null

        _uiState.update { 
            it.copy(
                status = null,

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
    }


    fun updateContactsSyncInterval(syncInterval: Long) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setContactsSyncInterval(syncInterval)
        }
    }

    fun updateCalendarSyncInterval(syncInterval: Long) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setCalendarSyncInterval(syncInterval)
        }
    }

    fun updateTasksSyncInterval(syncInterval: Long) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setTasksSyncInterval(syncInterval)
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setSyncWiFiOnly(wifiOnly)
        }
    }

    fun updateSyncWifiOnlySSIDs(ssids: List<String>?) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setSyncWifiOnlySSIDs(ssids)
        }
    }

    fun updateIgnoreVpns(ignoreVpns: Boolean) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setIgnoreVpns(ignoreVpns)
        }
    }


    fun authorizationContract() = OAuthIntegration.AuthorizationContract(authService)

    fun newAuthorizationRequest(): AuthorizationRequest? =
        accountSettings.credentials().authState?.lastAuthorizationResponse?.request

    fun authenticate(authResponse: AuthorizationResponse) {
        viewModelScope.launch {
            val status = finishOAuthLogin(authResponse).fold(
                onSuccess = { context.getString(R.string.settings_reauthorize_oauth_success) },
                onFailure = { exception -> exception.localizedMessage }
            )

            _uiState.update {
                it.copy(status = status)
            }
        }
    }

    private suspend fun finishOAuthLogin(authResponse: AuthorizationResponse): Result<Unit> {
        try {
            // Use applicationScope to make sure OAuth login completes even if the user navigates away from the screen,
            // i.e. viewModelScope is canceled.
            applicationScope.async {
                val authState = oAuthIntegration.authenticate(authService, authResponse)
                setAccountSettingsUseCase.setAuthState(authState)
            }.await()

            return Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Authentication failed", e)
            return Result.failure(e)
        }
    }

    fun authCodeFailed() {
        _uiState.update {
            it.copy(status = context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
        }
    }

    fun updateCredentials(credentials: Credentials) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setCredentials(credentials)
        }
    }


    fun updateTimeRangePastDays(days: Int?) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setTimeRangePastDays(days)
        }
    }

    fun updateDefaultAlarm(minBefore: Int?) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setDefaultAlarm(minBefore)
        }
    }

    fun updateManageCalendarColors(manage: Boolean) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setManageCalendarColors(manage)
        }
    }

    fun updateEventColors(manageColors: Boolean) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setEventColors(manageColors)
        }
    }

    fun updateContactGroupMethod(groupMethod: GroupMethod) {
        updateSettingsAndReload {
            setAccountSettingsUseCase.setContactGroupMethod(groupMethod)
        }
    }

    // Note: Until there's a mechanism for listening to account settings changes, we need to manually reload settings
    // after updating a setting.
    private fun updateSettingsAndReload(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            reload()
        }
    }
}
