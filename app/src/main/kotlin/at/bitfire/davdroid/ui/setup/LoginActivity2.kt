package at.bitfire.davdroid.ui.setup

import android.content.Intent
import android.os.Bundle
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
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity2: AppCompatActivity() {

    enum class Phase {
        TYPE_AND_CREDENTIALS,
        DETECT_RESOURCES,
        ACCOUNT_DETAILS
    }

    @Inject
    lateinit var loginTypes: Set<@JvmSuppressWildcards LoginType>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Login()
            }
        }
    }


    @Composable
    fun Login() {
        val uriHandler = LocalUriHandler.current

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onSupportNavigateUp() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = {
                        Text(stringResource(R.string.login_title))
                    },
                    actions = {
                        IconButton(onClick = {
                            // show tested-with page
                            uriHandler.openUri(
                                Constants.HOMEPAGE_URL.buildUpon()
                                    .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                                    .withStatParams("LoginActivity")
                                    .build().toString()
                            )
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
                var phase by remember { mutableStateOf(Phase.TYPE_AND_CREDENTIALS) }
                var loginInfo by remember { mutableStateOf<LoginInfo?>(null) }
                var foundConfig by remember { mutableStateOf<DavResourceFinder.Configuration?>(null) }

                when (phase) {
                    Phase.TYPE_AND_CREDENTIALS ->
                        LoginTypeAndCredentialsPage(
                            genericLoginTypes = loginTypes
                                .filter { it.isGeneric() }
                                .sortedWith { a, b ->
                                    val aOrder = a.getOrder() ?: 0
                                    val bOrder = b.getOrder() ?: 0
                                    if (aOrder != bOrder)
                                        aOrder.compareTo(bOrder)
                                    else
                                        a.getName().compareTo(b.getName(), ignoreCase = true)
                                },
                            initialLoginInfo = loginInfo,
                            onLogin = {
                                loginInfo = it
                                phase = Phase.DETECT_RESOURCES
                            }
                        )

                    Phase.DETECT_RESOURCES ->
                        loginInfo?.let {
                            DetectResourcesPage(
                                loginInfo = it,
                                onSuccess = {
                                    foundConfig = it
                                    phase = Phase.ACCOUNT_DETAILS
                                },
                                onBack = { phase = Phase.TYPE_AND_CREDENTIALS }
                            )
                        }

                    Phase.ACCOUNT_DETAILS ->
                        foundConfig?.let {
                            val context = LocalContext.current
                            AccountDetailsPage(
                                foundConfig = it,
                                onBack = { phase = Phase.TYPE_AND_CREDENTIALS },
                                onAccountCreated = { account ->
                                    finish()

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