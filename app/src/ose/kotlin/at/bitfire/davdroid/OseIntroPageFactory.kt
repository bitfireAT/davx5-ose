/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.ui.intro.PermissionsIntroPage
import at.bitfire.davdroid.ui.intro.TasksIntroPage
import at.bitfire.davdroid.ui.intro.WelcomePage
import javax.inject.Inject

class OseIntroPageFactory @Inject constructor(): IntroPageFactory {

    override val introPages = arrayOf(
        WelcomePage(),
        TasksIntroPage(),
        PermissionsIntroPage(),
        BatteryOptimizationsPage(),
        OpenSourcePage()
    )

}