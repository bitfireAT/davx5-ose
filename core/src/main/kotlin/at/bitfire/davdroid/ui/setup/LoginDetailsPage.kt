/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginDetailsPage(
    snackbarHostState: SnackbarHostState,
    model: LoginScreenViewModel = viewModel()
) {
    val activity = LocalActivity.current
    val autofillManager = LocalAutofillManager.current

    val uiState = model.loginDetailsUiState
    uiState.loginType.LoginScreen(
        snackbarHostState = snackbarHostState,
        initialLoginInfo = uiState.loginInfo,
        onLogin = { loginInfo ->
            model.updateLoginInfo(loginInfo)

            // Credentials storage is managed in the view model
            autofillManager?.cancel()

            model.navToNextPage(activity)
        }
    )
}