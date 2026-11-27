/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.ui.autofill.AutofillManager

/**
 * Whether the platform Credential Manager (API 34+) is used to offer saving credentials.
 *
 * On older versions, the Jetpack Credential Manager would need a Google Play services provider,
 * so there the autofill framework's save prompt is used instead.
 */
val useCredentialManager: Boolean
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/**
 * Called when the user submits credentials. On API 34+, saving is done through the Credential Manager
 * (see [LoginScreenViewModel]), so the autofill save request is cancelled to avoid double prompts.
 * On older versions, it's committed so that the autofill service can offer to save the credentials.
 */
fun AutofillManager?.onCredentialsSubmitted() {
    if (useCredentialManager)
        this?.cancel()
    else
        this?.commit()
}
