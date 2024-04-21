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
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

class UrlLoginModel: ViewModel() {

    data class UiState(
        val url: String = "",
        val username: String = "",
        val password: String = ""
    ) {

        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val uri = urlWithPrefix.toURIorNull()

        val canContinue = uri != null && username.isNotEmpty() && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo =
            LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = StringUtils.trimToNull(username),
                    password = StringUtils.trimToNull(password)
                )
            )

    }

    var uiState by mutableStateOf(UiState())
        private set

    fun initialize(loginInfo: LoginInfo) {
        uiState = UiState(
            url = loginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            username = loginInfo.credentials?.username ?: "",
            password = loginInfo.credentials?.password ?: ""
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

}