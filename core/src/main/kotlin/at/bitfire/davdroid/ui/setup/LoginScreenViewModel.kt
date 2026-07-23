/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.log.DebugDirectory
import at.bitfire.davdroid.log.FileLoggerFactory
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.settings.AccountSettingsStore
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.toUrlOrNull
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = LoginScreenViewModel.Factory::class)
class LoginScreenViewModel @AssistedInject constructor(
    @Assisted val initialLoginType: LoginType,
    @Assisted val skipLoginTypePage: Boolean,
    @Assisted val initialLoginInfo: LoginInfo,
    private val accountRepository: AccountRepository,
    @ApplicationContext val context: Context,
    private val debugDirectory: DebugDirectory,
    private val httpClientBuilder: HttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    val loginTypesProvider: LoginTypesProvider,
    private val resourceFinderFactory: DavResourceFinder.Factory,
    settingsManager: SettingsManager,
    private val loginValidator: Optional<LoginValidator>
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            initialLoginType: LoginType,
            skipLoginTypePage: Boolean,
            initialLoginInfo: LoginInfo
        ): LoginScreenViewModel
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

    var loginDetailsUiState by mutableStateOf(
        LoginDetailsUiState(
            loginType = initialLoginType,
            loginInfo = loginInfo
        )
    )
        private set

    fun updateLoginInfo(loginInfo: LoginInfo) {
        loginDetailsUiState = loginDetailsUiState.copy(loginInfo = loginInfo)
    }


    // UI element state – third page: detect resources

    data class DetectResourcesUiState(
        val loading: Boolean = false,
        val foundNothing: Boolean = false,
        val encountered401: Boolean = false,
        val loginValidationFailed: Boolean = false,
        val debugLogFileName: DebugDirectory.FileName? = null
    )

    var detectResourcesUiState by mutableStateOf(DetectResourcesUiState())
        private set

    private var foundConfig: DavResourceFinder.Configuration? = null
    private var detectResourcesJob: Job? = null

    private fun detectResources() {
        val baseUri = loginInfo.baseUri ?: return
        detectResourcesUiState = detectResourcesUiState.copy(loading = true)
        detectResourcesJob = viewModelScope.launch {
            if (!validateLogin(baseUri)) {
                detectResourcesUiState = detectResourcesUiState.copy(loading = false, loginValidationFailed = true)
                return@launch
            }

            try {
                val (result, logFile) = findConfiguration(baseUri)
                if (result.calDAV != null || result.cardDAV != null) {
                    foundConfig = result
                    navToNextPage()
                } else {
                    foundConfig = null
                    detectResourcesUiState = detectResourcesUiState.copy(
                        loading = false,
                        foundNothing = true,
                        encountered401 = result.encountered401,
                        debugLogFileName = logFile
                    )
                }
            } catch (e: IOException) {
                /* Note: this is only reached for severe problems like when the logs can't be written. It's NOT
                reached for exceptions during service detection – these are all caught by findConfiguration(). */
                logger.log(Level.SEVERE, "Couldn't run service detection", e)
            }
        }
    }

    /**
     * Validates the login using [loginValidator] if one is configured.
     *
     * @return `true` if valid or no validator is configured; `false` if the server rejected the login
     */
    private suspend fun validateLogin(baseUri: URI): Boolean {
        val httpUrl = baseUri.toUrlOrNull() ?: return true
        if (!loginValidator.isPresent)
            return true
        return loginValidator.get().beforeLogin(httpUrl)
    }

    /**
     * Runs service detection for [baseUri] and writes a log to a file in the debug directory.
     *
     * @return the detection result and the [DebugDirectory.FileName] of the log file written during detection
     * @throws IOException when the service detection log can't be created
     */
    private suspend fun findConfiguration(baseUri: URI): Pair<DavResourceFinder.Configuration, DebugDirectory.FileName> {
        val logFile = debugDirectory.resolve(SERVICE_DETECTION_LOG_FILE)
            ?: throw IOException("Couldn't write service detection log")

        val result = FileLoggerFactory.forFile(logFile).use { fileLoggerContext ->
            val credentials = loginInfo.credentials
            httpClientBuilder
                .logTo(fileLoggerContext.logger)    // log HTTP calls to logFile
                .let { builder ->
                    if (credentials != null)
                        builder.authenticate(domain = null, getCredentials = { credentials })
                    else
                        builder
                }
                .build()
                .use { httpClient ->
                    val finder = resourceFinderFactory.create(
                        baseUri,
                        credentials,
                        httpClient,
                        log = fileLoggerContext.logger  // log resource detection to logFile
                    )

                    // run the actual service detection
                    finder.findInitialConfiguration()
                }
        }
        return result to SERVICE_DETECTION_LOG_FILE
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
        .getStringFlow(AccountSettingsStore.KEY_CONTACT_GROUP_METHOD)
        .map { groupMethodName ->
            // map group method name to GroupMethod
            if (groupMethodName != null)
                try {
                    GroupMethod.valueOf(groupMethodName)
                } catch (e: IllegalArgumentException) {
                    logger.log(Level.WARNING, "Invalid forced group method: $groupMethodName", e)
                    null
                }
            else
                null
        }

    // backing field that is combined with dynamic content for the resulting UI State
    private var _accountDetailsUiState = MutableStateFlow(AccountDetailsUiState())
    val accountDetailsUiState = combine(_accountDetailsUiState, forcedGroupMethod) { uiState, method ->
        // set group type to read-only if group method is forced
        var combinedState = uiState.copy(groupMethodReadOnly = method != null)

        // apply forced group method, if applicable
        if (method != null)
            combinedState = combinedState.copy(groupMethod = method)

        combinedState
    }.stateIn(viewModelScope, SharingStarted.Lazily, _accountDetailsUiState.value)

    fun updateAccountName(accountName: String) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(
                accountName = accountName,
                accountNameExists = accountRepository.exists(accountName)
            )
        }
    }

    fun updateAccountNameAndEmails(accountName: String, emails: Set<String>) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(
                accountName = accountName,
                accountNameExists = accountRepository.exists(accountName),
                suggestedAccountNames = emails
            )
        }
    }

    fun updateGroupMethod(groupMethod: GroupMethod) {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(groupMethod = groupMethod)
        }
    }

    fun resetCouldNotCreateAccount() {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(couldNotCreateAccount = false)
        }
    }

    fun createAccount() {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(creatingAccount = true)
        }

        viewModelScope.launch {
            val account = withContext(ioDispatcher) {
                accountRepository.createBlocking(
                    accountDetailsUiState.value.accountName,
                    loginInfo.credentials,
                    foundConfig!!,
                    accountDetailsUiState.value.groupMethod,
                    loginInfo.preconfigurationUrl
                )
            }

            _accountDetailsUiState.update { currentState ->
                if (account != null)
                    currentState.copy(createdAccount = account)
                else
                    currentState.copy(
                        creatingAccount = false,
                        couldNotCreateAccount = true
                    )
            }
        }
    }


    companion object {

        /** file name of the service detection logs */
        private val SERVICE_DETECTION_LOG_FILE
            get() = DebugDirectory.FileName("service-detection.log")

    }

}