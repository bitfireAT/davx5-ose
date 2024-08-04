/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import at.bitfire.davdroid.ui.intro.IntroActivity
import at.bitfire.davdroid.ui.setup.LoginActivity
import kotlinx.serialization.Serializable

@Serializable
object AccountsDestination

fun NavGraphBuilder.accountsDestination(
    initialSyncAccounts: Boolean,
    onClose: () -> Unit,
    onNavigateToAccount: (String) -> Unit
) {
    composable<AccountsDestination> {
        val showIntro = rememberLauncherForActivityResult(IntroActivity.Contract) { cancelled ->
            if (cancelled)
                onClose()
        }

        val context = LocalContext.current
        AccountsScreen(
            initialSyncAccounts = initialSyncAccounts,
            onShowAppIntro = {
                showIntro.launch(Unit)
            },
            onAddAccount = {
                context.startActivity(Intent(context, LoginActivity::class.java))
            },
            onShowAccount = { account ->
                onNavigateToAccount(account.name)
            },
            onManagePermissions = {
                context.startActivity(Intent(context, PermissionsActivity::class.java))
            }
        )
    }
}