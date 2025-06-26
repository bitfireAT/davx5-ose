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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = EmailLoginModel.Factory::class)
class EmailLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): EmailLoginModel
    }

    data class UiState(
        val email: String = "",
        val password: String = ""
    ) {
        val uri = "mailto:$email".toURIorNull()

        val canContinue = uri != null && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo {
            return LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = email,
                    password = password.toCharArray()
                )
            )
        }
    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = uiState.copy(
            email = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password?.concatToString() ?: ""
        )
    }

    fun setEmail(email: String) {
        uiState = uiState.copy(email = email)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

}