/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.davdroid.ui.widget.RadioWithSwitch
import at.bitfire.ical4android.TaskProvider.ProviderName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksModel @Inject constructor(
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

    private val tasksWatcher = object: PackageChangedReceiver(application) {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkInstalled()
        }
    }

    val dontShow = MutableLiveData(
    settings.getBooleanOrNull(HINT_OPENTASKS_NOT_INSTALLED) == false
    )

    private val dontShowObserver = Observer<Boolean> { value ->
        if (value)
            settings.putBoolean(HINT_OPENTASKS_NOT_INSTALLED, false)
        else
            settings.remove(HINT_OPENTASKS_NOT_INSTALLED)
    }

    init {
        checkInstalled()
        settings.addOnChangeListener(this)
        dontShow.observeForever(dontShowObserver)
    }

    override fun onCleared() {
        settings.removeOnChangeListener(this)
        tasksWatcher.close()
        dontShow.removeObserver(dontShowObserver)
    }

    @AnyThread
    fun checkInstalled() {
        val taskProvider = TaskUtils.currentProvider(getApplication())
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
            getApplication<Application>().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    fun selectPreferredProvider(provider: ProviderName) {
        // Changes preferred task app setting, so onSettingsChanged() will be called
        TaskUtils.setPreferredProvider(getApplication(), provider)
    }


    override fun onSettingsChanged() {
        checkInstalled()
    }

}

@OptIn(ExperimentalTextApi::class)
@Composable
fun TasksCard(
    modifier: Modifier = Modifier,
    model: TasksModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val jtxInstalled by model.jtxInstalled.observeAsState(initial = false)
    val jtxSelected by model.jtxSelected.observeAsState(initial = false)
    val jtxRequested by model.jtxRequested.observeAsState(initial = false)

    val tasksOrgInstalled by model.tasksOrgInstalled.observeAsState(initial = false)
    val tasksOrgSelected by model.tasksOrgSelected.observeAsState(initial = false)
    val tasksOrgRequested by model.tasksOrgRequested.observeAsState(initial = false)

    val openTasksInstalled by model.openTasksInstalled.observeAsState(initial = false)
    val openTasksSelected by model.openTasksSelected.observeAsState(initial = false)
    val openTasksRequested by model.openTasksRequested.observeAsState(initial = false)

    val dontShow by model.dontShow.observeAsState(initial = false)

    fun installApp(packageName: String) {
        val uri = Uri.parse("market://details?id=$packageName&referrer=" +
            Uri.encode("utm_source=" + BuildConfig.APPLICATION_ID))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null)
            context.startActivity(intent)
        else
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.intro_tasks_no_app_store),
                    duration = SnackbarDuration.Long
                )
            }
    }

    fun onProviderSelected(provider: ProviderName) {
        if (model.currentProvider.value != provider)
            model.selectPreferredProvider(provider)
    }

    Scaffold(
        modifier = modifier,
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
                imageAlignment = BiasAlignment(0f, .1f),
                title = stringResource(R.string.intro_tasks_title),
                message = stringResource(R.string.intro_tasks_text1),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                RadioWithSwitch(
                    title = stringResource(R.string.intro_tasks_jtx),
                    summary = {
                        Text(stringResource(R.string.intro_tasks_jtx_info))
                    },
                    isSelected = jtxSelected,
                    isToggled = jtxRequested,
                    enabled = jtxInstalled,
                    onSelected = { onProviderSelected(ProviderName.JtxBoard) },
                    onToggled = { toggled ->
                        if (toggled) installApp(ProviderName.JtxBoard.packageName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                RadioWithSwitch(
                    title = stringResource(R.string.intro_tasks_tasks_org),
                    summary = {
                        val summary = HtmlCompat.fromHtml(
                            stringResource(R.string.intro_tasks_tasks_org_info),
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        ).toAnnotatedString()

                        ClickableText(
                            text = summary,
                            onClick = { index ->
                                // Get the tapped position, and check if there's any link
                                summary.getUrlAnnotations(index, index).firstOrNull()?.item?.url?.let { url ->
                                    UiUtils.launchUri(context, Uri.parse(url))
                                }
                            }
                        )
                    },
                    isSelected = tasksOrgSelected,
                    isToggled = tasksOrgRequested,
                    enabled = tasksOrgInstalled,
                    onSelected = { onProviderSelected(ProviderName.TasksOrg) },
                    onToggled = { toggled ->
                        if (toggled) installApp(ProviderName.TasksOrg.packageName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                RadioWithSwitch(
                    title = stringResource(R.string.intro_tasks_opentasks),
                    summary = {
                        Text(stringResource(R.string.intro_tasks_opentasks_info))
                    },
                    isSelected = openTasksSelected,
                    isToggled = openTasksRequested,
                    enabled = openTasksInstalled,
                    onSelected = { onProviderSelected(ProviderName.OpenTasks) },
                    onToggled = { toggled ->
                        if (toggled) installApp(ProviderName.OpenTasks.packageName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Checkbox(
                        checked = dontShow,
                        onCheckedChange = { model.dontShow.value = it }
                    )
                    Text(
                        text = stringResource(R.string.intro_tasks_dont_show),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.dontShow.value = !dontShow }
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.intro_leave_unchecked,
                    stringResource(R.string.app_settings_reset_hints)
                ),
                style = MaterialTheme.typography.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}