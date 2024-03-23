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
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.account.AccountActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity2: AppCompatActivity() {

    enum class Phase {
        LOGIN_TYPE,
        LOGIN_DETAILS,
        DETECT_RESOURCES,
        ACCOUNT_DETAILS
    }

    private val loginTypes = arrayOf(
        LoginTypeUrl(this),
        LoginTypeGoogle(this)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                LoginScreen(
                    onBack = { onSupportNavigateUp() },
                    onFinish = { finish() }
                )
            }
        }
    }

    @Composable
    fun LoginScreen(
        onBack: () -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        val uriHandler = LocalUriHandler.current

        var phase by remember { mutableStateOf(Phase.LOGIN_TYPE) }
        var selectedLoginType by remember { mutableStateOf<LoginType?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                        val helpUri: Uri? =
                            when (phase) {
                                Phase.LOGIN_TYPE -> Constants.HOMEPAGE_URL.buildUpon()
                                    .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                                    .withStatParams("LoginActivity")
                                    .build()
                                Phase.LOGIN_DETAILS -> selectedLoginType?.helpUrl
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
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                var loginInfo by remember { mutableStateOf(LoginInfo()) }
                var foundConfig by remember { mutableStateOf<DavResourceFinder.Configuration?>(null) }

                when (phase) {
                    Phase.LOGIN_TYPE ->
                        StandardLoginTypePage(
                            genericLoginTypes = loginTypes.filter { it.isGeneric }.toList(),
                            specificLoginTypes = loginTypes.filter { !it.isGeneric }.toList(),
                            selectedLoginType = selectedLoginType,
                            onSelectLoginType = { selectedLoginType = it },
                            loginInfo = loginInfo,
                            onUpdateLoginInfo = { loginInfo = it },
                            onContinue = {
                                phase = Phase.LOGIN_DETAILS
                            },
                            onLogin = {
                                phase = Phase.DETECT_RESOURCES
                            }
                        )

                    Phase.LOGIN_DETAILS -> {
                        BackHandler {
                            phase = Phase.LOGIN_TYPE
                        }

                        var readyToLogin by remember { mutableStateOf<Boolean?>(null) }
                        selectedLoginType?.Content(
                            loginInfo = loginInfo,
                            onUpdateLoginInfo = { newLoginInfo, _readyToLogin ->
                                loginInfo = newLoginInfo
                                readyToLogin = _readyToLogin
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