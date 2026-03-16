/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.util.DavUtils.toURIorNull
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.davdroid.util.trimToNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = UrlLoginModel.Factory::class)
class UrlLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): UrlLoginModel
    }

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: TextFieldState = TextFieldState()
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.trim().toURIorNull()

        val canContinue     // we have to use get() because password is not immutable
            get() = uri != null && username.isNotEmpty() && password.text.toString().isNotEmpty()

        fun asLoginInfo(): LoginInfo =
            LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = username.trimToNull(),
                    password = password.text.toString().trimToNull()?.toSensitiveString()
                )
            )

    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = UiState(
            url = initialLoginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = initialLoginInfo.credentials?.username ?: "",
            password = TextFieldState(initialLoginInfo.credentials?.password?.asString() ?: "")
        )
    }

    fun setUrl(url: String) {
        uiState = uiState.copy(url = url)
    }

    fun setUsername(username: String) {
        uiState = uiState.copy(username = username)
    }

}