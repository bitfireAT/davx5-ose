/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.ui.intro

import at.bitfire.davdroid.ui.intro.BackupsPage
import at.bitfire.davdroid.ui.intro.BatteryOptimizationsPage
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.ui.intro.OpenSourcePage
import at.bitfire.davdroid.ui.intro.PermissionsIntroPage
import at.bitfire.davdroid.ui.intro.TasksIntroPage
import at.bitfire.davdroid.ui.intro.WelcomePage
import javax.inject.Inject

class OseIntroPageFactory @Inject constructor(
    backupsPage: BackupsPage,
    batteryOptimizationsPage: BatteryOptimizationsPage,
    openSourcePage: OpenSourcePage,
    permissionsIntroPage: PermissionsIntroPage,
    tasksIntroPage: TasksIntroPage
): IntroPageFactory {

    override val introPages = arrayOf(
        WelcomePage(),
        tasksIntroPage,
        permissionsIntroPage,
        batteryOptimizationsPage,
        backupsPage,
        openSourcePage
    )

}