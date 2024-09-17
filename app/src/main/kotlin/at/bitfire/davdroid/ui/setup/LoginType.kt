/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.net.Uri
import android.os.Parcelable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface LoginType: Parcelable {

    val title: Int

    /** Optional URL to a provider-specific help page. */
    val helpUrl: Uri?

    @Composable
    fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    )

}