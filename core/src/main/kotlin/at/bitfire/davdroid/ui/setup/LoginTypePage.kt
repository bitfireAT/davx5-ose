/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginTypePage(
    snackbarHostState: SnackbarHostState,
    model: LoginScreenModel = viewModel()
) {
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
            model.navToNextPage()
        }
    )
}