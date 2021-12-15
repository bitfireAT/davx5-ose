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
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityWifiPermissionsBinding
import at.bitfire.davdroid.log.Logger

class WifiPermissionsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }
    private val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionUtils.WIFI_SSID_PERMISSIONS.all { perm -> PermissionUtils.declaresPermission(packageManager, perm) })
            throw IllegalArgumentException("WiFi SSID restriction requires location permissions")

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val binding = ActivityWifiPermissionsBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.model = model
        setContentView(binding.root)

        model.needLocation.observe(this) { needPermission ->
            if (needPermission && model.haveLocation.value == false)
                ActivityCompat.requestPermissions(this, arrayOf(model.PERMISSION_LOCATION), 0)
        }

        model.haveBackgroundLocation.observe(this) { status ->
            val label = if (Build.VERSION.SDK_INT >= 30)
                    packageManager.backgroundPermissionOptionLabel
                else
                    getString(R.string.wifi_permissions_background_location_permission_label)
            binding.backgroundLocationStatus.text = HtmlCompat.fromHtml(getString(
                    if (status) R.string.wifi_permissions_background_location_permission_on else R.string.wifi_permissions_background_location_permission_off,
                    label
            ), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
        model.needBackgroundLocation.observe(this) { needPermission ->
            if (needPermission && model.haveBackgroundLocation.value == false)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
        }
        binding.backgroundLocationDisclaimer.text = getString(R.string.wifi_permissions_background_location_disclaimer, getString(R.string.app_name))

        binding.settingsBtn.setOnClickListener {
            PermissionUtils.showAppSettings(this)
        }

        model.needLocationEnabled.observe(this) { needLocation ->
            if (needLocation != null && needLocation != model.isLocationEnabled.value) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                if (intent.resolveActivity(packageManager) != null)
                    startActivity(intent)
                else
                    Logger.log.warning("Couldn't resolve Location settings Intent")
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(SettingsActivity.EXTRA_ACCOUNT, account)
    }

    override fun onResume() {
        super.onResume()
        model.checkPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        model.checkPermissions()
    }


    class Model(app: Application): AndroidViewModel(app) {

        val PERMISSION_LOCATION =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    Manifest.permission.ACCESS_FINE_LOCATION    // since Android 10, fine location is required
                else
                    Manifest.permission.ACCESS_COARSE_LOCATION  // Android 8+: coarse location is enough

        val haveLocation = MutableLiveData<Boolean>()
        val needLocation = MutableLiveData<Boolean>()

        val haveBackgroundLocation = MutableLiveData<Boolean>()
        val needBackgroundLocation = MutableLiveData<Boolean>()

        val isLocationEnabled = MutableLiveData<Boolean>()
        val needLocationEnabled = MutableLiveData<Boolean>()
        val locationModeWatcher = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                checkPermissions()
            }
        }

        init {
            app.registerReceiver(locationModeWatcher, IntentFilter(LocationManager.MODE_CHANGED_ACTION))
            checkPermissions()
        }

        override fun onCleared() {
            getApplication<Application>().unregisterReceiver(locationModeWatcher)
        }

        fun checkPermissions() {
            // Android 8.1+: location permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val location = ContextCompat.checkSelfPermission(getApplication(), PERMISSION_LOCATION) == PackageManager.PERMISSION_GRANTED
                haveLocation.value = location
                needLocation.value = location
            }

            // Android 9+: location service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getSystemService(getApplication(), LocationManager::class.java)?.let { locationManager ->
                    val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
                    isLocationEnabled.value = locationEnabled
                    needLocationEnabled.value = locationEnabled
                }
            }

            // Android 10+: background location permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backgroundLocation = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                haveBackgroundLocation.value = backgroundLocation
                needBackgroundLocation.value = backgroundLocation
            }
        }

    }

}