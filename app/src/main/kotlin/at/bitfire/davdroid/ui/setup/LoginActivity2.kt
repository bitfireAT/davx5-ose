package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.account.AccountActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
@AndroidEntryPoint
class LoginActivity2: AppCompatActivity() {

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

    }

    enum class Phase {
        LOGIN_TYPE,
        LOGIN_DETAILS,
        DETECT_RESOURCES,
        ACCOUNT_DETAILS
    }

    private val genericLoginTypes = listOf(
        LoginTypeUrl()
    )
    private val specificLoginTypes = listOf(
        LoginTypeGoogle(),
        LoginTypeNextcloud()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                LoginScreen(
                    onFinish = { finish() }
                )
            }
        }
    }

    @Composable
    @Preview
    fun LoginScreen(
        onFinish: () -> Unit = {}
    ) {
        val uriHandler = LocalUriHandler.current

        var phase: Phase by remember { mutableStateOf(Phase.LOGIN_TYPE) }
        var selectedLoginType: LoginType by remember { mutableStateOf(genericLoginTypes.first()) }

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
                                Phase.LOGIN_TYPE -> testedWithUrl
                                Phase.LOGIN_DETAILS -> selectedLoginType.helpUrl ?: testedWithUrl
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
            Box(Modifier
                .fillMaxSize()
                .padding(padding)
            ) {
                var loginInfo by remember { mutableStateOf(LoginInfo()) }
                var foundConfig by remember { mutableStateOf<DavResourceFinder.Configuration?>(null) }

                when (phase) {
                    Phase.LOGIN_TYPE ->
                        StandardLoginTypePage(
                            genericLoginTypes = genericLoginTypes,
                            specificLoginTypes = specificLoginTypes,
                            selectedLoginType = selectedLoginType,
                            onSelectLoginType = { selectedLoginType = it },
                            onContinue = {
                                phase = Phase.LOGIN_DETAILS
                            }
                        )

                    Phase.LOGIN_DETAILS -> {
                        BackHandler {
                            phase = Phase.LOGIN_TYPE
                        }

                        selectedLoginType.Content(
                            snackbarHostState = snackbarHostState,
                            loginInfo = loginInfo,
                            onUpdateLoginInfo = { loginInfo = it },
                            onDetectResources = {
                                phase = Phase.DETECT_RESOURCES
                            }
                        )
                    }

                    Phase.DETECT_RESOURCES -> {
                        BackHandler(
                            onBack = { phase = Phase.LOGIN_TYPE }
                        )

                        DetectResourcesPage(
                            loginInfo = loginInfo,
                            onSuccess = {
                                foundConfig = it
                                phase = Phase.ACCOUNT_DETAILS
                            }
                        )
                    }

                    Phase.ACCOUNT_DETAILS ->
                        foundConfig?.let {
                            val context = LocalContext.current
                            AccountDetailsPage(
                                foundConfig = it,
                                onBack = { phase = Phase.LOGIN_TYPE },
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

}