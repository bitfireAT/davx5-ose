/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.widget.PermissionSwitchRow
import at.bitfire.davdroid.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class WifiPermissionsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }
    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
                                    Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                                }
                            },
                            title = { Text(stringResource(R.string.wifi_permissions_label)) },
                            actions = {
                                val uriHandler = LocalUriHandler.current
                                IconButton(onClick = {
                                    uriHandler.openUri(Constants.HOMEPAGE_URL.buildUpon()
                                        .appendPath(Constants.HOMEPAGE_PATH_FAQ)
                                        .appendPath(Constants.HOMEPAGE_PATH_FAQ_LOCATION_PERMISSION)
                                        .withStatParams("WifiPermissionsActivity")
                                        .build().toString())
                                }) {
                                    Icon(Icons.Default.Help, stringResource(R.string.help))
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        Content(
                            backgroundPermissionOptionLabel =
                                if (Build.VERSION.SDK_INT >= 30)
                                    packageManager.backgroundPermissionOptionLabel.toString()
                                else
                                    stringResource(R.string.wifi_permissions_background_location_permission_label),
                            locationServiceEnabled = model.isLocationEnabled.observeAsState(false).value
                        )
                    }
                }
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(SettingsActivity.EXTRA_ACCOUNT, account)
    }


    @Composable
    fun Content(
        backgroundPermissionOptionLabel: String,
        locationServiceEnabled: Boolean
    ) {
        Column(Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.wifi_permissions_intro),
                style = MaterialTheme.typography.body1
            )

            // Android 8.1+: location permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                LocationPermission(
                    modifier = Modifier.padding(top = 16.dp)
                )

            // Android 10+: background location permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                BackgroundLocationPermission(
                    backgroundPermissionOptionLabel = backgroundPermissionOptionLabel,
                    modifier = Modifier.padding(top = 16.dp)
                )

            // Android 9+: location service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                LocationService(
                    locationServiceEnabled = locationServiceEnabled,
                    modifier = Modifier.padding(top = 16.dp)
                )

            // If permissions have actively been denied
            Text(
                stringResource(R.string.permissions_app_settings_hint),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 16.dp)
            )
            val context = LocalContext.current
            OutlinedButton(
                onClick = { PermissionUtils.showAppSettings(context) }
            ) {
                Text(stringResource(R.string.permissions_app_settings).uppercase())
            }

            Divider(Modifier.padding(vertical = 16.dp))

            // Disclaimer
            Row {
                Text(
                    stringResource(R.string.wifi_permissions_background_location_disclaimer, stringResource(R.string.app_name)),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.CloudOff, null, modifier = Modifier.padding(8.dp))
            }
        }
    }

    @Composable
    fun LocationPermission(
        modifier: Modifier = Modifier
    ) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Manifest.permission.ACCESS_FINE_LOCATION    // since Android 10, fine location is required
        else
            Manifest.permission.ACCESS_COARSE_LOCATION  // Android 8+: coarse location is enough

        PermissionSwitchRow(
            text = stringResource(R.string.wifi_permissions_location_permission),
            permissions = listOf(permission),
            summaryWhenGranted = stringResource(R.string.wifi_permissions_location_permission_on),
            summaryWhenNotGranted = stringResource(R.string.wifi_permissions_location_permission_off),
            modifier = modifier
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Composable
    fun BackgroundLocationPermission(
        backgroundPermissionOptionLabel: String,
        modifier: Modifier = Modifier
    ) {
        PermissionSwitchRow(
            text = stringResource(R.string.wifi_permissions_background_location_permission),
            permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            summaryWhenGranted = stringResource(R.string.wifi_permissions_background_location_permission_on, backgroundPermissionOptionLabel),
            summaryWhenNotGranted = stringResource(R.string.wifi_permissions_background_location_permission_off, backgroundPermissionOptionLabel),
            modifier = modifier
        )
    }

    @Composable
    fun LocationService(
        locationServiceEnabled: Boolean,
        modifier: Modifier = Modifier
    ) {
        Row(modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.wifi_permissions_location_enabled),
                    style = MaterialTheme.typography.body1
                )
                Text(
                    stringResource(
                        if (locationServiceEnabled)
                            R.string.wifi_permissions_location_enabled_on
                        else
                            R.string.wifi_permissions_location_enabled_off
                    ),
                    style = MaterialTheme.typography.body2
                )
            }
            Switch(
                checked = locationServiceEnabled,
                onCheckedChange = {
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    if (intent.resolveActivity(packageManager) != null)
                        startActivity(intent)
                }
            )
        }
    }

    @Composable
    @Preview
    fun Content_Preview() {
        Content(
            backgroundPermissionOptionLabel = stringResource(R.string.wifi_permissions_background_location_permission_label),
            locationServiceEnabled = true
        )
    }


    @HiltViewModel
    class Model @Inject constructor(
        context: Application
    ): ViewModel() {

        val isLocationEnabled: LiveData<Boolean> = object: LiveData<Boolean>() {
            val locationChangedReceiver = object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    update()
                }
            }

            override fun onActive() {
                context.registerReceiver(locationChangedReceiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION))
                update()
            }

            override fun onInactive() {
                context.unregisterReceiver(locationChangedReceiver)
            }

            @MainThread
            fun update() {
                context.getSystemService<LocationManager>()?.let { locationManager ->
                    val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
                    value = locationEnabled
                }
            }
        }

    }

}