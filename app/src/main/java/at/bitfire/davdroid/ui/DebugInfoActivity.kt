/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.ContactsContract
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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.databinding.ActivityDebugInfoBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.TaskProvider.ProviderName
import at.techbee.jtx.JtxContract
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.dmfs.tasks.contract.TaskContract
import java.io.*
import java.util.*
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class DebugInfoActivity: AppCompatActivity() {

    companion object {
        /** [android.accounts.Account] (as [android.os.Parcelable]) related to problem */
        private const val EXTRA_ACCOUNT = "account"

        /** sync authority name related to problem */
        private const val EXTRA_AUTHORITY = "authority"

        /** serialized [Throwable] that causes the problem */
        private const val EXTRA_CAUSE = "cause"

        /** dump of local resource related to the problem (plain-text [String]) */
        private const val EXTRA_LOCAL_RESOURCE = "localResource"

        /** logs related to the problem (path to log file, plain-text [String]) */
        private const val EXTRA_LOG_FILE = "logFile"

        /** logs related to the problem (plain-text [String]) */
        private const val EXTRA_LOGS = "logs"

        /** URL of remote resource related to the problem (plain-text [String]) */
        private const val EXTRA_REMOTE_RESOURCE = "remoteResource"

        const val FILE_DEBUG_INFO = "debug-info.txt"
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
                intent.setDataAndType(uri, "text/plain")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, null))
            }
            binding.causeView.setOnClickListener(showDebugInfo)
            binding.debugInfoView.setOnClickListener(showDebugInfo)

            binding.fab.apply {
                setOnClickListener { shareArchive() }
                isEnabled = true
            }
            binding.zipShare.setOnClickListener { shareArchive() }
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

    fun shareArchive() {
        model.generateZip { zipFile ->
            val builder = ShareCompat.IntentBuilder.from(this)
                    .setSubject("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info")
                    .setText(getString(R.string.debug_info_attached))
                    .setType("*/*")     // application/zip won't show all apps that can manage binary files, like ShareViaHttp
                    .setStream(FileProvider.getUriForFile(this, getString(R.string.authority_debug_provider), zipFile))
            builder.intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.startChooser()
        }
    }


    @HiltViewModel
    class ReportModel @Inject constructor(
        @ApplicationContext val context: Context
    ): ViewModel() {

        @Inject lateinit var db: AppDatabase
        @Inject lateinit var settings: SettingsManager

        private var initialized = false

        val cause = MutableLiveData<Throwable>()
        var logFile = MutableLiveData<File>()
        val localResource = MutableLiveData<String>()
        val remoteResource = MutableLiveData<String>()
        val debugInfo = MutableLiveData<File>()

        val zipProgress = MutableLiveData(false)
        val zipFile = MutableLiveData<File>()

        // private storage, not readable by others
        private val debugInfoDir = File(context.filesDir, "debug")

        init {
            // create debug info directory
            if (!debugInfoDir.isDirectory && !debugInfoDir.mkdir())
                throw IOException("Couldn't create debug info directory")
        }

        @UiThread
        fun generate(extras: Bundle?) {
            if (initialized)
                return
            initialized = true

            viewModelScope.launch(Dispatchers.Default) {
                val logFileName = extras?.getString(EXTRA_LOG_FILE)
                val logsText = extras?.getString(EXTRA_LOGS)
                if (logFileName != null) {
                    val file = File(logFileName)
                    if (file.isFile && file.canRead())
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
                        extras?.getParcelable(EXTRA_ACCOUNT),
                        extras?.getString(EXTRA_AUTHORITY),
                        throwable,
                        local,
                        remote
                )
            }
        }

        private fun generateDebugInfo(syncAccount: Account?, syncAuthority: String?, cause: Throwable?, localResource: String?, remoteResource: String?) {
            val debugInfoFile = File(debugInfoDir, FILE_DEBUG_INFO)
            debugInfoFile.writer().buffered().use { writer ->
                writer.append(ByteOrderMark.UTF_BOM)
                writer.append("--- BEGIN DEBUG INFO ---\n\n")

                // begin with most specific information
                if (syncAccount != null || syncAuthority != null) {
                    writer.append("SYNCHRONIZATION INFO\n")
                    if (syncAccount != null)
                        writer.append("Account: $syncAccount\n")
                    if (syncAuthority != null)
                        writer.append("Authority: $syncAuthority\n")
                    writer.append("\n")
                }

                cause?.let {
                    // Log.getStackTraceString(e) returns "" in case of UnknownHostException
                    writer.append("EXCEPTION\n${ExceptionUtils.getStackTrace(cause)}\n")
                }

                // exception details
                if (cause is DavException) {
                    cause.request?.let { request ->
                        writer.append("HTTP REQUEST\n$request\n")
                        cause.requestBody?.let { writer.append(it) }
                        writer.append("\n\n")
                    }
                    cause.response?.let { response ->
                        writer.append("HTTP RESPONSE\n$response\n")
                        cause.responseBody?.let { writer.append(it) }
                        writer.append("\n\n")
                    }
                }

                if (localResource != null)
                    writer.append("LOCAL RESOURCE\n$localResource\n\n")

                if (remoteResource != null)
                    writer.append("REMOTE RESOURCE\n$remoteResource\n\n")

                // software info
                try {
                    writer.append("SOFTWARE INFORMATION\n")
                    val table = TextTable("Package", "Version", "Code", "Installer", "Notes")
                    val pm = context.packageManager

                    val packageNames = mutableSetOf(      // we always want info about these packages:
                            BuildConfig.APPLICATION_ID,            // DAVx5
                            ProviderName.JtxBoard.packageName,     // jtx Board
                            ProviderName.OpenTasks.packageName,    // OpenTasks
                            ProviderName.TasksOrg.packageName      // tasks.org
                    )
                    // ... and info about contact and calendar provider
                    for (authority in arrayOf(ContactsContract.AUTHORITY, CalendarContract.AUTHORITY))
                        pm.resolveContentProvider(authority, 0)?.let { packageNames += it.packageName }
                    // ... and info about contact, calendar, task-editing apps
                    val dataUris = arrayOf(
                            ContactsContract.Contacts.CONTENT_URI,
                            CalendarContract.Events.CONTENT_URI,
                            TaskContract.Tasks.getContentUri(ProviderName.OpenTasks.authority),
                            TaskContract.Tasks.getContentUri(ProviderName.TasksOrg.authority)
                    )
                    for (uri in dataUris) {
                        val viewIntent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(uri, /* some random ID */ 1))
                        for (info in pm.queryIntentActivities(viewIntent, 0))
                            packageNames += info.activityInfo.packageName
                    }

                    for (packageName in packageNames)
                        try {
                            val info = pm.getPackageInfo(packageName, 0)
                            val appInfo = info.applicationInfo
                            val notes = mutableListOf<String>()
                            if (!appInfo.enabled)
                                notes += "disabled"
                            if (appInfo.flags.and(ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0)
                                notes += "<em>on external storage</em>"
                            table.addLine(
                                    info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info),
                                    pm.getInstallerPackageName(info.packageName) ?: '—', notes.joinToString(", ")
                            )
                        } catch(e: PackageManager.NameNotFoundException) {
                        }
                    writer.append(table.toString())
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't get software information", e)
                }

                // system info
                val locales: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    LocaleList.getAdjustedDefault()
                else
                    Locale.getDefault()
                writer.append(
                        "\nSYSTEM INFORMATION\n\n" +
                        "Android version: ${Build.VERSION.RELEASE} (${Build.DISPLAY})\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n\n" +
                        "Locale(s): $locales\n" +
                        "Time zone: ${TimeZone.getDefault().id}\n"
                )
                val filesPath = Environment.getDataDirectory()
                val statFs = StatFs(filesPath.path)
                writer.append("Internal memory ($filesPath): ")
                    .append(FileUtils.byteCountToDisplaySize(statFs.availableBytes))
                    .append(" free of ")
                    .append(FileUtils.byteCountToDisplaySize(statFs.totalBytes))
                    .append("\n\n")

                // connectivity
                context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                    writer.append("\nCONNECTIVITY\n\n")
                    val activeNetwork = if (Build.VERSION.SDK_INT >= 23) connectivityManager.activeNetwork else null
                    connectivityManager.allNetworks.sortedByDescending { it == activeNetwork }.forEach { network ->
                        val properties = connectivityManager.getLinkProperties(network)
                        connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                            writer  .append(if (network == activeNetwork) " ☒ " else " ☐ ")
                                    .append(properties?.interfaceName ?: "?")
                                    .append("\n   - ")
                                    .append(capabilities.toString().replace('&',' '))
                                    .append('\n')
                        }
                        if (properties != null) {
                            writer  .append("   - DNS: ")
                                    .append(properties.dnsServers.joinToString(", ") { it.hostAddress })
                            if (Build.VERSION.SDK_INT >= 28 && properties.isPrivateDnsActive)
                                writer.append(" (private mode)")
                            writer.append('\n')
                        }
                    }
                    writer.append('\n')

                    if (Build.VERSION.SDK_INT >= 23)
                        connectivityManager.defaultProxy?.let { proxy ->
                            writer.append("System default proxy: ${proxy.host}:${proxy.port}\n")
                        }
                    if (Build.VERSION.SDK_INT >= 24)
                        writer.append("Data saver: ").append(when (connectivityManager.restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                            else -> connectivityManager.restrictBackgroundStatus.toString()
                        }).append('\n')
                    writer.append('\n')
                }

                writer.append("\nCONFIGURATION\n\n")
                // power saving
                if (Build.VERSION.SDK_INT >= 28)
                    context.getSystemService<UsageStatsManager>()?.let { statsManager ->
                        val bucket = statsManager.appStandbyBucket
                        writer.append("App standby bucket: $bucket")
                            if (bucket > UsageStatsManager.STANDBY_BUCKET_ACTIVE)
                                writer.append(" (RESTRICTED!)")
                        writer.append('\n')
                    }
                if (Build.VERSION.SDK_INT >= 23)
                    context.getSystemService<PowerManager>()?.let { powerManager ->
                        writer.append("Power saving disabled: ")
                                .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                                .append('\n')
                    }
                // system-wide sync
                writer  .append("System-wide synchronization: ")
                        .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "manually")
                        .append('\n')
                // notifications
                val nm = NotificationManagerCompat.from(context)
                writer.append("\nNotifications")
                if (!nm.areNotificationsEnabled())
                    writer.append(" (blocked!)")
                writer.append(":\n")
                if (Build.VERSION.SDK_INT >= 26) {
                    val channelsWithoutGroup = nm.notificationChannels.toMutableSet()
                    for (group in nm.notificationChannelGroups) {
                        writer.append(" - ${group.id}")
                        if (Build.VERSION.SDK_INT >= 28)
                            writer.append(" isBlocked=${group.isBlocked}")
                        writer.append('\n')
                        for (channel in group.channels) {
                            writer.append("  * ${channel.id}: importance=${channel.importance}\n")
                            channelsWithoutGroup -= channel
                        }
                    }
                    for (channel in channelsWithoutGroup)
                        writer.append(" - ${channel.id}: importance=${channel.importance}\n")
                }
                writer.append('\n')
                // permissions
                writer.append("Permissions:\n")
                val ownPkgInfo = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_PERMISSIONS)
                for (permission in ownPkgInfo.requestedPermissions) {
                    val shortPermission = permission.removePrefix("android.permission.")
                    writer  .append(" - $shortPermission: ")
                            .append(if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                                "granted"
                            else
                                "denied")
                            .append('\n')
                }
                writer.append('\n')

                writer.append("\nACCOUNTS\n\n")
                // main accounts
                val accountManager = AccountManager.get(context)
                val mainAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type))
                val addressBookAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).toMutableList()
                for (account in mainAccounts) {
                    dumpMainAccount(account, writer)

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
                }
                if (addressBookAccounts.isNotEmpty()) {
                    writer.append("Address book accounts without main account:\n")
                    for (account in addressBookAccounts)
                        dumpAddressBookAccount(account, accountManager, writer)
                }

                // database dump
                writer.append("\nDATABASE DUMP\n\n")
                db.dump(writer, arrayOf("webdav_document"))

                // app settings
                writer.append("\nAPP SETTINGS\n\n")
                settings.dump(writer)

                writer.append("--- END DEBUG INFO ---\n")
                writer.toString()
            }
            debugInfo.postValue(debugInfoFile)
        }

        fun generateZip(onSuccess: (File) -> Unit) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    zipProgress.postValue(true)

                    val zipFile = File(debugInfoDir, "davx5-debug.zip")
                    Logger.log.fine("Writing debug info to ${zipFile.absolutePath}")
                    ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                        zip.setLevel(9)
                        debugInfo.value?.let { debugInfo ->
                            zip.putNextEntry(ZipEntry("debug-info.txt"))
                            debugInfo.inputStream().use {
                                IOUtils.copy(it, zip)
                            }
                            zip.closeEntry()
                        }

                        val logs = logFile.value
                        if (logs != null) {
                            // verbose logs available
                            zip.putNextEntry(ZipEntry(logs.name))
                            logs.inputStream().use {
                                IOUtils.copy(it, zip)
                            }
                            zip.closeEntry()
                        } else {
                            // logcat (short logs)
                            try {
                                Runtime.getRuntime().exec("logcat -d").also { logcat ->
                                    zip.putNextEntry(ZipEntry("logcat.txt"))
                                    IOUtils.copy(logcat.inputStream, zip)
                                }
                            } catch (e: Exception) {
                                Logger.log.log(Level.SEVERE, "Couldn't attach logcat", e)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onSuccess(zipFile)
                    }
                } catch(e: IOException) {
                    // creating attachment with debug info failed
                    Logger.log.log(Level.SEVERE, "Couldn't attach debug info", e)
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                }
                zipProgress.postValue(false)
            }
        }


        private fun dumpMainAccount(account: Account, writer: Writer) {
            writer.append(" - Account: ${account.name}\n")
            writer.append(dumpAccount(account, AccountDumpInfo.mainAccount(context)))
            try {
                val accountSettings = AccountSettings(context, account)
                writer.append("  WiFi only: ${accountSettings.getSyncWifiOnly()}")
                accountSettings.getSyncWifiOnlySSIDs()?.let { ssids ->
                    writer.append(", SSIDs: ${ssids.joinToString(", ")}")
                }
                writer.append("\n  Contact group method: ${accountSettings.getGroupMethod()}\n" +
                        "  Time range (past days): ${accountSettings.getTimeRangePastDays()}\n" +
                        "  Default alarm (min before): ${accountSettings.getDefaultAlarm()}\n" +
                        "  Manage calendar colors: ${accountSettings.getManageCalendarColors()}\n" +
                        "  Use event colors: ${accountSettings.getEventColors()}\n")
            } catch(e: InvalidAccountException) {
                writer.append("$e\n")
            }
            writer.append('\n')
        }

        private fun dumpAddressBookAccount(account: Account, accountManager: AccountManager, writer: Writer) {
            writer.append("  * Address book: ${account.name}\n")
            val table = dumpAccount(account, AccountDumpInfo.addressBookAccount())
            writer  .append(TextTable.indent(table, 4))
                    .append("URL: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_URL)}\n")
                    .append("    Read-only: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_READ_ONLY) ?: 0}\n\n")
        }

        private fun dumpAccount(account: Account, infos: Iterable<AccountDumpInfo>): String {
            val table = TextTable("Authority", "Syncable", "Auto-sync", "Interval", "Entries")
            for (info in infos) {
                var nrEntries = "—"
                var client: ContentProviderClient? = null
                if (info.countUri != null)
                    try {
                        client = context.contentResolver.acquireContentProviderClient(info.authority)
                        if (client != null) {
                            client.query(info.countUri, null, "account_name=? AND account_type=?", arrayOf(account.name, account.type), null)?.use { cursor ->
                                nrEntries = "${cursor.count} ${info.countStr}"
                            }
                        }
                    } catch(ignored: Exception) {
                    } finally {
                        client?.closeCompat()
                    }
                table.addLine(
                        info.authority,
                        ContentResolver.getIsSyncable(account, info.authority),
                        ContentResolver.getSyncAutomatically(account, info.authority),
                        ContentResolver.getPeriodicSyncs(account, info.authority).firstOrNull()?.let { periodicSync ->
                            "${periodicSync.period / 60} min"
                        },
                        nrEntries
                )
            }
            return table.toString()
        }

    }


    data class AccountDumpInfo(
            val authority: String,
            val countUri: Uri?,
            val countStr: String?) {

        companion object {

            fun mainAccount(context: Context) = listOf(
                    AccountDumpInfo(context.getString(R.string.address_books_authority), null, null),
                    AccountDumpInfo(CalendarContract.AUTHORITY, CalendarContract.Events.CONTENT_URI, "event(s)"),
                    AccountDumpInfo(ProviderName.JtxBoard.authority, JtxContract.JtxICalObject.CONTENT_URI, "jtx Board ICalObject(s)"),
                    AccountDumpInfo(ProviderName.OpenTasks.authority, TaskContract.Tasks.getContentUri(ProviderName.OpenTasks.authority), "OpenTasks task(s)"),
                    AccountDumpInfo(ProviderName.TasksOrg.authority, TaskContract.Tasks.getContentUri(ProviderName.TasksOrg.authority), "tasks.org task(s)"),
                    AccountDumpInfo(ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI, "wrongly assigned raw contact(s)")
            )

            fun addressBookAccount() = listOf(
                    AccountDumpInfo(ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI, "raw contact(s)")
            )

        }

    }


    class IntentBuilder(context: Context) {

        companion object {
            val MAX_ELEMENT_SIZE = 800*FileUtils.ONE_KB.toInt()
        }

        val intent = Intent(context, DebugInfoActivity::class.java)

        fun newTask(): IntentBuilder {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return this
        }

        fun withAccount(account: Account?): IntentBuilder {
            if (account != null)
                intent.putExtra(EXTRA_ACCOUNT, account)
            return this
        }

        fun withAuthority(authority: String?): IntentBuilder {
            if (authority != null)
                intent.putExtra(EXTRA_AUTHORITY, authority)
            return this
        }

        fun withCause(throwable: Throwable?): IntentBuilder {
            if (throwable != null)
                intent.putExtra(EXTRA_CAUSE, throwable)
            return this
        }

        fun withLocalResource(dump: String?): IntentBuilder {
            if (dump != null)
                intent.putExtra(EXTRA_LOCAL_RESOURCE, StringUtils.abbreviate(dump, MAX_ELEMENT_SIZE))
            return this
        }

        fun withLogFile(logFile: File?): IntentBuilder {
            if (logFile != null)
                intent.putExtra(EXTRA_LOG_FILE, logFile.absolutePath)
            return this
        }

        fun withLogs(logs: String?): IntentBuilder {
            if (logs != null)
                intent.putExtra(EXTRA_LOGS, StringUtils.abbreviate(logs, MAX_ELEMENT_SIZE))
            return this
        }

        fun withRemoteResource(remote: HttpUrl?): IntentBuilder {
            if (remote != null)
                intent.putExtra(EXTRA_REMOTE_RESOURCE, remote.toString())
            return this
        }


        fun build() = intent

        fun share() = intent.apply {
            action = Intent.ACTION_SEND
        }

    }

}
