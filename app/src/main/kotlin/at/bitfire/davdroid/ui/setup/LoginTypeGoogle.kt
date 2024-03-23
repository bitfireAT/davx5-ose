package at.bitfire.davdroid.ui.setup

import android.content.Context
import android.net.Uri
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R

class LoginTypeGoogle(
    val context: Context
) : LoginType {

    override val title: String
        get() = context.getString(R.string.login_type_google)

    override val isGeneric: Boolean
        get() = false

    override val helpUrl: Uri
        get() = Constants.HOMEPAGE_URL.buildUpon()
            .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
            .appendPath("google")
            .withStatParams("LoginTypeGoogle")
            .build()

    @Composable
    override fun Content(
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo, readyToLogin: Boolean?) -> Unit
    ) {
        Text("Google login")
    }

}