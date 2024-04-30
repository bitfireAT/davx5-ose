/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.util.DavUtils.toURIorNull
import org.apache.commons.lang3.StringUtils

class AdvancedLoginModel: ViewModel() {

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
                username = StringUtils.trimToNull(username),
                password = StringUtils.trimToNull(password),
                certificateAlias = StringUtils.trimToNull(certAlias)
            )
        )

    }

    var uiState by mutableStateOf(UiState())
        private set

    fun initialize(loginInfo: LoginInfo) {
        uiState = uiState.copy(
            url = loginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = loginInfo.credentials?.username ?: "",
            password = loginInfo.credentials?.password ?: "",
            certAlias = loginInfo.credentials?.certificateAlias ?: ""
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