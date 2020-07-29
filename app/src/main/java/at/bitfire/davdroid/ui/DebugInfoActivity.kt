/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityDebugInfoBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.exception.ExceptionUtils
import org.dmfs.tasks.contract.TaskContract
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.logging.Level

class DebugInfoActivity: AppCompatActivity() {

    companion object {
        const val KEY_THROWABLE = "throwable"
        const val KEY_LOGS = "logs"
        const val KEY_ACCOUNT = "account"
        const val KEY_AUTHORITY = "authority"
        const val KEY_LOCAL_RESOURCE = "localResource"
        const val KEY_REMOTE_RESOURCE = "remoteResource"
    }

    private val model by viewModels<ReportModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.initialize(intent.extras)

        val binding = DataBindingUtil.setContentView<ActivityDebugInfoBinding>(this, R.layout.activity_debug_info)
        binding.model = model
        binding.lifecycleOwner = this
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_debug_info, menu)
        return true
    }


    fun onShare(item: MenuItem) {
        model.report.value?.let { report ->
            val builder = ShareCompat.IntentBuilder.from(this)
                    .setSubject("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info")
                    .setText(getString(R.string.debug_info_logs_attached))
                    .setType("text/plain")

            try {
                val debugInfoDir = File(filesDir, "debug")
                if (!(debugInfoDir.exists() && debugInfoDir.isDirectory) && !debugInfoDir.mkdir())
                    throw IOException("Couldn't create debug directory")

                val reportFile = File(debugInfoDir, "davx5-info.txt")
                Logger.log.fine("Writing debug info to ${reportFile.absolutePath}")
                val writer = FileWriter(reportFile)
                writer.write(report)
                writer.close()

                builder.setStream(FileProvider.getUriForFile(this, getString(R.string.authority_debug_provider), reportFile))
                builder.intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            } catch(e: IOException) {
                // creating an attachment failed, so send it inline
                val text = "Couldn't create debug info file: " + Log.getStackTraceString(e) + "\n\n$report"
                builder.setText(text)
            }

            builder.startChooser()
        }
    }


    class ReportModel(application: Application): AndroidViewModel(application) {

        private var initialized = false
        val report = MutableLiveData<String>()

        fun initialize(extras: Bundle?) {
            if (initialized)
                return

            Logger.log.info("Generating debug info report")
            initialized = true

            viewModelScope.launch(Dispatchers.Default) {
                val context = getApplication<Application>()
                val text = StringBuilder("--- BEGIN DEBUG INFO ---\n")

                // begin with most specific information
                extras?.getParcelable<Account>(KEY_ACCOUNT)?.let {
                    text.append("SYNCHRONIZATION INFO\nAccount name: ${it.name}\n")
                }
                extras?.getString(KEY_AUTHORITY)?.let {
                    text.append("Authority: $it\n")
                }

                // exception details
                val throwable = extras?.getSerializable(KEY_THROWABLE) as Throwable?
                if (throwable is HttpException) {
                    throwable.request?.let { request ->
                        text.append("\nHTTP REQUEST:\n$request\n")
                        throwable.requestBody?.let { text.append(it) }
                        text.append("\n\n")
                    }
                    throwable.response?.let { response ->
                        text.append("HTTP RESPONSE:\n$response\n")
                        throwable.responseBody?.let { text.append(it) }
                        text.append("\n\n")
                    }
                }

                extras?.getString(KEY_LOCAL_RESOURCE)?.let {
                    text.append("\nLOCAL RESOURCE:\n$it\n")
                }
                extras?.getString(KEY_REMOTE_RESOURCE)?.let {
                    text.append("\nREMOTE RESOURCE:\n$it\n")
                }

                throwable?.let {
                    // Log.getStackTraceString(e) returns "" in case of UnknownHostException
                    text.append("\nEXCEPTION:\n${ExceptionUtils.getStackTrace(throwable)}")
                }

                // logs (for instance, from failed resource detection)
                extras?.getString(KEY_LOGS)?.let {
                    text.append("\nLOGS:\n$it\n")
                }

                // software information
                try {
                    text.append("\nSOFTWARE INFORMATION\n")
                    val pm = context.packageManager
                    val appIDs = mutableSetOf(      // we always want info about these packages
                            BuildConfig.APPLICATION_ID,                     // DAVx5
                            "org.dmfs.tasks"                                // OpenTasks
                    )
                    // add info about contact, calendar, task provider
                    for (authority in arrayOf(ContactsContract.AUTHORITY, CalendarContract.AUTHORITY, TaskProvider.ProviderName.OpenTasks.authority))
                        pm.resolveContentProvider(authority, 0)?.let { appIDs += it.packageName }
                    // add info about available contact, calendar, task apps
                    for (uri in arrayOf(ContactsContract.Contacts.CONTENT_URI, CalendarContract.Events.CONTENT_URI, TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority))) {
                        val viewIntent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(uri, 1))
                        for (info in pm.queryIntentActivities(viewIntent, 0))
                            appIDs += info.activityInfo.packageName
                    }

                    for (appID in appIDs)
                        try {
                            val info = pm.getPackageInfo(appID, 0)
                            val appInfo = info.applicationInfo
                            text    .append("* ").append(appInfo.loadLabel(pm))
                                    .append(" ").append(info.versionName)
                                    .append(" (").append(appID)
                                    .append(" ").append(PackageInfoCompat.getLongVersionCode(info)).append(")")
                            pm.getInstallerPackageName(appID)?.let { installer ->
                                text    .append(" from ")
                                        .append(pm.getApplicationInfo(installer, 0).loadLabel(pm))
                                        .append(" (").append(installer).append(")")
                            }
                            if (!appInfo.enabled)
                                text.append(" DISABLED!")
                            if (appInfo.flags.and(ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0)
                                text.append(" ON EXTERNAL STORAGE!")
                            text.append("\n")
                        } catch(e: PackageManager.NameNotFoundException) {
                        }
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't get software information", e)
                }

                // connectivity
                context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                    text.append("\nCONNECTIVITY (at the moment)\n")
                    connectivityManager.allNetworks.forEach { network ->
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        text.append("- $capabilities\n")
                    }
                    if (Build.VERSION.SDK_INT >= 23)
                        connectivityManager.defaultProxy?.let { proxy ->
                            text.append("System default proxy: ${proxy.host}:${proxy.port}\n")
                        }
                    if (Build.VERSION.SDK_INT >= 24)
                        text.append("Data saver: ").append(when (connectivityManager.restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                            else -> connectivityManager.restrictBackgroundStatus
                        }).append("\n")
                    text.append("\n")
                }

                text.append("CONFIGURATION\n")
                // power saving
                if (Build.VERSION.SDK_INT >= 23)
                    context.getSystemService<PowerManager>()?.let { powerManager ->
                        text.append("Power saving disabled: ")
                                .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                                .append("\n")
                    }
                // notifications
                val nm = NotificationManagerCompat.from(context)
                text.append("Notifications (")
                text.append(if (nm.areNotificationsEnabled())
                    "not blocked"
                else
                    "BLOCKED!")
                text.append("):\n")
                if (Build.VERSION.SDK_INT >= 26) {
                    val channelsWithoutGroup = nm.notificationChannels.toMutableSet()
                    for (group in nm.notificationChannelGroups) {
                        text.append("  [group] ${group.id}")
                        if (Build.VERSION.SDK_INT >= 28)
                            text.append(" isBlocked=${group.isBlocked}")
                        text.append("\n")

                        for (channel in group.channels) {
                            text.append("    ${channel.id}: importance=${channel.importance}\n")
                            channelsWithoutGroup -= channel
                        }
                    }
                    for (channel in channelsWithoutGroup)
                        text.append("  ${channel.id}: importance=${channel.importance}\n")
                }
                // permissions
                text.append("Permissions:\n")
                for (permission in arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
                        TaskProvider.PERMISSION_READ_TASKS, TaskProvider.PERMISSION_WRITE_TASKS,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    val shortPermission = permission.replace(Regex("^.+\\.permission\\."), "")
                    text    .append("  $shortPermission: ")
                            .append(if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                                "granted"
                            else
                                "denied")
                            .append("\n")
                }
                // system-wide sync settings
                text    .append("System-wide synchronization: ")
                        .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "manually")
                        .append("\n\n")

                // app settings
                val settings = SettingsManager.getInstance(context)
                val overrideProxy = settings.getBoolean(Settings.OVERRIDE_PROXY)
                text    .append("APP SETTINGS\n")
                        .append("Distrust system certs: ${settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)}\n")
                        .append("Override system proxy: $overrideProxy\n")
                if (overrideProxy == true)
                    text.append("  Proxy: ${settings.getString(Settings.OVERRIDE_PROXY_HOST)}:${settings.getInt(Settings.OVERRIDE_PROXY_PORT)}\n")
                text.append("\n")

                // main accounts
                text.append("ACCOUNTS\n")
                val accountManager = AccountManager.get(context)
                for (acct in accountManager.getAccountsByType(context.getString(R.string.account_type)))
                    try {
                        val accountSettings = AccountSettings(context, acct)
                        text.append("Account: ${acct.name}\n" +
                                "  Address book sync. interval: ${syncStatus(accountSettings, context.getString(R.string.address_books_authority))}\n" +
                                "  Calendar     sync. interval: ${syncStatus(accountSettings, CalendarContract.AUTHORITY)}\n" +
                                "  OpenTasks    sync. interval: ${syncStatus(accountSettings, TaskProvider.ProviderName.OpenTasks.authority)}\n" +
                                "  WiFi only: ").append(accountSettings.getSyncWifiOnly())
                        accountSettings.getSyncWifiOnlySSIDs()?.let {
                            text.append(", SSIDs: ${accountSettings.getSyncWifiOnlySSIDs()}")
                        }
                        text    .append("\n  getIsSyncable(AddressBooks): ${ContentResolver.getIsSyncable(acct, context.getString(R.string.address_books_authority))}")
                        text    .append("\n  getIsSyncable(CalendarContract): ${ContentResolver.getIsSyncable(acct, CalendarContract.AUTHORITY)}")
                                .append("\n  getIsSyncable(OpenTasks): ${ContentResolver.getIsSyncable(acct, TaskProvider.ProviderName.OpenTasks.authority)}")
                                .append("\n  [CardDAV] Contact group method: ${accountSettings.getGroupMethod()}")
                                .append("\n  [CalDAV] Time range (past days): ${accountSettings.getTimeRangePastDays()}")
                                .append("\n           Default alarm (min before): ${accountSettings.getDefaultAlarm()}")
                                .append("\n           Manage calendar colors: ${accountSettings.getManageCalendarColors()}")
                                .append("\n           Use event colors: ${accountSettings.getEventColors()}")
                                .append("\n")
                    } catch (e: InvalidAccountException) {
                        text.append("$acct is invalid (unsupported settings version) or does not exist\n")
                    }
                // address book accounts
                for (acct in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                    try {
                        val addressBook = LocalAddressBook(context, acct, null)
                        text.append("Address book account: ${acct.name}\n" +
                                "  Main account: ${addressBook.mainAccount}\n" +
                                "  URL: ${addressBook.url}\n" +
                                "  isSyncable(${ContactsContract.AUTHORITY}): ").append(ContentResolver.getIsSyncable(acct, ContactsContract.AUTHORITY)).append("\n" +
                                "  Sync automatically: ").append(ContentResolver.getSyncAutomatically(acct, ContactsContract.AUTHORITY)).append("\n")
                    } catch(e: Exception) {
                        text.append("$acct is invalid: ${e.message}\n")
                    }
                text.append("\n")

                text.append("SQLITE DUMP\n")
                AppDatabase.getInstance(context).dump(text)
                text.append("\n")

                try {
                    text.append(
                            "SYSTEM INFORMATION\n" +
                                    "Android version: ${Build.VERSION.RELEASE} (${Build.DISPLAY})\n" +
                                    "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n\n"
                    )
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't get system details", e)
                }

                text.append("--- END DEBUG INFO ---\n")
                report.postValue(text.toString())
            }
        }

        private fun syncStatus(settings: AccountSettings, authority: String): String {
            val interval = settings.getSyncInterval(authority)
            return if (interval != null) {
                if (interval == AccountSettings.SYNC_INTERVAL_MANUALLY) "manually" else "${interval/60} min"
            } else
                "—"
        }

    }

}
