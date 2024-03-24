package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.R

class LoginTypeAdvanced : LoginType {

    override val title: Int
        get() = R.string.login_type_advanced

    override val helpUrl: Uri?
        get() = null


    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit
    ) {
        // TODO
    }

}