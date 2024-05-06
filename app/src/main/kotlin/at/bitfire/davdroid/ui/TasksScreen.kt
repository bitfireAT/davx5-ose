package at.bitfire.davdroid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.BasicTopAppBar
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.RadioWithSwitch
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(onSupportNavigateUp: () -> Unit) {
    AppTheme {
        Scaffold(
            topBar = {
                BasicTopAppBar(
                    titleStringRes = R.string.intro_tasks_title,
                    onNavigateUp = onSupportNavigateUp
                )
            }
        ) { paddingValues ->
            Box(Modifier.padding(paddingValues)) {
                TasksCard()
            }
        }
    }
}

@Composable
fun TasksCard(
    model: TasksModel = viewModel()
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

    TasksCard(
        jtxSelected = jtxSelected,
        jtxInstalled = jtxInstalled,
        tasksOrgSelected = tasksOrgSelected,
        tasksOrgInstalled = tasksOrgInstalled,
        openTasksSelected = openTasksSelected,
        openTasksInstalled = openTasksInstalled,
        showAgain = showAgain,
        onSetShowAgain = model::setShowAgain,
        onProviderSelected = { provider ->
            if (model.currentProvider.value != provider)
                model.selectProvider(provider)
        },
        installApp = { packageName ->
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
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TasksCard_Preview() {
    AppTheme {
        TasksCard(
            jtxSelected = true,
            jtxInstalled = true,
            tasksOrgSelected = false,
            tasksOrgInstalled = false,
            openTasksSelected = false,
            openTasksInstalled = false,
            showAgain = true,
            onSetShowAgain = {},
            onProviderSelected = {},
            installApp = {}
        )
    }
}

@Composable
fun TasksCard(
    jtxSelected: Boolean,
    jtxInstalled: Boolean,
    tasksOrgSelected: Boolean,
    tasksOrgInstalled: Boolean,
    openTasksSelected: Boolean,
    openTasksInstalled: Boolean,
    showAgain: Boolean,
    onSetShowAgain: (Boolean) -> Unit,
    onProviderSelected: (TaskProvider.ProviderName) -> Unit,
    installApp: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        CardWithImage(
            image = painterResource(R.drawable.intro_tasks),
            imageAlignment = BiasAlignment(0f, .1f),
            title = stringResource(R.string.intro_tasks_title),
            message = stringResource(R.string.intro_tasks_text1),
            modifier = Modifier.padding(8.dp)
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
                    onCheckedChange = { onSetShowAgain(!it) }
                )
                Text(
                    text = stringResource(R.string.intro_tasks_dont_show),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetShowAgain(!showAgain) }
                )
            }
        }

        Text(
            text = stringResource(
                R.string.intro_leave_unchecked,
                stringResource(R.string.app_settings_reset_hints)
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
