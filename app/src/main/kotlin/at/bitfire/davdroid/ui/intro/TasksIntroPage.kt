/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.TasksActivity
import at.bitfire.davdroid.ui.TasksCard
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class TasksIntroPage : IntroPage {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TasksIntroPageEntryPoint {
        fun settingsManager(): SettingsManager
    }

    override fun getShowPolicy(application: Application): IntroPage.ShowPolicy {
        val settingsManager = EntryPointAccessors.fromApplication(application, TasksIntroPageEntryPoint::class.java).settingsManager()

        return if (TaskUtils.isAvailable(application) || settingsManager.getBooleanOrNull(TasksActivity.Model.HINT_OPENTASKS_NOT_INSTALLED) == false)
                IntroPage.ShowPolicy.DONT_SHOW
            else
                IntroPage.ShowPolicy.SHOW_ALWAYS
    }

    @Composable
    override fun ComposePage() {
        TasksCard(model = viewModel<TasksActivity.Model>())
    }

}