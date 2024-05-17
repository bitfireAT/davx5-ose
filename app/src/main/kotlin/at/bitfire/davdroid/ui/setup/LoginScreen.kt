/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme

@Composable
fun LoginScreen(
    initialLoginInfo: LoginInfo = LoginInfo(),
    skipLoginTypePage: Boolean = false,
    initialLoginType: LoginType = UrlLogin,
    onNavUp: () -> Unit,
    onFinish: (Account?) -> Unit
) {
    val model: LoginScreenModel = hiltViewModel { factory: LoginScreenModel.Factory ->
        factory.create(skipLoginTypePage, initialLoginInfo, initialLoginType)
    }

    // handle back/up navigation
    BackHandler {
        model.navBack()
    }
    if (model.finish) {
        onFinish(null)
        return
    }

    LoginScreenContent(
        page = model.page,
        onNavUp = onNavUp,
        onFinish = onFinish
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LoginScreenContent(
    page: LoginScreenModel.Page,
    onNavUp: () -> Unit = {},
    onFinish: (newAccount: Account?) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                stringResource(R.string.navigate_up)
                            )
                        }
                    },
                    title = {
                        Text(stringResource(R.string.login_title))
                    },
                    actions = {
                        val uriHandler = LocalUriHandler.current
                        val testedWithUri = Constants.HOMEPAGE_URL.buildUpon()
                            .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                            .withStatParams("LoginActivity")
                            .build()
                        IconButton(onClick = {
                            // show tested-with page
                            uriHandler.openUri(testedWithUri.toString())
                        }) {
                            Icon(Icons.AutoMirrored.Default.Help, stringResource(R.string.help))
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                when (page) {
                    LoginScreenModel.Page.LoginType ->
                        LoginTypePage(snackbarHostState = snackbarHostState)

                    LoginScreenModel.Page.LoginDetails ->
                        LoginDetailsPage(snackbarHostState = snackbarHostState)

                    LoginScreenModel.Page.DetectResources ->
                        DetectResourcesPage()

                    LoginScreenModel.Page.AccountDetails ->
                        AccountDetailsPage(
                            snackbarHostState = snackbarHostState,
                            onAccountCreated = { account ->
                                onFinish(account)
                            }
                        )
                }

            }
        }
    }
}