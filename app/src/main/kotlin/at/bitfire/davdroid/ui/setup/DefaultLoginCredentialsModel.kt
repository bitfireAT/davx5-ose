/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class DefaultLoginCredentialsModel(app: Application): AndroidViewModel(app) {

    private var initialized = false

    val loginWithEmailAddress = MutableLiveData(true)
    val loginWithUrlAndUsername = MutableLiveData(false)
    val loginAdvanced = MutableLiveData(false)
    val loginGoogle = MutableLiveData(false)
    val loginNextcloud = MutableLiveData(false)

    val baseUrl = MutableLiveData<String>()
    val baseUrlError = MutableLiveData<String>()

    /** user name or email address */
    val username = MutableLiveData<String>()
    val usernameError = MutableLiveData<String>()

    val password = MutableLiveData<String>()
    val passwordError = MutableLiveData<String>()

    val certificateAlias = MutableLiveData<String>()
    val certificateAliasError = MutableLiveData<String>()

    val loginUseUsernamePassword = MutableLiveData(false)
    val loginUseClientCertificate = MutableLiveData(false)

    @MainThread
    fun initialize(intent: Intent) {
        if (initialized)
            return
        initialized = true

        var givenUrl: String? = null
        var givenUsername: String? = null
        var givenPassword: String? = null

        intent.data?.normalizeScheme()?.let { uri ->
            // We've got initial login data from the Intent.
            // We can't use uri.buildUpon() because this keeps the user info (it's readable, but not writable).
            val realScheme = when (uri.scheme) {
                "caldav", "carddav" -> "http"
                "caldavs", "carddavs", "davx5" -> "https"
                "http", "https" -> uri.scheme
                else -> null
            }
            if (realScheme != null) {
                val realUri = Uri.Builder()
                    .scheme(realScheme)
                    .authority(uri.host)
                    .path(uri.path)
                    .query(uri.query)
                givenUrl = realUri.build().toString()

                // extract user info
                uri.userInfo?.split(':')?.let { userInfo ->
                    givenUsername = userInfo.getOrNull(0)
                    givenPassword = userInfo.getOrNull(1)
                }
            }
        }

        // no login data from the Intent, let's look up the extras
        givenUrl ?: intent.getStringExtra(LoginActivity.EXTRA_URL)

        // always prefer username/password from the extras
        if (intent.hasExtra(LoginActivity.EXTRA_USERNAME))
            givenUsername = intent.getStringExtra(LoginActivity.EXTRA_USERNAME)
        if (intent.hasExtra(LoginActivity.EXTRA_PASSWORD))
            givenPassword = intent.getStringExtra(LoginActivity.EXTRA_PASSWORD)

        if (givenUrl != null) {
            loginWithUrlAndUsername.value = true
            baseUrl.value = givenUrl
        } else
            loginWithEmailAddress.value = true
        username.value = givenUsername
        password.value = givenPassword
    }

}
