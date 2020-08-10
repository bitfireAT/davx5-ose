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
import android.text.Html
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.UiThread
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
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.exception.DavException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.dmfs.tasks.contract.TaskContract
import java.io.*
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DebugInfoActivity: AppCompatActivity() {

    companion object {
        /** serialized [Throwable] that causes the problem */
        const val EXTRA_CAUSE = "cause"

        /** logs related to the problem (plain-text [String]) */
        const val EXTRA_LOGS = "logs"

        /** logs related to the problem (path to log file, plain-text [String]) */
        const val EXTRA_LOG_FILE = "logFile"

        /** [android.accounts.Account] (as [android.os.Parcelable]) related to problem */
        const val EXTRA_ACCOUNT = "account"

        /** sync authority name related to problem */
        const val EXTRA_AUTHORITY = "authority"

        /** local resource related to the problem (plain-text [String]) */
        const val EXTRA_LOCAL_RESOURCE = "localResource"

        /** remote resource related to the problem (plain-text [String]) */
        const val EXTRA_REMOTE_RESOURCE = "remoteResource"

        const val FILE_DEBUG_INFO = "debug-info.html"
        const val FILE_LOGS = "logs.txt"
    }

    private val model by viewModels<ReportModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.generate(intent.extras)

        val binding = DataBindingUtil.setContentView<ActivityDebugInfoBinding>(this, R.layout.activity_debug_info)
        binding.model = model
        binding.lifecycleOwner = this

        model.cause.observe(this, Observer { cause ->
            if (cause == null)
                return@Observer

            binding.causeCaption.text = when (cause) {
                is HttpException -> getString(if (cause.code / 100 == 5) R.string.debug_info_server_error else R.string.debug_info_http_error)
                is DavException -> getString(R.string.debug_info_webdav_error)
                is IOException, is IOError -> getString(R.string.debug_info_io_error)
                else -> cause::class.java.simpleName
            }

            binding.causeText.text = getString(
                if (cause is HttpException)
                    when {
                        cause.code == 403 -> R.string.debug_info_http_403_description
                        cause.code == 404 -> R.string.debug_info_http_404_description
                        cause.code/100 == 5 -> R.string.debug_info_http_5xx_description
                        else -> R.string.debug_info_unexpected_error
                    }
                else
                    R.string.debug_info_unexpected_error
            )
        })

        model.debugInfo.observe(this, Observer { debugInfo ->
            val showDebugInfo = View.OnClickListener {
                val uri = FileProvider.getUriForFile(this, getString(R.string.authority_debug_provider), debugInfo)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "text/html")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, null))
            }
            binding.causeView.setOnClickListener(showDebugInfo)
            binding.debugInfoView.setOnClickListener(showDebugInfo)
        })

        model.logFile.observe(this, Observer { logs ->
            binding.logsView.setOnClickListener {
                val uri = FileProvider.getUriForFile(this, getString(R.string.authority_debug_provider), logs)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "text/plain")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, null))
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_debug_info, menu)
        return true
    }


    fun onShare(item: MenuItem? = null) {
        model.generateZip() { zipFile ->
            val builder = ShareCompat.IntentBuilder.from(this)
                    .setSubject("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info")
                    .setText(getString(R.string.debug_info_attached))
                    .setType("application/zip")
                    .setStream(FileProvider.getUriForFile(this, getString(R.string.authority_debug_provider), zipFile))
            builder.intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.startChooser()
        }
    }


    class ReportModel(
            val context: Application
    ): AndroidViewModel(context) {

        private var initialized = false

        val cause = MutableLiveData<Throwable>()
        var logFile = MutableLiveData<File>()
        val localResource = MutableLiveData<String>()
        val remoteResource = MutableLiveData<String>()
        val debugInfo = MutableLiveData<File>()

        val zipFile = MutableLiveData<File>()

        // private storage, not readable by others
        val debugInfoDir = File(context.filesDir, "debug")

        init {
            // create debug info directory
            if (!debugInfoDir.isDirectory() && !debugInfoDir.mkdir())
                throw IOException("Couldn't create debug info directory")
        }

        @UiThread
        fun generate(extras: Bundle?) {
            if (initialized)
                return
            initialized = true

            viewModelScope.async(Dispatchers.Default) {
                val logFileName = extras?.getString(EXTRA_LOG_FILE)
                val logsText = extras?.getString(EXTRA_LOGS)
                if (logFileName != null) {
                    val file = File(logFileName)
                    if (file.isFile() && file.canRead())
                        logFile.postValue(file)
                    else
                        Logger.log.warning("Can't read logs from $logFileName")
                } else if (logsText != null) {
                    val file = File(debugInfoDir, FILE_LOGS)
                    if (!file.exists() || file.canWrite()) {
                        file.writer().buffered().use { writer ->
                            IOUtils.copy(StringReader(logsText), writer)
                        }
                        logFile.postValue(file)
                    } else
                        Logger.log.warning("Can't write logs to $file")
                }

                val throwable = extras?.getSerializable(EXTRA_CAUSE) as? Throwable
                cause.postValue(throwable)

                val local = extras?.getString(EXTRA_LOCAL_RESOURCE)
                localResource.postValue(local)

                val remote = extras?.getString(EXTRA_REMOTE_RESOURCE)
                remoteResource.postValue(remote)

                generateDebugInfo(
                        extras?.getParcelable<Account>(EXTRA_ACCOUNT),
                        extras?.getString(EXTRA_AUTHORITY),
                        throwable,
                        local,
                        remote
                )
            }
        }

        fun generateDebugInfo(syncAccount: Account?, syncAuthority: String?, cause: Throwable?, localResource: String?, remoteResource: String?) {
            val debugInfoFile = File(debugInfoDir, FILE_DEBUG_INFO)
            debugInfoFile.writer().buffered().use { writer ->
                writer.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/><style>")
                context.assets.open("debug-info.css").use {
                    val css = IOUtils.toString(it, Charsets.UTF_8)
                    writer.append(css)
                }
                writer.append("</style></head><body><h1>${context.getString(R.string.app_name)} Debug Info</h1>")

                // begin with most specific information
                if (syncAccount != null)
                    writer.append("<h2>Synchronization info</h2><p>Account: <code>$syncAccount</code></p>")
                if (syncAuthority != null)
                    writer.append("<p>Authority: <code>$syncAuthority</code></p>")

                cause?.let {
                    // Log.getStackTraceString(e) returns "" in case of UnknownHostException
                    writer.append("<h2>Exception</h2><pre>${Html.escapeHtml(ExceptionUtils.getStackTrace(cause))}</pre>")
                }

                // exception details
                if (cause is HttpException) {
                    cause.request?.let { request ->
                        writer.append("<h3>HTTP Request</h3><p><code>${Html.escapeHtml(request)}</code></p><pre>\n")
                        cause.requestBody?.let { writer.append(Html.escapeHtml(it)) }
                        writer.append("</pre>")
                    }
                    cause.response?.let { response ->
                        writer.append("<h3>HTTP Response</h3><p><code>${Html.escapeHtml(response)}</code></p><pre>\n")
                        cause.responseBody?.let { writer.append(Html.escapeHtml(it)) }
                        writer.append("</pre>")
                    }
                }

                if (localResource != null)
                    writer.append("<h2>Local Resource</h2><p><code>${Html.escapeHtml(localResource)}</code></p>")

                if (remoteResource != null)
                    writer.append("<h2>Remote Resource</h2><p><code>${Html.escapeHtml(remoteResource)}</code></p>")

                // software info
                try {
                    writer.append("<h2>Software information</h2><table><thead><tr><td>Package</td>" +
                                "<td>Version</td><td>Code</td><td>Installer</td><td>Notes</td></tr></thead><tbody>")
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
                            writer    .append("<tr><td>$appID</td>")
                                    .append("<td>${info.versionName}</td>")
                                    .append("<td>${PackageInfoCompat.getLongVersionCode(info)}</td>")
                                    .append("<td>${pm.getInstallerPackageName(appID) ?: '—'}</td><td>")
                            val notes = mutableListOf<String>()
                            if (!appInfo.enabled)
                                notes += "disabled"
                            if (appInfo.flags.and(ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0)
                                notes += "<em>on external storage</em>"
                            if (notes.isNotEmpty())
                                writer.append(notes.joinToString(", "))
                            writer.append("</td></tr>")
                        } catch(e: PackageManager.NameNotFoundException) {
                        }
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't get software information", e)
                }
                writer.append("</tbody></table>")

                // system info
                writer.append(
                        "<h2>System information</h2>" +
                        "<p>Android version: ${Build.VERSION.RELEASE} (${Build.DISPLAY})<br/>" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})</p>"
                )

                // connectivity
                context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                    writer.append("<h2>Connectivity</h2><ul>")
                    val activeNetwork = if (Build.VERSION.SDK_INT >= 23) connectivityManager.activeNetwork else null
                    connectivityManager.allNetworks.sortedByDescending { it == activeNetwork }.forEach { network ->
                        val capabilities = connectivityManager.getNetworkCapabilities(network)!!
                        writer.append("<li>")
                        if (network == activeNetwork)
                            writer.append("<strong>")
                        writer.append("${capabilities.toString().replace('&',' ')}")
                        if (network == activeNetwork)
                            writer.append("</strong>")
                        writer.append("</li>")
                    }
                    writer.append("</ul>")
                    if (Build.VERSION.SDK_INT >= 23)
                        connectivityManager.defaultProxy?.let { proxy ->
                            writer.append("<p>System default proxy: ${proxy.host}:${proxy.port}</p>")
                        }
                    if (Build.VERSION.SDK_INT >= 24)
                        writer.append("<p>Data saver: ").append(when (connectivityManager.restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                            else -> connectivityManager.restrictBackgroundStatus.toString()
                        }).append("</p>")
                }

                writer.append("<h2>Configuration</h2>")
                // power saving
                if (Build.VERSION.SDK_INT >= 23)
                    context.getSystemService<PowerManager>()?.let { powerManager ->
                        writer.append("<p>Power saving disabled: ")
                                .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                                .append("</p>")
                    }
                // system-wide sync
                writer    .append("<p>System-wide synchronization: ")
                        .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "<em>manually</em>")
                        .append("</p>")
                // notifications
                val nm = NotificationManagerCompat.from(context)
                writer.append("<h3>Notifications ")
                if (!nm.areNotificationsEnabled())
                    writer.append("(blocked!)")
                writer.append("</h3><ul>")
                if (Build.VERSION.SDK_INT >= 26) {
                    val channelsWithoutGroup = nm.notificationChannels.toMutableSet()
                    for (group in nm.notificationChannelGroups) {
                        writer.append("<li>${group.id} ")
                        if (Build.VERSION.SDK_INT >= 28)
                            writer.append(" isBlocked=${group.isBlocked}")
                        writer.append("<ul>")
                        for (channel in group.channels) {
                            writer.append("<li>${channel.id}: importance=${channel.importance}</li>")
                            channelsWithoutGroup -= channel
                        }
                        writer.append("</ul>")
                    }
                    writer.append("<ul>")
                    for (channel in channelsWithoutGroup)
                        writer.append("<li>${channel.id}: importance=${channel.importance}</li>")
                    writer.append("</ul>")
                }
                writer.append("</ul>")
                // permissions
                writer.append("<h3>Permissions</h3><ul>")
                for (permission in arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
                        TaskProvider.PERMISSION_READ_TASKS, TaskProvider.PERMISSION_WRITE_TASKS,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    val shortPermission = permission.replace(Regex("^.+\\.permission\\."), "")
                    writer    .append("<li>$shortPermission: ")
                            .append(if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                                "granted"
                            else
                                "denied")
                            .append("</li>")
                }
                writer.append("</ul>")

                // app settings
                val settings = SettingsManager.getInstance(context)
                val overrideProxy = settings.getBoolean(Settings.OVERRIDE_PROXY)
                writer    .append("<h2>App settings</h2>")
                        .append("<p>Distrust system certs: ${settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)}</p>")
                        .append("<p>Override system proxy: $overrideProxy")
                if (overrideProxy)
                    writer.append("(${settings.getString(Settings.OVERRIDE_PROXY_HOST)}:${settings.getInt(Settings.OVERRIDE_PROXY_PORT)})")
                writer.append("</p>")

                writer.append("<h2>Accounts</h2><ul class='only-indent'>")
                // main accounts
                val accountManager = AccountManager.get(context)
                val mainAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type))
                val addressBookAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).toMutableList()
                for (account in mainAccounts) {
                    writer.append("<li>")
                    dumpMainAccount(account, writer)

                    writer.append("<ul>")
                    val iter = addressBookAccounts.iterator()
                    while (iter.hasNext()) {
                        val addressBookAccount = iter.next()
                        val mainAccount = Account(
                                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_MAIN_ACCOUNT_NAME),
                                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_MAIN_ACCOUNT_TYPE)
                        )
                        if (mainAccount == account) {
                            dumpAddressBookAccount(addressBookAccount, accountManager, writer)
                            iter.remove()
                        }
                    }
                    writer.append("</ul></li>")
                }
                writer.append("</ul>")
                if (addressBookAccounts.isNotEmpty()) {
                    writer.append("<p><em>Address book accounts without main account:</em></p><ul class='only-indent'>")
                    for (account in addressBookAccounts) {
                        writer.append("<li>")
                        dumpAddressBookAccount(account, accountManager, writer)
                        writer.append("</li>")
                    }
                    writer.append("</ul>")
                }

                writer.append("<h2>Database dump</h2>")
                AppDatabase.getInstance(context).dumpHtml(writer)

                writer.append("</body></html>")
                writer.toString()
            }
            debugInfo.postValue(debugInfoFile)
        }

        fun generateZip(callback: (File) -> Unit) {
            val context = getApplication<Application>()

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val zipFile = File(debugInfoDir, "davx5-debug.zip")
                    Logger.log.fine("Writing debug info to ${zipFile.absolutePath}")
                    ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                        debugInfo.value?.let { debugInfo ->
                            zip.putNextEntry(ZipEntry("debug-info.html"))
                            debugInfo.inputStream().use {
                                IOUtils.copy(it, zip)
                            }
                            zip.closeEntry()
                        }

                        // logs
                        logFile.value?.let { logs ->
                            zip.putNextEntry(ZipEntry(logs.name))
                            logs.inputStream().use {
                                IOUtils.copy(it, zip)
                            }
                            zip.closeEntry()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        callback(zipFile)
                    }
                } catch(e: IOException) {
                    // creating attachment with debug info failed
                    Logger.log.log(Level.SEVERE, "Couldn't attach debug info", e)
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                }
            }
        }


        private fun dumpMainAccount(account: Account, writer: Writer) {
            val context = getApplication<Application>()
            writer.append("<table>" +
                    "<caption>${TextUtils.htmlEncode(account.name)}</caption>" +
                    "<thead><tr><td>Authority</td><td>isSyncable</td><td>getSyncAutomatically</td><td>Sync interval</td></tr></thead><tbody>")
            for (authority in arrayOf(
                    context.getString(R.string.address_books_authority),
                    CalendarContract.AUTHORITY,
                    TaskProvider.ProviderName.OpenTasks.authority
            )) {
                writer.append("<tr><td>$authority</td>" +
                        "<td>${isSyncableHtml(account, authority)}</td>" +
                        "<td>${ContentResolver.getSyncAutomatically(account, authority)}</td><td>")
                ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.let { periodicSync ->
                    writer.append("${periodicSync.period/60} min")
                }
                writer.append("</td></tr>")
            }
            writer.append("</tbody></table><p>")

            try {
                val accountSettings = AccountSettings(context, account)
                writer.append("WiFi only: ${accountSettings.getSyncWifiOnly()}")
                accountSettings.getSyncWifiOnlySSIDs()?.let {
                    val ssids = it.map { TextUtils.htmlEncode(it) }.joinToString(", ")
                    writer.append(", SSIDs: $ssids")
                }
                writer.append("</p><p>Contact group method: <code>${accountSettings.getGroupMethod()}</code></p>" +
                        "<p>Time range (past days): ${accountSettings.getTimeRangePastDays()}<br/>" +
                        "Default alarm (min before): ${accountSettings.getDefaultAlarm()}<br/>" +
                        "Manage calendar colors: ${accountSettings.getManageCalendarColors()}<br/>" +
                        "Use event colors: ${accountSettings.getEventColors()}")
            } catch(e: InvalidAccountException) {
                writer.append("<em>$e</em>")
            }
            writer.append("</p>")
        }

        private fun dumpAddressBookAccount(account: Account, accountManager: AccountManager, writer: Writer) {
            writer.append("<table>" +
                    "<caption>${TextUtils.htmlEncode(account.name)}</caption><tbody>" +
                    "<thead><tr><td colspan=2>Contacts</td></tr></thead><tbody>" +
                    "<tr><td>isSyncable</td><td>${isSyncableHtml(account, ContactsContract.AUTHORITY)}</td></tr>" +
                    "<tr><td>getSyncAutomatically</td><td>${ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)}</td></tr>" +
                    "<tr><td>Sync interval</td><td>")
            ContentResolver.getPeriodicSyncs(account, ContactsContract.AUTHORITY).firstOrNull()?.let { periodicSync ->
                writer.append("${periodicSync.period/60} min")
            }
            writer.append("</td></tr>" +
                    "<tr><td>URL</td><td>${TextUtils.htmlEncode(accountManager.getUserData(account, LocalAddressBook.USER_DATA_URL))}</td></tr>" +
                    "<tr><td>read-only</td><td>${accountManager.getUserData(account, LocalAddressBook.USER_DATA_READ_ONLY) ?: 0}</td></tr>" +
                    "</tbody></table>")
        }

        private fun isSyncableHtml(account: Account, authority: String): String {
            val result = ContentResolver.getIsSyncable(account, authority)
            return if (result == -1)
                "<em>-1</em>"
            else
                result.toString()
        }

    }

}
