package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable

interface LoginType {

    val title: Int

    /** Optional URL to a provider-specific help page. */
    val helpUrl: Uri?

    @Composable
    fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit,
        onFinish: () -> Unit
    )

}