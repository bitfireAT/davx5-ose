/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.app.Application
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.ui.PermissionsContent
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.util.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.ical4android.TaskProvider

class PermissionsIntroPage: IntroPage {

    override fun getShowPolicy(application: Application): IntroPage.ShowPolicy {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS + CALENDAR_PERMISSIONS +
                TaskProvider.PERMISSIONS_JTX +
                TaskProvider.PERMISSIONS_OPENTASKS +
                TaskProvider.PERMISSIONS_TASKS_ORG
        return if (PermissionUtils.haveAnyPermission(application, permissions))
            IntroPage.ShowPolicy.DONT_SHOW
        else
            IntroPage.ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        PermissionsContent()
    }

}