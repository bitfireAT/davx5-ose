/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.webdav.WebDavMountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
class AddWebdavMountModel @Inject constructor(
    val context: Application,
    val db: AppDatabase,
    val mountRepository: WebDavMountRepository
): ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val success: Boolean = false,
        val error: String? = null,
        val displayName: String = "",
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val certificateAlias: String? = null
    ) {
        val urlWithPrefix =
            if (url.startsWith("http://", true) || url.startsWith("https://", true))
                url
            else
                "https://$url"
        val httpUrl = urlWithPrefix.toHttpUrlOrNull()
        val canContinue = displayName.isNotBlank() && httpUrl != null
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun resetError() {
        uiState = uiState.copy(error = null)
    }

    fun setDisplayName(displayName: String) {
        uiState = uiState.copy(displayName = displayName)
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

    fun setCertificateAlias(certAlias: String) {
        uiState = uiState.copy(certificateAlias = certAlias)
    }


    fun addMount() {
        if (uiState.isLoading)
            return
        val url = uiState.httpUrl ?: return
        uiState = uiState.copy(isLoading = true)

        val displayName = uiState.displayName
        val credentials = Credentials(
            username = uiState.username,
            password = uiState.password,
            certificateAlias = uiState.certificateAlias
        )

        viewModelScope.launch {
            var error: String? = null
            try {
                if (!mountRepository.addMount(url, displayName, credentials))
                    error = context.getString(R.string.webdav_add_mount_no_support)
                else
                    uiState = uiState.copy(success = true)
            } catch (e: Exception) {
                error = e.localizedMessage
            }

            uiState = uiState.copy(isLoading = false, error = error)
        }
    }

}