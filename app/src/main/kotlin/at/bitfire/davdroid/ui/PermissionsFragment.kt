/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.PackageChangedReceiver
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.PermissionUtils.CALENDAR_PERMISSIONS
import at.bitfire.davdroid.util.PermissionUtils.CONTACT_PERMISSIONS
import at.bitfire.davdroid.util.PermissionUtils.havePermissions
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.themeadapter.material.MdcTheme
import net.fortuna.ical4j.model.Content

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

        val haveAutoResetPermission = MutableLiveData<Boolean>()
        val needAutoResetPermission = MutableLiveData<Boolean>()

        init {
            checkPermissions()
        }

        @MainThread
        fun checkPermissions() {
            val pm = getApplication<Application>().packageManager

            // auto-reset permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val keepPermissions = pm.isAutoRevokeWhitelisted
                haveAutoResetPermission.value = keepPermissions
                needAutoResetPermission.value = keepPermissions
            }
        }

    }

}

@Composable
fun PermissionsFragmentContent(model: PermissionsFragment.Model = viewModel()) {
    val context = LocalContext.current

    val keepPermissions by model.needAutoResetPermission.observeAsState()

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
    permissions: List<String>?,
    fontWeight: FontWeight = FontWeight.Normal
) {
    if (permissions != null) {
        val state = rememberMultiplePermissionsState(permissions = permissions.toList())

        PermissionSwitchRow(
            text = text,
            fontWeight = fontWeight,
            allPermissionsGranted = state.allPermissionsGranted,
            onLaunchRequest = state::launchMultiplePermissionRequest
        )
    }
}

@Composable
fun PermissionSwitchRow(
    text: String,
    allPermissionsGranted: Boolean,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    onLaunchRequest: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontWeight = fontWeight
        )
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
                allPermissionsGranted = keepPermissions == true,
                onLaunchRequest = onKeepPermissionsRequested
            )
        }

        PermissionSwitchRow(
            text = stringResource(R.string.permissions_all_title),
            permissions = listOfNotNull(
                *CONTACT_PERMISSIONS,
                *CALENDAR_PERMISSIONS,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS
                else null,
                *(TaskProvider.PERMISSIONS_OPENTASKS.takeIf { openTasksAvailable } ?: emptyArray()),
                *(TaskProvider.PERMISSIONS_TASKS_ORG.takeIf { tasksOrgAvailable } ?: emptyArray()),
                *(TaskProvider.PERMISSIONS_JTX.takeIf { jtxAvailable } ?: emptyArray())
            ),
            fontWeight = FontWeight.Bold
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_notification_title),
            permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            else null
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_calendar_title),
            permissions = CALENDAR_PERMISSIONS.toList()
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_contacts_title),
            permissions = CONTACT_PERMISSIONS.toList()
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_jtx_title),
            permissions = TaskProvider.PERMISSIONS_JTX.toList().takeIf { jtxAvailable }
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_opentasks_title),
            permissions = TaskProvider.PERMISSIONS_OPENTASKS.toList().takeIf { openTasksAvailable }
        )
        PermissionSwitchRow(
            text = stringResource(R.string.permissions_tasksorg_title),
            permissions = TaskProvider.PERMISSIONS_TASKS_ORG.toList().takeIf { tasksOrgAvailable }
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
