/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = LoginScreenModel.Factory::class)
class LoginScreenModel @AssistedInject constructor(
    @Assisted val initialLoginType: LoginType,
    @Assisted val skipLoginTypePage: Boolean,
    @Assisted val initialLoginInfo: LoginInfo,
    val context: Application,
    val loginTypesProvider: LoginTypesProvider,
    private val accountRepository: AccountRepository,
    settingsManager: SettingsManager
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            initialLoginType: LoginType,
            skipLoginTypePage: Boolean,
            initialLoginInfo: LoginInfo
        ): LoginScreenModel
    }

    enum class Page {
        LoginType,
        LoginDetails,
        DetectResources,
        AccountDetails
    }

    private val startPage = if (skipLoginTypePage)
        Page.LoginDetails
    else
        Page.LoginType

    var page by mutableStateOf(startPage)
        private set

    var finish by mutableStateOf(false)
        private set


    // navigation events

    fun navToNextPage() {
        when (page) {
            Page.LoginType -> {
                // continue to login details
                loginDetailsUiState = loginDetailsUiState.copy(
                    loginType = loginTypeUiState.loginType
                )
                page = Page.LoginDetails
            }

            Page.LoginDetails -> {
                // continue to resource detection
                loginInfo = loginDetailsUiState.loginInfo
                page = Page.DetectResources

                detectResources()
            }

            Page.DetectResources -> {
                // continue to account details
                val emails = foundConfig?.calDAV?.emails.orEmpty().toSet()
                val initialAccountName = emails.firstOrNull()
                    ?: loginInfo.suggestedAccountName
                    ?: loginInfo.credentials?.username
                    ?: loginInfo.baseUri?.host
                    ?: ""
                updateAccountNameAndEmails(initialAccountName, emails)
                updateGroupMethod(loginInfo.suggestedGroupMethod)
                page = Page.AccountDetails
            }

            Page.AccountDetails -> {
                // last page
            }
        }
    }

    fun navBack() {
        when (page) {
            Page.LoginType ->
                finish = true

            Page.LoginDetails ->
                if (loginTypesProvider.maybeNonInteractive)
                    finish = true
                else
                    page = Page.LoginType

            Page.DetectResources -> {
                cancelResourceDetection()
                page = Page.LoginDetails
            }

            Page.AccountDetails ->
                page = Page.LoginDetails
        }
    }


    // UI element state – first page: login type

    data class LoginTypeUiState(
        val loginType: LoginType
    )

    var loginTypeUiState by mutableStateOf(LoginTypeUiState(loginType = initialLoginType))
        private set

    fun selectLoginType(loginType: LoginType) {
        loginTypeUiState = loginTypeUiState.copy(loginType = loginType)
        loginDetailsUiState = loginDetailsUiState.copy(loginType = loginType)
    }


    // UI element state – second page: login details

    // base URI and credentials
    private var loginInfo: LoginInfo = initialLoginInfo

    data class LoginDetailsUiState(
        val loginType: LoginType,
        val loginInfo: LoginInfo
    )

    var loginDetailsUiState by mutableStateOf(LoginDetailsUiState(
        loginType = initialLoginType,
        loginInfo = loginInfo
    ))
        private set

    fun updateLoginInfo(loginInfo: LoginInfo) {
        loginDetailsUiState = loginDetailsUiState.copy(loginInfo = loginInfo)
    }


    // UI element state – third page: detect resources

    data class DetectResourcesUiState(
        val loading: Boolean = false,
        val foundNothing: Boolean = false,
        val encountered401: Boolean = false,
        val logs: String? = null
    )

    var detectResourcesUiState by mutableStateOf(DetectResourcesUiState())
        private set

    private var foundConfig: DavResourceFinder.Configuration? = null
    private var detectResourcesJob: Job? = null

    private fun detectResources() {
        detectResourcesUiState = detectResourcesUiState.copy(loading = true)
        detectResourcesJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                 runInterruptible {
                    DavResourceFinder(context, loginInfo.baseUri!!, loginInfo.credentials).use { finder ->
                        finder.findInitialConfiguration()
                    }
                }
            }

            if (result.calDAV != null || result.cardDAV != null) {
                foundConfig = result
                navToNextPage()

            } else {
                foundConfig = null
                detectResourcesUiState = detectResourcesUiState.copy(
                    loading = false,
                    foundNothing = true,
                    encountered401 = result.encountered401,
                    logs = result.logs
                )
            }
        }
    }

    private fun cancelResourceDetection() {
        detectResourcesJob?.cancel()
    }


    // UI element state – last page: account details

    data class AccountDetailsUiState(
        val accountName: String = "",
        val suggestedAccountNames: Set<String> = emptySet(),
        val accountNameExists: Boolean = false,
        val groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        val groupMethodReadOnly: Boolean = false,
        val creatingAccount: Boolean = false,
        val createdAccount: Account? = null,
        val couldNotCreateAccount: Boolean = false
    ) {
        val showApostropheWarning = accountName.contains('\'') || accountName.contains('"')
    }

    private val forcedGroupMethod = settingsManager
        .getStringFlow(AccountSettings.KEY_CONTACT_GROUP_METHOD)
        .map { groupMethodName ->
            // map group method name to GroupMethod
            if (groupMethodName != null)
                try {
                    GroupMethod.valueOf(groupMethodName)
                } catch (e: IllegalArgumentException) {
                    Logger.log.warning("Invalid forced group method: $groupMethodName")
                    null
                }
            else
                null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // backing field that is combined with dynamic content for the resulting UI State
    private var _accountDetailsUiState by mutableStateOf(AccountDetailsUiState())
    val accountDetailsUiState by derivedStateOf {
        val method = forcedGroupMethod.value

        // set group type to read-only if group method is forced
        var combinedState = _accountDetailsUiState.copy(groupMethodReadOnly = method != null)

        // apply forced group method, if applicable
        if (method != null)
            combinedState = combinedState.copy(groupMethod = method)

        combinedState
    }

    fun updateAccountName(accountName: String) {
        _accountDetailsUiState = _accountDetailsUiState.copy(
            accountName = accountName,
            accountNameExists = accountRepository.exists(accountName)
        )
    }

    fun updateAccountNameAndEmails(accountName: String, emails: Set<String>) {
        _accountDetailsUiState = _accountDetailsUiState.copy(
            accountName = accountName,
            accountNameExists = accountRepository.exists(accountName),
            suggestedAccountNames = emails
        )
    }

    fun updateGroupMethod(groupMethod: GroupMethod) {
        _accountDetailsUiState = _accountDetailsUiState.copy(groupMethod = groupMethod)
    }

    fun resetCouldNotCreateAccount() {
        _accountDetailsUiState = _accountDetailsUiState.copy(couldNotCreateAccount = false)
    }

    fun createAccount() {
        _accountDetailsUiState = _accountDetailsUiState.copy(creatingAccount = true)
        viewModelScope.launch {
            val account = withContext(Dispatchers.Default) {
                accountRepository.create(
                    accountDetailsUiState.accountName,
                    loginInfo.credentials,
                    foundConfig!!,
                    accountDetailsUiState.groupMethod
                )
            }

            _accountDetailsUiState =
                if (account != null)
                    accountDetailsUiState.copy(createdAccount = account)
                else
                    accountDetailsUiState.copy(
                        creatingAccount = false,
                        couldNotCreateAccount = true
                    )
        }
    }

}