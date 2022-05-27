/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.davdroid.R
import at.bitfire.ical4android.TaskProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class PermissionsIntroFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.intro_permissions, container, false)


    class Factory @Inject constructor(): IntroFragmentFactory {

        override fun getOrder(context: Context): Int {
            // show PermissionsFragment as intro fragment when no permissions are granted
            val permissions = CONTACT_PERMISSIONS + CALENDAR_PERMISSIONS + TaskProvider.PERMISSIONS_OPENTASKS
            return if (PermissionUtils.haveAnyPermission(context, permissions))
                IntroFragmentFactory.DONT_SHOW
            else
                50
        }

        override fun create() = PermissionsIntroFragment()

    }

}