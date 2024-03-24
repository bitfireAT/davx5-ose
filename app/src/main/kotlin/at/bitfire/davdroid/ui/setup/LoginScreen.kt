package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.account.AccountActivity

@Composable
fun LoginScreen(
    loginTypesProvider: LoginTypesProvider,
    initialLoginInfo: LoginInfo = LoginInfo(),
    initialLoginType: LoginType,
    onFinish: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    val initialPhase =
        if (initialLoginInfo.baseUri != null)
            LoginActivity.Phase.LOGIN_DETAILS
        else
            LoginActivity.Phase.LOGIN_TYPE
    var phase: LoginActivity.Phase by remember { mutableStateOf(initialPhase) }
    var selectedLoginType: LoginType by remember { mutableStateOf(initialLoginType) }

    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onFinish) {
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
                    val testedWithUrl = Constants.HOMEPAGE_URL.buildUpon()
                        .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                        .withStatParams("LoginActivity")
                        .build()
                    val helpUri: Uri? =
                        when (phase) {
                            LoginActivity.Phase.LOGIN_TYPE -> testedWithUrl
                            LoginActivity.Phase.LOGIN_DETAILS -> selectedLoginType.helpUrl ?: testedWithUrl
                            else -> null
                        }
                    if (helpUri != null)
                        IconButton(onClick = {
                            // show tested-with page
                            uriHandler.openUri(helpUri.toString())
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
            var loginInfo by remember { mutableStateOf(initialLoginInfo) }
            var foundConfig by remember { mutableStateOf<DavResourceFinder.Configuration?>(null) }

            when (phase) {
                LoginActivity.Phase.LOGIN_TYPE ->
                    loginTypesProvider.LoginTypePage(
                        selectedLoginType = selectedLoginType,
                        onSelectLoginType = { selectedLoginType = it },
                        loginInfo = loginInfo,
                        onUpdateLoginInfo = { loginInfo = it },
                        onContinue = {
                            phase = LoginActivity.Phase.LOGIN_DETAILS
                        },
                        onFinish = onFinish
                    )

                LoginActivity.Phase.LOGIN_DETAILS -> {
                    BackHandler {
                        phase = LoginActivity.Phase.LOGIN_TYPE
                    }

                    selectedLoginType.Content(
                        snackbarHostState = snackbarHostState,
                        loginInfo = loginInfo,
                        onUpdateLoginInfo = { loginInfo = it },
                        onDetectResources = {
                            phase = LoginActivity.Phase.DETECT_RESOURCES
                        },
                        onFinish = onFinish
                    )
                }

                LoginActivity.Phase.DETECT_RESOURCES -> {
                    BackHandler(
                        onBack = { phase = LoginActivity.Phase.LOGIN_TYPE }
                    )

                    DetectResourcesPage(
                        loginInfo = loginInfo,
                        onSuccess = {
                            foundConfig = it
                            phase = LoginActivity.Phase.ACCOUNT_DETAILS
                        }
                    )
                }

                LoginActivity.Phase.ACCOUNT_DETAILS ->
                    foundConfig?.let {
                        val context = LocalContext.current
                        AccountDetailsPage(
                            loginInfo = loginInfo,
                            foundConfig = it,
                            onBack = { phase = LoginActivity.Phase.LOGIN_TYPE },
                            onAccountCreated = { account ->
                                onFinish()

                                val intent = Intent(context, AccountActivity::class.java)
                                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                                context.startActivity(intent)
                            }
                        )
                    }

            }
        }
    }
}