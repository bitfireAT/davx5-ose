/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AccountsActivity: AppCompatActivity() {

    @Inject
    lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private val introActivityLauncher = registerForActivityResult(IntroActivity.Contract) { cancelled ->
        if (cancelled)
            finish()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle "Sync all" intent from launcher shortcut
        val syncAccounts = intent.action == Intent.ACTION_SYNC

        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = AccountsRoute
            ) {
                composable<AccountsRoute> {
                    AccountsScreen(
                        initialSyncAccounts = syncAccounts,
                        onShowAppIntro = {
                            introActivityLauncher.launch(null)
                        },
                        accountsDrawerHandler = accountsDrawerHandler,
                        onAddAccount = {
                            startActivity(Intent(this@AccountsActivity, LoginActivity::class.java))
                        },
                        onShowAccount = { account ->
                            val intent = Intent(this@AccountsActivity, AccountActivity::class.java)
                            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                            startActivity(intent)
                        },
                        onManagePermissions = {
                            startActivity(Intent(this@AccountsActivity, PermissionsActivity::class.java))
                        }
                    )
                }
            }
        }
    }

}