/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.content.Context
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.ui.PermissionsModel
import at.bitfire.davdroid.ui.PermissionsScreen
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.util.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PermissionsIntroPage @Inject constructor(
    @ApplicationContext private val context: Context
): IntroPage() {

    var model: PermissionsModel? = null

    override fun getShowPolicy(): ShowPolicy {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMISSIONS + CALENDAR_PERMISSIONS +
                TaskProvider.PERMISSIONS_JTX +
                TaskProvider.PERMISSIONS_OPENTASKS +
                TaskProvider.PERMISSIONS_TASKS_ORG
        return if (PermissionUtils.haveAnyPermission(context, permissions))
            ShowPolicy.DONT_SHOW
        else
            ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        PermissionsScreen()
    }

}