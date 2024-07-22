package at.bitfire.davdroid.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsModel @Inject constructor(
    @ApplicationContext val context: Context,
): ViewModel() {

    var needKeepPermissions by mutableStateOf(false)
        private set
    var openTasksAvailable by mutableStateOf(false)
        private set
    var tasksOrgAvailable by mutableStateOf(false)
        private set
    var jtxAvailable by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // check permissions when a package (e.g. tasks app) is (un)installed
            packageChangedFlow(context).collect {
                checkPermissions()
            }
        }
    }

    fun checkPermissions() {
        val pm = context.packageManager

        // auto-reset permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            needKeepPermissions = pm.isAutoRevokeWhitelisted
        }

        openTasksAvailable = pm.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
        tasksOrgAvailable = pm.resolveContentProvider(TaskProvider.ProviderName.TasksOrg.authority, 0) != null
        jtxAvailable = pm.resolveContentProvider(TaskProvider.ProviderName.JtxBoard.authority, 0) != null
    }

}
