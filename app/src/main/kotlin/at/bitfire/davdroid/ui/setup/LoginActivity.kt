/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.ui.account.AccountActivity
import dagger.hilt.android.AndroidEntryPoint
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
@AndroidEntryPoint
class LoginActivity @Inject constructor(): AppCompatActivity() {

    @Inject lateinit var loginTypesProvider: LoginTypesProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (initialLoginType, skipLoginTypePage) = loginTypesProvider.intentToInitialLoginType(intent)

        setContent {
            LoginScreen(
                initialLoginType = initialLoginType,
                skipLoginTypePage = skipLoginTypePage,
                initialLoginInfo = loginInfoFromIntent(intent),
                onNavUp = { onSupportNavigateUp() },
                onFinish = { newAccount ->
                    finish()

                    if (newAccount != null) {
                        val intent = Intent(this, AccountActivity::class.java)
                        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, newAccount)
                        startActivity(intent)
                    }
                }
            )
        }
    }

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


        /**
         * Extracts login information from given intent, validates it and returns it in [LoginInfo].
         *
         * @param intent Contains base url, username and password.
         * @return Extracted login info. Contains null values if given info is invalid.
         */
        fun loginInfoFromIntent(intent: Intent): LoginInfo {
            var givenUri: String? = null
            var givenUsername: String? = null
            var givenPassword: String? = null

            // extract URI or email and optionally username/password from Intent data
            val logger = Logger.getGlobal()
            intent.data?.normalizeScheme()?.let { uri ->
                val realScheme = when (uri.scheme) {
                    // replace caldav[s]:// and carddav[s]:// with http[s]://
                    "caldav", "carddav" -> "http"
                    "caldavs", "carddavs", "davx5" -> "https"

                    // keep these
                    "http", "https", "mailto" -> uri.scheme

                    // unknown scheme
                    else -> null
                }

                when (realScheme) {
                    "http", "https" -> {
                        // extract user info
                        uri.userInfo?.split(':')?.let { userInfo ->
                            givenUsername = userInfo.getOrNull(0)
                            givenPassword = userInfo.getOrNull(1)
                        }

                        // use real scheme, drop user info and fragment
                        givenUri = try {
                            URI(realScheme, null, uri.host, uri.port, uri.path, uri.query, null).toString()
                        } catch (_: URISyntaxException) {
                            logger.warning("Couldn't construct URI from login Intent data: $uri")
                            null
                        }
                    }

                    "mailto" ->
                        givenUsername = uri.schemeSpecificPart
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

}