/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.compose.runtime.Composable

interface LoginTypesProvider {

    val defaultLoginType: LoginType

    fun intentToInitialLoginType(intent: Intent): LoginType

    @Composable
    fun LoginTypePage(
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onContinue: () -> Unit,
        onFinish: () -> Unit
    )

}