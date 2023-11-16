/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.davdroid.ui.widget.RadioWithSwitch
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TasksFragment: Fragment() {

    val model by viewModels<Model>()

    private lateinit var uiCoroutineScope: CoroutineScope
    private lateinit var snackbarHostState: SnackbarHostState


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snackbarHostState = remember {
                    SnackbarHostState().also { this@TasksFragment.snackbarHostState = it }
                }

                TasksCard(model, snackbarHostState)
            }
        }
    }

    private fun installApp(packageName: String) {
        val uri = Uri.parse("market://details?id=$packageName&referrer=" +
                Uri.encode("utm_source=" + BuildConfig.APPLICATION_ID))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(requireActivity().packageManager) != null)
            startActivity(intent)
        else uiCoroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = getString(R.string.intro_tasks_no_app_store),
                duration = SnackbarDuration.Long
            )
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val settings: SettingsManager
    ) : AndroidViewModel(application), SettingsManager.OnChangeListener {

        companion object {

            /**
             * Whether this fragment (which asks for OpenTasks installation) shall be shown.
             * If this setting is true or null/not set, the notice shall be shown. Only if this
             * setting is false, the notice shall not be shown.
             */
            const val HINT_OPENTASKS_NOT_INSTALLED = "hint_OpenTasksNotInstalled"

        }

        val context: Context get() = getApplication()

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

        val dontShow = object : MutableState<Boolean> {
            private fun get() = settings.getBooleanOrNull(HINT_OPENTASKS_NOT_INSTALLED) == false

            private fun update(value: Boolean) {
                if (value)
                    settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
                else
                    settings.remove(HINT_OPENTASKS_NOT_INSTALLED)
            }

            override var value: Boolean
                get() = get()
                set(value) { update(value) }

            override fun component1(): Boolean = get()

            override fun component2(): (Boolean) -> Unit = ::update
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

@Composable
fun TasksCard(
    model: TasksFragment.Model = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val jtxInstalled by model.jtxInstalled.observeAsState(initial = false)
    val jtxSelected by model.jtxSelected.observeAsState(initial = false)
    val jtxRequested by model.jtxRequested.observeAsState(initial = false)

    val dontShow by model.dontShow

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            CardWithImage(
                image = painterResource(R.drawable.intro_tasks),
                title = stringResource(R.string.intro_tasks_title),
                message = stringResource(R.string.intro_tasks_text1),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                RadioWithSwitch(
                    title = stringResource(R.string.intro_tasks_jtx),
                    summary = stringResource(R.string.intro_tasks_jtx_info),
                    isSelected = jtxSelected,
                    isToggled = jtxRequested,
                    enabled = jtxInstalled,
                    onSelected = { model.jtxSelected.value = true },
                    onToggled = model.jtxRequested::setValue
                )
            }

            Row {
                Checkbox(
                    checked = dontShow,
                    onCheckedChange = {  }
                )
                Text(
                    text = stringResource(R.string.intro_tasks_dont_show)
                )
            }
        }
    }
}

@Preview
@Composable
fun TasksCard_Preview() {
    TasksCard()
}
