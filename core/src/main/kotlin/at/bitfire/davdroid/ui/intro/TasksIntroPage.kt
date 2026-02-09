/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.compose.runtime.Composable
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.ui.TasksCard
import at.bitfire.davdroid.ui.TasksModel
import javax.inject.Inject

class TasksIntroPage @Inject constructor(
    private val settingsManager: SettingsManager,
    private val tasksAppManager: TasksAppManager
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy {
        return if (tasksAppManager.currentProvider() != null || settingsManager.getBooleanOrNull(TasksModel.HINT_OPENTASKS_NOT_INSTALLED) == false)
                ShowPolicy.DONT_SHOW
            else
                ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        TasksCard()
    }

}