package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.TasksFragment
import at.bitfire.davdroid.ui.intro.IIntroFragmentFactory.ShowMode

class TasksFragmentFactory: IIntroFragmentFactory {

    override fun shouldBeShown(context: Context, settingsManager: SettingsManager): ShowMode {
        // On Android <6, OpenTasks must be installed before DAVx5, so this fragment is not useful.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return ShowMode.DONT_SHOW

        return if (!TaskUtils.isAvailable(context) && settingsManager.getBooleanOrNull(TasksFragment.Model.HINT_OPENTASKS_NOT_INSTALLED) != false)
            ShowMode.SHOW
        else
            ShowMode.DONT_SHOW
    }

    override fun create() = TasksIntroFragment()


    class TasksIntroFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
                inflater.inflate(R.layout.intro_tasks, container, false)

    }

}