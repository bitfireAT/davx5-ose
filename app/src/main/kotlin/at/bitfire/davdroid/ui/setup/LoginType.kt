package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.Composable

interface LoginType {

    fun isGeneric(): Boolean
    fun getOrder(): Int?
    fun getName(): String

    @Composable
    fun LoginForm(
        initialLoginInfo: LoginInfo?,
        updateCanContinue: (Boolean) -> Unit,
        updateLoginInfo: (LoginInfo) -> Unit,
        onLogin: () -> Unit
    )

}