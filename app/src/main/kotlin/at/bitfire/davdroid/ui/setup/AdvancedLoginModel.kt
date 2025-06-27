/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.util.DavUtils.toURIorNull
import at.bitfire.davdroid.util.trimToNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = AdvancedLoginModel.Factory::class)
class AdvancedLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): AdvancedLoginModel
    }

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val certAlias: String = ""
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.toURIorNull()

        val canContinue = uri != null

        fun asLoginInfo() = LoginInfo(
            baseUri = uri,
            credentials = Credentials(
                username = username.trimToNull(),
                password = password.trimToNull()?.toCharArray(),
                certificateAlias = certAlias.trimToNull()
            )
        )

    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = uiState.copy(
            url = initialLoginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password?.concatToString() ?: "",
            certAlias = initialLoginInfo.credentials?.certificateAlias ?: ""
        )
    }

    fun setUrl(url: String) {
        uiState = uiState.copy(url = url)
    }

    fun setUsername(username: String) {
        uiState = uiState.copy(username = username)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

    fun setCertAlias(certAlias: String) {
        uiState = uiState.copy(certAlias = certAlias)
    }

}