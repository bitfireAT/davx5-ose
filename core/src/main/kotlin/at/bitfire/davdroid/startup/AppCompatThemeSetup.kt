/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_DEFAULT
import at.bitfire.davdroid.ui.UiUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Sets the light/dark mode according to the current settings.
 *
 * Must run synchronously on the main thread: when called from a background thread, it may
 * recreate the current activity and cause an [IllegalStateException] in rare cases.
 */
class AppCompatThemeSetup @Inject constructor(
    @ApplicationContext private val context: Context
) : StartupAction {

    override val priority = PRIORITY_DEFAULT

    override fun onAppCreate() {
        UiUtils.updateTheme(context)
    }

}
