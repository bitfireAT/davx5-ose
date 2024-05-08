/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.app.Application
import androidx.compose.runtime.Composable
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.TasksActivity
import at.bitfire.davdroid.ui.TasksCard
import at.bitfire.davdroid.util.TaskUtils
import javax.inject.Inject

class TasksIntroPage @Inject constructor(
    private val application: Application,
    private val settingsManager: SettingsManager
): IntroPage {

    override fun getShowPolicy(): IntroPage.ShowPolicy {
        return if (TaskUtils.isAvailable(application) || settingsManager.getBooleanOrNull(TasksActivity.Model.HINT_OPENTASKS_NOT_INSTALLED) == false)
                IntroPage.ShowPolicy.DONT_SHOW
            else
                IntroPage.ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        TasksCard()
    }

}