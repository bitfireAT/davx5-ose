/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.TasksCard
import at.bitfire.davdroid.ui.TasksModel
import com.github.appintro.R
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TasksIntroFragment : Fragment() {
    val model: TasksModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    TasksCard(
                        modifier = Modifier
                            .padding(bottom = dimensionResource(R.dimen.appintro2_bottombar_height)),
                        model = model
                    )
                }
            }
        }
    }


    class Factory @Inject constructor(
        val settingsManager: SettingsManager
    ): IntroFragmentFactory {

        override fun getOrder(context: Context): Int {
            return if (!TaskUtils.isAvailable(context) && settingsManager.getBooleanOrNull(TasksModel.HINT_OPENTASKS_NOT_INSTALLED) != false)
                10
            else
                IntroFragmentFactory.DONT_SHOW
        }

        override fun create() = TasksIntroFragment()

    }

}
