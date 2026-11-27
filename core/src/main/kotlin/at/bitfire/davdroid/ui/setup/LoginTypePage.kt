/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginTypePage(
    snackbarHostState: SnackbarHostState,
    model: LoginScreenViewModel = viewModel()
) {
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
            // API 34+: credentials storage is managed in the view model; older: autofill save prompt
            autofillManager.onCredentialsSubmitted()
            model.navToNextPage()
        }
    )
}