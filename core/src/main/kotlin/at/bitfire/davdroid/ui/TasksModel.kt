/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksModel @Inject constructor(
    @ApplicationContext val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val settings: SettingsManager,
    private val tasksAppManager: TasksAppManager
) : ViewModel() {

    companion object {

        /**
         * Whether this fragment (which asks for OpenTasks installation) shall be shown.
         * If this setting is true or null/not set, the notice shall be shown. Only if this
         * setting is false, the notice shall not be shown.
         */
        const val HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled"

    }

    val showAgain = settings.getBooleanFlow(HINT_OPENTASKS_NOT_INSTALLED, true)
    fun setShowAgain(showAgain: Boolean) {
        if (showAgain)
            settings.remove(HINT_OPENTASKS_NOT_INSTALLED)
        else
            settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
    }

    val currentProvider = tasksAppManager.currentProviderFlow()
    val jtxSelected = currentProvider.map { it == TaskProvider.ProviderName.JtxBoard }
    val tasksOrgSelected = currentProvider.map { it == TaskProvider.ProviderName.TasksOrg }
    val openTasksSelected = currentProvider.map { it == TaskProvider.ProviderName.OpenTasks }

    var jtxInstalled by mutableStateOf(false)
    var tasksOrgInstalled by mutableStateOf(false)
    var openTasksInstalled by mutableStateOf(false)

    init {
        viewModelScope.launch {
            packageChangedFlow(context).collect {
                jtxInstalled = isInstalled(TaskProvider.ProviderName.JtxBoard.packageName)
                tasksOrgInstalled = isInstalled(TaskProvider.ProviderName.TasksOrg.packageName)
                openTasksInstalled = isInstalled(TaskProvider.ProviderName.OpenTasks.packageName)
            }
        }
    }

    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun selectProvider(provider: TaskProvider.ProviderName) = viewModelScope.launch(defaultDispatcher) {
        tasksAppManager.selectProvider(provider)
    }

}
