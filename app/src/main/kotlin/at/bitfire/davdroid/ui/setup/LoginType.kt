package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.runtime.Composable

interface LoginType {

    val title: String

    /** Whether this login type is shown in the generic section and expanded with [Content] as soon as it is selected. */
    val isGeneric: Boolean

    /** Optional URL to a provider-specific help page. */
    val helpUrl: Uri?

    /**
     * Appears when the login type is currently selected.
     *
     * When [isGeneric] is `true`, it appears as expanded section on the login type page.
     * Otherwise, it appears on a separate page.
     */
    @Composable
    fun Content(
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo, readyToLogin: Boolean?) -> Unit
    )

}