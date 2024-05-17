/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import javax.inject.Inject

class StandardLoginTypesProvider @Inject constructor() : LoginTypesProvider {

    companion object {
        val genericLoginTypes = listOf(
            UrlLogin,
            EmailLogin,
            AdvancedLogin
        )

        val specificLoginTypes = listOf(
            GoogleLogin,
            NextcloudLogin
        )
    }

    override val defaultLoginType = UrlLogin

    override fun intentToInitialLoginType(intent: Intent) =
        if (intent.hasExtra(LoginActivity.EXTRA_LOGIN_FLOW))
            Pair(NextcloudLogin, true)
        else
            Pair(defaultLoginType, false)

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