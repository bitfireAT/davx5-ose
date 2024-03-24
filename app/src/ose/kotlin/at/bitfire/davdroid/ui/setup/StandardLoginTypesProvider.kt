package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.compose.runtime.Composable
import javax.inject.Inject

class StandardLoginTypesProvider @Inject constructor() : LoginTypesProvider {

    companion object {
        val genericLoginTypes = listOf(
            LoginTypeUrl,
            LoginTypeEmail,
            LoginTypeAdvanced
        )

        val specificLoginTypes = listOf(
            LoginTypeGoogle,
            LoginTypeNextcloud
        )
    }

    override val defaultLoginType = LoginTypeUrl

    override fun intentToInitialLoginType(intent: Intent) =
        if (intent.hasExtra(LoginActivity.EXTRA_LOGIN_FLOW))
            LoginTypeNextcloud
        else
            LoginTypeUrl

    @Composable
    override fun LoginTypePage(selectedLoginType: LoginType, onSelectLoginType: (LoginType) -> Unit, onContinue: () -> Unit) {
        StandardLoginTypePage(
            selectedLoginType = selectedLoginType,
            onSelectLoginType = onSelectLoginType,
            onContinue = onContinue
        )
    }

}