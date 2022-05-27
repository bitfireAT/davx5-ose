/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.databinding.ObservableBoolean
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityTasksBinding
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class TasksFragment: Fragment() {

    private var _binding: ActivityTasksBinding? = null
    private val binding get() = _binding!!
    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityTasksBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        model.openTasksRequested.observe(viewLifecycleOwner) { shallBeInstalled ->
            if (shallBeInstalled && model.openTasksInstalled.value == false) {
                // uncheck switch for the moment (until the app is installed)
                model.openTasksRequested.value = false
                installApp(ProviderName.OpenTasks.packageName)
            }
        }
        model.openTasksSelected.observe(viewLifecycleOwner) { selected ->
            if (selected && model.currentProvider.value != ProviderName.OpenTasks)
                model.selectPreferredProvider(ProviderName.OpenTasks)
        }

        model.tasksOrgRequested.observe(viewLifecycleOwner) { shallBeInstalled ->
            if (shallBeInstalled && model.tasksOrgInstalled.value == false) {
                model.tasksOrgRequested.value = false
                installApp(ProviderName.TasksOrg.packageName)
            }
        }
        model.tasksOrgSelected.observe(viewLifecycleOwner) { selected ->
            if (selected && model.currentProvider.value != ProviderName.TasksOrg)
                model.selectPreferredProvider(ProviderName.TasksOrg)
        }


        model.jtxRequested.observe(viewLifecycleOwner) { shallBeInstalled ->
            if (shallBeInstalled && model.jtxInstalled.value == false) {
                model.jtxRequested.value = false
                installApp(ProviderName.JtxBoard.packageName)
            }
        }
        model.jtxSelected.observe(viewLifecycleOwner) { selected ->
            if (selected && model.currentProvider.value != ProviderName.JtxBoard)
                model.selectPreferredProvider(ProviderName.JtxBoard)
        }

        binding.infoLeaveUnchecked.text = getString(R.string.intro_leave_unchecked, getString(R.string.app_settings_reset_hints))

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun installApp(packageName: String) {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(requireActivity().packageManager) != null)
            startActivity(intent)
        else
            Snackbar.make(binding.frame, R.string.intro_tasks_no_app_store, Snackbar.LENGTH_LONG).show()
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
        val settings: SettingsManager
    ) : ViewModel(), SettingsManager.OnChangeListener {

        companion object {

            /**
             * Whether this fragment (which asks for OpenTasks installation) shall be shown.
             * If this setting is true or null/not set, the notice shall be shown. Only if this
             * setting is false, the notice shall not be shown.
             */
            const val HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled"

        }

        val currentProvider = MutableLiveData<ProviderName>()
        val openTasksInstalled = MutableLiveData<Boolean>()
        val openTasksRequested = MutableLiveData<Boolean>()
        val openTasksSelected = MutableLiveData<Boolean>()
        val tasksOrgInstalled = MutableLiveData<Boolean>()
        val tasksOrgRequested = MutableLiveData<Boolean>()
        val tasksOrgSelected = MutableLiveData<Boolean>()
        val jtxInstalled = MutableLiveData<Boolean>()
        val jtxRequested = MutableLiveData<Boolean>()
        val jtxSelected = MutableLiveData<Boolean>()
        val tasksWatcher = object: PackageChangedReceiver(context) {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkInstalled()
            }
        }

        val dontShow = object: ObservableBoolean() {
            override fun get() = settings.getBooleanOrNull(HINT_OPENTASKS_NOT_INSTALLED) == false
            override fun set(dontShowAgain: Boolean) {
                if (dontShowAgain)
                    settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
                else
                    settings.remove(HINT_OPENTASKS_NOT_INSTALLED)
                notifyChange()
            }
        }

        init {
            checkInstalled()
            settings.addOnChangeListener(this)
        }

        override fun onCleared() {
            settings.removeOnChangeListener(this)
            tasksWatcher.close()
        }

        @AnyThread
        fun checkInstalled() {
            val taskProvider = TaskUtils.currentProvider(context)
            currentProvider.postValue(taskProvider)

            val openTasks = isInstalled(ProviderName.OpenTasks.packageName)
            openTasksInstalled.postValue(openTasks)
            openTasksRequested.postValue(openTasks)
            openTasksSelected.postValue(taskProvider == ProviderName.OpenTasks)

            val tasksOrg = isInstalled(ProviderName.TasksOrg.packageName)
            tasksOrgInstalled.postValue(tasksOrg)
            tasksOrgRequested.postValue(tasksOrg)
            tasksOrgSelected.postValue(taskProvider == ProviderName.TasksOrg)

            val jtxBoard = isInstalled(ProviderName.JtxBoard.packageName)
            jtxInstalled.postValue(jtxBoard)
            jtxRequested.postValue(jtxBoard)
            jtxSelected.postValue(taskProvider == ProviderName.JtxBoard)
        }

        private fun isInstalled(packageName: String): Boolean =
                try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }

        fun selectPreferredProvider(provider: ProviderName) {
            TaskUtils.setPreferredProvider(context, provider)
        }


        override fun onSettingsChanged() {
            checkInstalled()
        }

    }

}