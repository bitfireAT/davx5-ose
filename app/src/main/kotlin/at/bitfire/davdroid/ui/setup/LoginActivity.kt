/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URI
import javax.inject.Inject

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
@AndroidEntryPoint
class LoginActivity @Inject constructor(): AppCompatActivity() {

    companion object {

        /**
         * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
         * When not set, "login by email" will be activated by default.
         */
        const val EXTRA_URL = "url"

        /**
         * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
         * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
         */
        const val EXTRA_USERNAME = "username"

        /**
         * When set, the password field will be set to this value.
         */
        const val EXTRA_PASSWORD = "password"

        /**
         * When set, Nextcloud Login Flow will be used.
         */
        const val EXTRA_LOGIN_FLOW = "loginFlow"


        fun loginInfoFromIntent(intent: Intent): LoginInfo {
            var givenUri: String? = null
            var givenUsername: String? = null
            var givenPassword: String? = null

            // extract URI and optionally username/password from Intent data
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
                    givenUri = realUri.build().toString()

                    // extract user info
                    uri.userInfo?.split(':')?.let { userInfo ->
                        givenUsername = userInfo.getOrNull(0)
                        givenPassword = userInfo.getOrNull(1)
                    }
                }
            }

            if (givenUri == null)
                givenUri = intent.getStringExtra(EXTRA_URL)

            // always prefer username/password from the extras
            if (intent.hasExtra(EXTRA_USERNAME))
                givenUsername = intent.getStringExtra(EXTRA_USERNAME)
            if (intent.hasExtra(EXTRA_PASSWORD))
                givenPassword = intent.getStringExtra(EXTRA_PASSWORD)

            return LoginInfo(
                baseUri = try {
                    URI(givenUri)
                } catch (_: Exception) {
                    null
                },
                credentials = Credentials(
                    username = givenUsername,
                    password = givenPassword
                )
            )
        }

    }

    enum class Phase {
        LOGIN_TYPE,
        LOGIN_DETAILS,
        DETECT_RESOURCES,
        ACCOUNT_DETAILS
    }


    @Inject
    lateinit var loginTypesProvider: LoginTypesProvider


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                LoginScreen(
                    loginTypesProvider = loginTypesProvider,
                    initialLoginInfo = loginInfoFromIntent(intent),
                    initialLoginType = loginTypesProvider.intentToInitialLoginType(intent),
                    onFinish = { finish() }
                )
            }
        }
    }

}