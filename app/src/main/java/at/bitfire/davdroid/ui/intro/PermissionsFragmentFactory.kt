package at.bitfire.davdroid.ui.intro

import android.content.Context
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.CONTACT_PERMSSIONS
import at.bitfire.davdroid.PermissionUtils.TASKS_PERMISSIONS
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.PermissionsFragment
import at.bitfire.davdroid.ui.intro.IIntroFragmentFactory.ShowMode

class PermissionsFragmentFactory: IIntroFragmentFactory {

    override fun shouldBeShown(context: Context, settings: Settings): IIntroFragmentFactory.ShowMode {
        // show PermissionsFragment as intro fragment when no permissions are granted
        val permissions = CONTACT_PERMSSIONS + CALENDAR_PERMISSIONS + TASKS_PERMISSIONS
        return if (PermissionUtils.haveAnyPermission(context, permissions))
            ShowMode.DONT_SHOW
        else
            ShowMode.SHOW
    }

    override fun create() = PermissionsFragment()

}