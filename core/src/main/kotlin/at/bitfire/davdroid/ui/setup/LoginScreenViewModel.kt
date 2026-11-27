/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountNameTakenException
import at.bitfire.davdroid.log.DebugDirectory
import at.bitfire.davdroid.log.FileLoggerFactory
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.toUrlOrNull
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val logger: Logger,
    val loginTypesProvider: LoginTypesProvider,
    private val resourceFinderFactory: DavResourceFinder.Factory,
    settingsManager: SettingsManager,
    private val loginValidator: Optional<LoginValidator>,
    private val credentialManager: CredentialManager
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

    /**
     * Handles back/up navigation within the login flow, usually by navigating to the previous page.
     *
     * @return  *true* if the navigation was handled within the login flow;
     *          *false* if there's no previous page and the caller should leave the flow
     */
    fun navBack(): Boolean {
        // Account creation can't be canceled, so ignore back navigation while it's running – otherwise
        // the account would be created anyway and its result would be applied when the account details
        // page is opened again.
        if (_accountDetailsUiState.value.creatingAccount)
            return true

        return when (page) {
            Page.LoginType -> false

            Page.LoginDetails ->
                // don't go back to the login type page if it was skipped or may be non-interactive
                if (skipLoginTypePage || loginTypesProvider.maybeNonInteractive)
                    false
                else {
                    page = Page.LoginType
                    true
                }

            Page.DetectResources -> {
                cancelResourceDetection()
                page = Page.LoginDetails
                true
            }

            Page.AccountDetails -> {
                page = Page.LoginDetails
                true
            }
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
        val debugLogFileName: DebugDirectory.FileName? = null,
        /** Credentials the UI should offer to store in the password manager before continuing (see [onCredentialsHandled]). */
        val credentialsToSave: Credentials? = null
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

                    // Offer the password manager to store the credentials, if they are username+password
                    // (other login methods can't be stored). The UI does that with its current Activity
                    // and then calls onCredentialsHandled(), so that no Activity is held here.
                    // Only with the platform Credential Manager; older versions use the autofill save prompt.
                    val credentials = loginInfo.credentials
                    if (useCredentialManager && credentials?.username != null && credentials.password != null)
                        detectResourcesUiState = detectResourcesUiState.copy(credentialsToSave = credentials)
                    else
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
     * Offers to store [DetectResourcesUiState.credentialsToSave] in the password manager.
     *
     * Needs an Activity context to show the prompt. It's only passed for the duration of this call,
     * so the caller should run it in a scope that's cancelled with the Activity (e.g. `LaunchedEffect`).
     */
    suspend fun saveCredentials(activity: Activity) {
        val credentials = detectResourcesUiState.credentialsToSave ?: return
        val username = credentials.username ?: return
        val password = credentials.password?.asString() ?: return
        try {
            credentialManager.createCredential(
                context = MutableContextWrapper(activity),
                request = CreatePasswordRequest(id = username, password = password)
            )
        } catch (_: CreateCredentialCancellationException) {
            // the user dismissed the save prompt
            logger.fine("User rejected storing the password in the password manager")
        } catch (e: CreateCredentialException) {
            // no provider available or another error
            logger.warning("There's no password manager to store the credentials into, or something went wrong: $e")
        }
    }

    /**
     * Called when the UI is done offering to store the credentials (whatever the result was).
     * Continues to the next page.
     */
    fun onCredentialsHandled() {
        if (detectResourcesUiState.credentialsToSave == null)
            return
        detectResourcesUiState = detectResourcesUiState.copy(credentialsToSave = null)
        navToNextPage()
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
        val createdAccountId: AccountId? = null,
        val couldNotCreateAccount: Boolean = false,
        val androidAccountNameTaken: Boolean = false
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

    fun updateAccountName(accountName: String) = viewModelScope.launch {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(
                accountName = accountName,
                accountNameExists = accountRepository.exists(accountName)
            )
        }
    }

    fun updateAccountNameAndEmails(accountName: String, emails: Set<String>) = viewModelScope.launch {
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

    fun resetAndroidAccountNameTaken() {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(androidAccountNameTaken = false)
        }
    }

    fun createAccount() {
        _accountDetailsUiState.update { currentState ->
            currentState.copy(creatingAccount = true)
        }

        viewModelScope.launch {
            val accountId = try {
                accountRepository.create(
                    accountDetailsUiState.value.accountName,
                    loginInfo.credentials,
                    foundConfig!!,
                    accountDetailsUiState.value.groupMethod,
                    loginInfo.preconfigurationUrl
                )
            } catch (_: AndroidAccountNameTakenException) {
                _accountDetailsUiState.update { currentState ->
                    currentState.copy(
                        creatingAccount = false,
                        androidAccountNameTaken = true
                    )
                }
                return@launch
            }

            _accountDetailsUiState.update { currentState ->
                if (accountId != null)
                    currentState.copy(createdAccountId = accountId)
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