/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.compose.runtime.Composable

interface LoginTypesProvider {

    val defaultLoginType: LoginType

    fun intentToInitialLoginType(intent: Intent): LoginType

    /** Whether the [LoginTypePage] may be non-interactive. This causes it to be skipped in back navigation. */
    val maybeNonInteractive: Boolean
        get() = false

    @Composable
    fun LoginTypePage(
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        setInitialLoginInfo: (LoginInfo) -> Unit,
        onContinue: () -> Unit
    )

}