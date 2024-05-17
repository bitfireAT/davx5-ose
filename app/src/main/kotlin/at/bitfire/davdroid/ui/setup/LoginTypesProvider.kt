/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

interface LoginTypesProvider {

    val defaultLoginType: LoginType

    /**
     * Which login type to use and whether to skip the login type selection page. Used for Nextcloud
     * login flow. May be used by other login flows.
     */
    fun intentToInitialLoginType(intent: Intent): Pair<LoginType, Boolean> = Pair(defaultLoginType, false)

    /** Whether the [LoginTypePage] may be non-interactive. This causes it to be skipped in back navigation. */
    val maybeNonInteractive: Boolean
        get() = false

    @Composable
    fun LoginTypePage(
        snackbarHostState: SnackbarHostState,
        selectedLoginType: LoginType,
        onSelectLoginType: (LoginType) -> Unit,
        setInitialLoginInfo: (LoginInfo) -> Unit,
        onContinue: () -> Unit
    )

}