/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.externalscanner

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.ui.setup.LoginInfo
import com.davx5.feature.preconfiguredsettings.shared.InvalidPreconfiguredSettingsException
import com.davx5.feature.preconfiguredsettings.shared.LocalizedException
import com.davx5.feature.preconfiguredsettings.shared.PreconfigurationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.parseUrl
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger
import com.davx5.feature.preconfiguredsettings.shared.R as sharedR

@HiltViewModel
class PreconfiguredSettingsViewModel @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val preconfigurationRepository: PreconfigurationRepository
) : ViewModel() {

    private val _error = MutableStateFlow<Exception?>(null)
    val error get() = _error.asStateFlow()

    private var intentHandled = false

    /**
     * Marks the config URL from the launching intent as handled, so that it's only fetched
     * automatically once per view model (and not again after navigating back to the login page).
     *
     * @return `true` if this is the first call (the intent has to be handled now), `false` if it was already handled
     */
    fun markIntentHandled(): Boolean {
        if (intentHandled) return false
        intentHandled = true
        return true
    }

    fun dismissError() {
        _error.value = null
    }

    fun onScanQrCodeFailure() {
        _error.tryEmit(LocalizedException(R.string.preconfigured_settings_external_scanner_error_no_app_found))
    }

    fun handleScanResult(scanResult: String, onLogin: (LoginInfo) -> Unit) {
        val configUrl = extractConfigUrl(scanResult.toUri())
        if (configUrl == null) {
            _error.tryEmit(LocalizedException(sharedR.string.preconfigured_settings_error_no_config_url))
            return
        }

        fetchPreconfiguredSettings(configUrl, onLogin)
    }

    /**
     * Extract config URL from intent (camera, QR code scanner or other apps)
     *
     * Example: `davx5://setup?config=https://example.com/config.json`
     */
    fun extractConfigUrl(uri: Uri): String? {
        return if (uri.scheme == "davx5") {
            uri.getQueryParameter("config")?.takeIf { it.isNotEmpty() && parseUrl(it) != null }
        } else {
            null
        }
    }

    /**
     * Tries to fetch the preconfigured settings from [url], and calls [onLogin] if some valid [LoginInfo] was provided.
     *
     * Potentially occurring exceptions are sent to [error]. Especially interesting could be:
     *
     * - [InvalidPreconfiguredSettingsException] – If the server responded with an error, or the response given is not valid.
     * - [IllegalStateException] – If the base root given by the server is missing or invalid.
     * - [javax.net.ssl.SSLHandshakeException] – On TLS problems, for instance if the certificate is unknown/invalid
     */
    fun fetchPreconfiguredSettings(url: String, onLogin: (LoginInfo) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            val preconfiguredSettings = try {
                preconfigurationRepository.fetchPreconfiguredSettings(url)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't fetch preconfigured settings", e)
                _error.emit(e)
                return@launch
            }

            // Provide the LoginInfo
            val loginInfo = try {
                preconfiguredSettings.toLoginInfo(url)
            } catch (e: IllegalStateException) {
                logger.log(Level.WARNING, "Invalid preconfigured settings", e)
                _error.emit(e)
                return@launch
            }
            onLogin(loginInfo)
        }
    }
}
