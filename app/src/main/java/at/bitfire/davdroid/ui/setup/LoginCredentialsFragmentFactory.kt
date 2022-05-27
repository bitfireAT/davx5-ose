/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.content.Intent
import androidx.fragment.app.Fragment

interface LoginCredentialsFragmentFactory {

    fun getFragment(intent: Intent): Fragment?

}