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
fun LoginTypePage(
    snackbarHostState: SnackbarHostState,
    model: LoginScreenViewModel = viewModel()
) {
    val activity = LocalActivity.current
    val autofillManager = LocalAutofillManager.current
    val uiState = model.loginTypeUiState

    // show login type selection page
    model.loginTypesProvider.LoginTypePage(
        snackbarHostState = snackbarHostState,
        selectedLoginType = uiState.loginType,
        onSelectLoginType = { loginType ->
            model.selectLoginType(loginType)
        },
        setInitialLoginInfo = { loginInfo ->
            model.updateLoginInfo(loginInfo)
        },
        onContinue = {
            // Credentials storage is managed in the view model
            autofillManager?.cancel()
            model.navToNextPage(activity)
        }
    )
}