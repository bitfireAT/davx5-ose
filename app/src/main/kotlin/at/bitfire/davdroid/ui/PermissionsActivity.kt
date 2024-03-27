/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.composable.BasicTopAppBar
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.PermissionSwitchRow
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import java.util.logging.Level

class PermissionsActivity: AppCompatActivity() {

    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        BasicTopAppBar(
                            titleStringRes = R.string.app_settings_security_app_permissions
                        )
                    }
                ) { paddingValues ->
                    PermissionsContent(modifier = Modifier.padding(paddingValues), model)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        val needKeepPermissions = MutableLiveData<Boolean>()

        val openTasksAvailable = MutableLiveData<Boolean>()
        val tasksOrgAvailable = MutableLiveData<Boolean>()
        val jtxAvailable = MutableLiveData<Boolean>()

        private val tasksWatcher = object: PackageChangedReceiver(app) {
            override fun onPackageChanged() {
                checkPermissions()
            }
        }

        init {
            tasksWatcher.register()
            checkPermissions()
        }

        override fun onCleared() {
            tasksWatcher.close()
        }

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                needKeepPermissions.value = pm.isAutoRevokeWhitelisted
            }

            openTasksAvailable.value = pm.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
            tasksOrgAvailable.value = pm.resolveContentProvider(TaskProvider.ProviderName.TasksOrg.authority, 0) != null
            jtxAvailable.value = pm.resolveContentProvider(TaskProvider.ProviderName.JtxBoard.authority, 0) != null
        }
    }

}


@Composable
fun PermissionsContent(
    modifier: Modifier = Modifier,
    model: PermissionsActivity.Model = viewModel()
) {
    val context = LocalContext.current

    val keepPermissions by model.needKeepPermissions.observeAsState()
    val openTasksAvailable by model.openTasksAvailable.observeAsState()
    val tasksOrgAvailable by model.tasksOrgAvailable.observeAsState()
    val jtxAvailable by model.jtxAvailable.observeAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PermissionsCard(
            keepPermissions,
            onKeepPermissionsRequested = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(
                        Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                        Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    )
                    try {
                        context.startActivity(intent)
                        Toast.makeText(context, R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't start Keep Permissions activity", e)
                    }
                }
            },
            openTasksAvailable,
            tasksOrgAvailable,
            jtxAvailable
        )
    }
}

@Composable
fun PermissionsCard(
    keepPermissions: Boolean?,
    onKeepPermissionsRequested: () -> Unit,
    openTasksAvailable: Boolean?,
    tasksOrgAvailable: Boolean?,
    jtxAvailable: Boolean?
) {
    val context = LocalContext.current

    CardWithImage(
        title = stringResource(R.string.permissions_title),
        message = stringResource(
            R.string.permissions_text,
            stringResource(R.string.app_name)
        ),
        image = painterResource(R.drawable.intro_permissions),
        modifier = Modifier.padding(8.dp)
    ) {
        if (keepPermissions != null) {
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_autoreset_title),
                summaryWhenGranted = stringResource(R.string.permissions_autoreset_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_autoreset_status_off),
                allPermissionsGranted = keepPermissions,
                onLaunchRequest = onKeepPermissionsRequested,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        val allPermissions = mutableListOf<String>()
        allPermissions.addAll(PermissionUtils.CONTACT_PERMISSIONS)
        allPermissions.addAll(PermissionUtils.CALENDAR_PERMISSIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            allPermissions += Manifest.permission.POST_NOTIFICATIONS
        if (openTasksAvailable == true)
            allPermissions.addAll(TaskProvider.PERMISSIONS_OPENTASKS)
        if (tasksOrgAvailable == true)
            allPermissions.addAll(TaskProvider.PERMISSIONS_TASKS_ORG)
        if (jtxAvailable == true)
            allPermissions.addAll(TaskProvider.PERMISSIONS_JTX)
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_all_title),
            permissions = allPermissions,
            summaryWhenGranted = stringResource(R.string.permissions_all_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_all_status_off),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_notification_title),
                summaryWhenGranted = stringResource(R.string.permissions_notification_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_notification_status_off),
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                modifier = Modifier.padding(vertical = 4.dp)
            )

        PermissionSwitchRow(
            text = stringResource(R.string.permissions_calendar_title),
            summaryWhenGranted = stringResource(R.string.permissions_calendar_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_calendar_status_off),
            permissions = PermissionUtils.CALENDAR_PERMISSIONS.toList(),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_contacts_title),
            summaryWhenGranted = stringResource(R.string.permissions_contacts_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_contacts_status_off),
            permissions = PermissionUtils.CONTACT_PERMISSIONS.toList(),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (jtxAvailable == true)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_jtx_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_JTX.toList(),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        if (openTasksAvailable == true)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_opentasks_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_OPENTASKS.toList(),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        if (tasksOrgAvailable == true)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_tasksorg_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_TASKS_ORG.toList(),
                modifier = Modifier.padding(vertical = 4.dp)
            )

        Text(
            text = stringResource(R.string.permissions_app_settings_hint),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(top = 24.dp)
        )

        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            onClick = { PermissionUtils.showAppSettings(context) }
        ) {
            Text(stringResource(R.string.permissions_app_settings).uppercase())
        }
    }
}