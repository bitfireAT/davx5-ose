/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Context
import android.view.Menu
import android.view.MenuItem

interface AccountsDrawerHandler {

    fun initMenu(context: Context, menu: Menu)

    fun onNavigationItemSelected(activity: Activity, item: MenuItem)

}