/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginTypePage(
    model: LoginScreenModel = viewModel()
) {
    val uiState = model.loginTypeUiState

    // show login type selection page
    model.loginTypesProvider.LoginTypePage(
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