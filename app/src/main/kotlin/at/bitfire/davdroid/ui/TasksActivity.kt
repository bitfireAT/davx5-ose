/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.BasicTopAppBar
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.RadioWithSwitch
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.davdroid.util.packageChangedFlow
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TasksActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        BasicTopAppBar(
                            titleStringRes = R.string.intro_tasks_title,
                            onNavigateUp = ::onSupportNavigateUp
                        )
                    }
                ) { paddingValues ->
                    Box(Modifier.padding(paddingValues)) {
                        TasksCard()
                    }
                }
            }
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        val settings: SettingsManager
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

        val currentProvider = TaskUtils.currentProviderFlow(context, viewModelScope)
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
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

        fun selectProvider(provider: TaskProvider.ProviderName) = viewModelScope.launch(Dispatchers.Default) {
            TaskUtils.selectProvider(context, provider)
        }

    }

}


@Composable
fun TasksCard(
    model: TasksActivity.Model = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val jtxInstalled = model.jtxInstalled
    val jtxSelected by model.jtxSelected.collectAsStateWithLifecycle(false)

    val tasksOrgInstalled = model.tasksOrgInstalled
    val tasksOrgSelected by model.tasksOrgSelected.collectAsStateWithLifecycle(false)

    val openTasksInstalled = model.openTasksInstalled
    val openTasksSelected by model.openTasksSelected.collectAsStateWithLifecycle(false)

    val showAgain by model.showAgain.collectAsStateWithLifecycle(true)

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

    fun onProviderSelected(provider: TaskProvider.ProviderName) {
        if (model.currentProvider.value != provider)
            model.selectProvider(provider)
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(8.dp)
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
                isToggled = jtxInstalled,
                enabled = jtxInstalled,
                onSelected = { onProviderSelected(TaskProvider.ProviderName.JtxBoard) },
                onToggled = { toggled ->
                    if (toggled) installApp(TaskProvider.ProviderName.JtxBoard.packageName)
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
                    ClickableTextWithLink(summary)
                },
                isSelected = tasksOrgSelected,
                isToggled = tasksOrgInstalled,
                enabled = tasksOrgInstalled,
                onSelected = { onProviderSelected(TaskProvider.ProviderName.TasksOrg) },
                onToggled = { toggled ->
                    if (toggled) installApp(TaskProvider.ProviderName.TasksOrg.packageName)
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
                isToggled = openTasksInstalled,
                enabled = openTasksInstalled,
                onSelected = { onProviderSelected(TaskProvider.ProviderName.OpenTasks) },
                onToggled = { toggled ->
                    if (toggled) installApp(TaskProvider.ProviderName.OpenTasks.packageName)
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
                    checked = !showAgain,
                    onCheckedChange = { model.setShowAgain(!it) }
                )
                Text(
                    text = stringResource(R.string.intro_tasks_dont_show),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { model.setShowAgain(!showAgain) }
                )
            }
        }

        Text(
            text = stringResource(
                R.string.intro_leave_unchecked,
                stringResource(R.string.app_settings_reset_hints)
            ),
            style = MaterialTheme.typography.body2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}