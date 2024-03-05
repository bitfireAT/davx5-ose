/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.util.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.themeadapter.material.MdcTheme

class PermissionsFragment: Fragment() {

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    PermissionsFragmentContent(model)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        val haveKeepPermissions = MutableLiveData<Boolean>()
        val needKeepPermissions = MutableLiveData<Boolean>()

        init {
            checkPermissions()
        }

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val keepPermissions = pm.isAutoRevokeWhitelisted
                haveKeepPermissions.value = keepPermissions
                needKeepPermissions.value = keepPermissions
            }
        }

    }

}

@Composable
fun PermissionsFragmentContent(model: PermissionsFragment.Model = viewModel()) {
    val context = LocalContext.current

    val keepPermissions by model.needKeepPermissions.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PermissionsCard(
            keepPermissions,
            onKeepPermissionsRequested = {
                Toast.makeText(context, R.string.permissions_autoreset_instruction, Toast.LENGTH_LONG).show()
                (context as? Activity)?.startActivity(
                    Intent(
                        Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                        Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    )
                )
            }
        )
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun PermissionSwitchRow(
    text: String,
    permissions: List<String>,
    summaryWhenGranted: String,
    summaryWhenNotGranted: String,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val state = rememberMultiplePermissionsState(permissions = permissions.toList())

    PermissionSwitchRow(
        text = text,
        fontWeight = fontWeight,
        summaryWhenGranted = summaryWhenGranted,
        summaryWhenNotGranted = summaryWhenNotGranted,
        allPermissionsGranted = state.allPermissionsGranted,
        onLaunchRequest = state::launchMultiplePermissionRequest
    )
}

@Preview(showBackground = true)
@Composable
fun PermissionSwitchRow_Preview() {
    PermissionSwitchRow(
        text = "Contacts",
        allPermissionsGranted = false,
        summaryWhenGranted = "Granted",
        summaryWhenNotGranted = "Not granted",
        onLaunchRequest = {}
    )
}

@Composable
fun PermissionSwitchRow(
    text: String,
    allPermissionsGranted: Boolean,
    summaryWhenGranted: String,
    summaryWhenNotGranted: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    onLaunchRequest: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = fontWeight
            )
            Text(
                text = if (allPermissionsGranted) summaryWhenGranted else summaryWhenNotGranted,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Switch(
            checked = allPermissionsGranted,
            enabled = !allPermissionsGranted,
            onCheckedChange = { checked ->
                if (checked) {
                    onLaunchRequest()
                }
            }
        )
    }
}

@Composable
fun PermissionsCard(keepPermissions: Boolean?, onKeepPermissionsRequested: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    val openTasksAvailable = pm.resolveContentProvider(ProviderName.OpenTasks.authority, 0) != null
    val tasksOrgAvailable = pm.resolveContentProvider(ProviderName.TasksOrg.authority, 0) != null
    val jtxAvailable = pm.resolveContentProvider(ProviderName.JtxBoard.authority, 0) != null

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
                onLaunchRequest = onKeepPermissionsRequested
            )
        }

        val allPermissions = mutableListOf<String>()
        allPermissions.addAll(CONTACT_PERMISSIONS)
        allPermissions.addAll(CALENDAR_PERMISSIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            allPermissions += Manifest.permission.POST_NOTIFICATIONS
        if (openTasksAvailable)
            allPermissions.addAll(TaskProvider.PERMISSIONS_OPENTASKS)
        if (tasksOrgAvailable)
            allPermissions.addAll(TaskProvider.PERMISSIONS_TASKS_ORG)
        if (jtxAvailable)
            allPermissions.addAll(TaskProvider.PERMISSIONS_JTX)
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_all_title),
            permissions = allPermissions,
            summaryWhenGranted = stringResource(R.string.permissions_all_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_all_status_off),
            fontWeight = FontWeight.Bold
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_notification_title),
                summaryWhenGranted = stringResource(R.string.permissions_notification_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_notification_status_off),
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
            )

        PermissionSwitchRow(
            text = stringResource(R.string.permissions_calendar_title),
            summaryWhenGranted = stringResource(R.string.permissions_calendar_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_calendar_status_off),
            permissions = CALENDAR_PERMISSIONS.toList()
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_contacts_title),
            summaryWhenGranted = stringResource(R.string.permissions_contacts_status_on),
            summaryWhenNotGranted = stringResource(R.string.permissions_contacts_status_off),
            permissions = CONTACT_PERMISSIONS.toList()
        )

        if (jtxAvailable)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_jtx_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_JTX.toList()
            )
        if (openTasksAvailable)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_opentasks_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_OPENTASKS.toList()
            )
        if (tasksOrgAvailable)
            PermissionSwitchRow(
                text = stringResource(R.string.permissions_tasksorg_title),
                summaryWhenGranted = stringResource(R.string.permissions_tasks_status_on),
                summaryWhenNotGranted = stringResource(R.string.permissions_tasks_status_off),
                permissions = TaskProvider.PERMISSIONS_TASKS_ORG.toList()
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
            Text(stringResource(R.string.permissions_app_settings))
        }
    }
}
