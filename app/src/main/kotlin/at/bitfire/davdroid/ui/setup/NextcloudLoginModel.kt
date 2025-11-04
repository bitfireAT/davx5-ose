/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.network.NextcloudLoginFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = NextcloudLoginModel.Factory::class)
class NextcloudLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val loginFlow: NextcloudLoginFlow
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): NextcloudLoginModel
    }

    /*companion object {
        const val STATE_POLL_URL = "poll_url"
        const val STATE_TOKEN = "token"
    }*/

    data class UiState(
        val baseUrl: String = "",
        val inProgress: Boolean = false,
        val error: String? = null,

        /** URL to open in the browser (set during Login Flow) */
        val loginUrl: HttpUrl? = null,

        /** login info (set after successful login) */
        val result: LoginInfo? = null
    ) {

        val baseHttpUrl: HttpUrl? = run {
            val baseUrlWithPrefix =
                if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))
                    baseUrl
                else
                    "https://$baseUrl"

            baseUrlWithPrefix.toHttpUrlOrNull()
        }

        val canContinue = !inProgress && baseHttpUrl != null

    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        val baseUri = initialLoginInfo.baseUri
        if (baseUri != null)
            uiState = uiState.copy(
                baseUrl = baseUri.toString()
                    .removePrefix("https://")
                    .removeSuffix(NextcloudLoginFlow.FLOW_V1_PATH)
                    .removeSuffix(NextcloudLoginFlow.FLOW_V2_PATH)
                    .removeSuffix(NextcloudLoginFlow.DAV_PATH)
            )

        uiState = uiState.copy(
            error = null,
            result = null
        )
    }

    fun updateBaseUrl(baseUrl: String) {
        uiState = uiState.copy(baseUrl = baseUrl)
    }

    // Login flow state
    /*private var pollUrl: HttpUrl?
        get() = state.get<String>(STATE_POLL_URL)?.toHttpUrlOrNull()
        set(value) {
            state[STATE_POLL_URL] = value.toString()
        }
    private var token: String?
        get() = state.get<String>(STATE_TOKEN)
        set(value) {
            state[STATE_TOKEN] = value
        }*/


    /**
     * Starts the Login Flow.
     */
    fun startLoginFlow() {
        val baseUrl = uiState.baseHttpUrl
        if (uiState.inProgress || baseUrl == null)
            return

        uiState = uiState.copy(
            inProgress = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val loginUrl = loginFlow.initiate(baseUrl)

                uiState = uiState.copy(
                    loginUrl = loginUrl,
                    inProgress = false
                )

            } catch (e: Exception) {
                logger.log(Level.WARNING, "Initiating Login Flow failed", e)

                uiState = uiState.copy(
                    inProgress = false,
                    error = e.toString()
                )
            }
        }
    }

    /**
     * Called when the custom tab / browser activity is finished. If memory is low, our
     * [NextcloudLogin] and its model have been cleared in the meanwhile. So if
     * we need certain data from the model, we have to make sure that these data are retained when the
     * model is cleared (saved state).
     */
    fun onReturnFromBrowser() = viewModelScope.launch {
        // Login Flow has been started in browser by UI, should not be started again
        uiState = uiState.copy(
            loginUrl = null,
            inProgress = true
        )

        val loginInfo = try {
            loginFlow.fetchLoginInfo()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Fetching login info failed", e)
            uiState = uiState.copy(
                inProgress = false,
                error = e.toString()
            )
            return@launch
        }

        uiState = uiState.copy(
            inProgress = false,
            result = loginInfo
        )
    }

    fun resetResult() {
        uiState = uiState.copy(
            loginUrl = null,
            result = null
        )
    }

}