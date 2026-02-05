/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.ui.setup

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.ui.setup.AdvancedLogin
import at.bitfire.davdroid.ui.setup.EmailLogin
import at.bitfire.davdroid.ui.setup.FastmailLogin
import at.bitfire.davdroid.ui.setup.GoogleLogin
import at.bitfire.davdroid.ui.setup.LoginActivity
import at.bitfire.davdroid.ui.setup.LoginInfo
import at.bitfire.davdroid.ui.setup.LoginType
import at.bitfire.davdroid.ui.setup.LoginTypesProvider
import at.bitfire.davdroid.ui.setup.LoginTypesProvider.LoginAction
import at.bitfire.davdroid.ui.setup.NextcloudLogin
import at.bitfire.davdroid.ui.setup.UrlLogin
import java.util.logging.Logger
import javax.inject.Inject

class StandardLoginTypesProvider @Inject constructor(
    private val logger: Logger
) : LoginTypesProvider {

    companion object {
        val genericLoginTypes = listOf(
            UrlLogin,
            EmailLogin,
            AdvancedLogin
        )

        val specificLoginTypes = listOf(
            FastmailLogin,
            GoogleLogin,
            NextcloudLogin
        )
    }

    override val defaultLoginType = UrlLogin

    override fun intentToInitialLoginType(intent: Intent): LoginAction =
        intent.data?.normalizeScheme().let { uri ->
            when {
                intent.hasExtra(LoginActivity.EXTRA_LOGIN_FLOW) ->
                    LoginAction(NextcloudLogin, true)
                uri?.scheme == "mailto" ->
                    LoginAction(EmailLogin, true)
                listOf("caldavs", "carddavs", "davx5", "http", "https").any { uri?.scheme == it } ->
                    LoginAction(UrlLogin, true)
                else -> {
                    logger.warning("Did not understand login intent: $intent")
                    LoginAction(defaultLoginType, false) // Don't skip login type page if intent is unclear
                }
            }
        }

    @Composable
    override fun LoginTypePage(
        snackbarHostState: SnackbarHostState,
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        setInitialLoginInfo: (LoginInfo) -> Unit,
        onContinue: () -> Unit
    ) {
        StandardLoginTypePage(
            selectedLoginType = selectedLoginType,
            onSelectLoginType = onSelectLoginType,
            setInitialLoginInfo = setInitialLoginInfo,
            onContinue = onContinue
        )
    }

}