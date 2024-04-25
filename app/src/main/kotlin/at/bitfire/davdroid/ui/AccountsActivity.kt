/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class AccountsActivity: AppCompatActivity() {

    @Inject
    lateinit var accountsDrawerHandler: AccountsDrawerHandler

    private val introActivityLauncher = registerForActivityResult(IntroActivity.Contract) { cancelled ->
        if (cancelled)
            finish()
    }

    val model by viewModels<AccountsModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // use a separate thread to check whether IntroActivity should be shown
        if (savedInstanceState == null) {
            // move to Model
            CoroutineScope(Dispatchers.Default).launch {
                if (IntroActivity.shouldShowIntroActivity(this@AccountsActivity))
                    introActivityLauncher.launch(null)
            }
        }

        // handle "Sync all" intent from launcher shortcut
        if (savedInstanceState == null && intent.action == Intent.ACTION_SYNC)
            model.syncAllAccounts()

        setContent {
            AccountsScreen(
                accountsDrawerHandler = accountsDrawerHandler,
                onAddAccount = {
                    startActivity(Intent(this, LoginActivity::class.java))
                },
                onShowAccount = { account ->
                    val intent = Intent(this, AccountActivity::class.java)
                    intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                }
            )
        }
    }

}