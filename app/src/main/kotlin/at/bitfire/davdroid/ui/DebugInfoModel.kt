/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.os.PowerManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.BaseSyncWorker
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.dmfs.tasks.contract.TaskContract
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.Writer
import java.util.TimeZone
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter as asCalendarSyncAdapter
import at.bitfire.vcard4android.Utils.asSyncAdapter as asContactsSyncAdapter
import at.techbee.jtx.JtxContract.asSyncAdapter as asJtxSyncAdapter

class DebugInfoModel @AssistedInject constructor (
    val context: Application,
    @Assisted extras: Bundle?
) : AndroidViewModel(context) {

    companion object {
        /** [android.accounts.Account] (as [android.os.Parcelable]) related to problem */
        private const val EXTRA_ACCOUNT = "account"

        /** sync authority name related to problem */
        private const val EXTRA_AUTHORITY = "authority"

        /** serialized [Throwable] that causes the problem */
        private const val EXTRA_CAUSE = "cause"

        /** dump of local resource related to the problem (plain-text [String]) */
        private const val EXTRA_LOCAL_RESOURCE = "localResource"

        /** logs related to the problem (plain-text [String]) */
        private const val EXTRA_LOGS = "logs"

        /** URL of remote resource related to the problem (plain-text [String]) */
        private const val EXTRA_REMOTE_RESOURCE = "remoteResource"

        const val FILE_DEBUG_INFO = "debug-info.txt"
        const val FILE_LOGS = "logs.txt"
    }

    @AssistedFactory
    interface Factory {
        fun create(extras: Bundle?): DebugInfoModel
    }

    @Inject
    lateinit var db: AppDatabase
    @Inject
    lateinit var settings: SettingsManager

    data class UiState(
        val cause: Throwable? = null,
        val localResource: String? = null,
        val remoteResource: String? = null,
        val logFile: File? = null,
        val debugInfo: File? = null,
        val zipFile: File? = null,
        val zipInProgress: Boolean = false,
        val error: String? = null
    )

    var uiState by mutableStateOf(UiState())
        private set

    fun resetError() {
        uiState = uiState.copy(error = null)
    }

    fun resetZipFile() {
        uiState = uiState.copy(zipFile = null)
    }

    init {
        // create debug info directory
        val debugDir = Logger.debugDir() ?: throw IOException("Couldn't create debug info directory")

        viewModelScope.launch(Dispatchers.Main) {
            // create log file from EXTRA_LOGS or log file
            val logsText = extras?.getString(EXTRA_LOGS)
            if (logsText != null) {
                val file = File(debugDir, FILE_LOGS)
                if (!file.exists() || file.canWrite()) {
                    file.writer().buffered().use { writer ->
                        IOUtils.copy(StringReader(logsText), writer)
                    }
                    uiState = uiState.copy(logFile = file)
                } else
                    Logger.log.warning("Can't write logs to $file")
            } else Logger.getDebugLogFile()?.let { debugLogFile ->
                if (debugLogFile.isFile && debugLogFile.canRead())
                    uiState = uiState.copy(logFile = debugLogFile)
            }

            val throwable = extras?.getSerializable(EXTRA_CAUSE) as? Throwable
            uiState = uiState.copy(cause = throwable)

            val local = extras?.getString(EXTRA_LOCAL_RESOURCE)
            uiState = uiState.copy(localResource = local)

            val remote = extras?.getString(EXTRA_REMOTE_RESOURCE)
            uiState = uiState.copy(remoteResource = remote)

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
        val debugInfoFile = File(Logger.debugDir(), FILE_DEBUG_INFO)
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
                    TaskProvider.ProviderName.JtxBoard.packageName,     // jtx Board
                    TaskProvider.ProviderName.OpenTasks.packageName,    // OpenTasks
                    TaskProvider.ProviderName.TasksOrg.packageName      // tasks.org
                )
                // ... and info about contact and calendar provider
                for (authority in arrayOf(ContactsContract.AUTHORITY, CalendarContract.AUTHORITY))
                    pm.resolveContentProvider(authority, 0)?.let { packageNames += it.packageName }
                // ... and info about contact, calendar, task-editing apps
                val dataUris = arrayOf(
                    ContactsContract.Contacts.CONTENT_URI,
                    CalendarContract.Events.CONTENT_URI,
                    TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority),
                    TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.TasksOrg.authority)
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
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                writer.append(table.toString())
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't get software information", e)
            }

            // system info
            val locales: Any = LocaleList.getAdjustedDefault()
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

            // power saving
            if (Build.VERSION.SDK_INT >= 28)
                context.getSystemService<UsageStatsManager>()?.let { statsManager ->
                    val bucket = statsManager.appStandbyBucket
                    writer
                        .append("App standby bucket: ")
                        .append(
                            when {
                                bucket <= 5 -> "exempted (very good)"
                                bucket <= UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active (good)"
                                bucket <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set (bad: job restrictions apply)"
                                bucket <= UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent (bad: job restrictions apply)"
                                bucket <= UsageStatsManager.STANDBY_BUCKET_RARE -> "rare (very bad: job and network restrictions apply)"
                                bucket <= UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted (very bad: job and network restrictions apply)"
                                else -> "$bucket"
                            }
                        )
                    writer.append('\n')
                }
            context.getSystemService<PowerManager>()?.let { powerManager ->
                writer.append("App exempted from power saving: ")
                    .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes (good)" else "no (bad)")
                    .append('\n')
                    .append("System in power-save mode: ")
                    .append(if (powerManager.isPowerSaveMode) "yes (restrictions apply!)" else "no")
                    .append('\n')
            }
            // system-wide sync
            writer.append("System-wide synchronization: ")
                .append(if (ContentResolver.getMasterSyncAutomatically()) "automatically" else "manually")
                .append("\n\n")

            // connectivity
            context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
                writer.append("\nCONNECTIVITY\n\n")
                val activeNetwork = connectivityManager.activeNetwork
                connectivityManager.allNetworks.sortedByDescending { it == activeNetwork }.forEach { network ->
                    val properties = connectivityManager.getLinkProperties(network)
                    connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                        writer.append(if (network == activeNetwork) " ☒ " else " ☐ ")
                            .append(properties?.interfaceName ?: "?")
                            .append("\n   - ")
                            .append(capabilities.toString().replace('&', ' '))
                            .append('\n')
                    }
                    if (properties != null) {
                        writer.append("   - DNS: ")
                            .append(properties.dnsServers.joinToString(", ") { it.hostAddress })
                        if (Build.VERSION.SDK_INT >= 28 && properties.isPrivateDnsActive)
                            writer.append(" (private mode)")
                        writer.append('\n')
                    }
                }
                writer.append('\n')

                connectivityManager.defaultProxy?.let { proxy ->
                    writer.append("System default proxy: ${proxy.host}:${proxy.port}\n")
                }
                writer.append("Data saver: ").append(
                    when (connectivityManager.restrictBackgroundStatus) {
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                        else -> connectivityManager.restrictBackgroundStatus.toString()
                    }
                ).append('\n')
                writer.append('\n')
            }

            writer.append("\nCONFIGURATION\n\n")
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
                writer.append(" - $shortPermission: ")
                    .append(
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                            "granted"
                        else
                            "denied"
                    )
                    .append('\n')
            }
            writer.append('\n')

            // main accounts
            writer.append("\nACCOUNTS\n\n")
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
        uiState = uiState.copy(debugInfo = debugInfoFile)
    }

    fun generateZip() {
        try {
            uiState = uiState.copy(zipInProgress = true)

            val file = File(Logger.debugDir(), "davx5-debug.zip")
            Logger.log.fine("Writing debug info to ${file.absolutePath}")
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.setLevel(9)
                uiState.debugInfo?.let { debugInfo ->
                    zip.putNextEntry(ZipEntry("debug-info.txt"))
                    debugInfo.inputStream().use {
                        IOUtils.copy(it, zip)
                    }
                    zip.closeEntry()
                }

                val logs = uiState.logFile
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

            // success, show ZIP file
            uiState = uiState.copy(zipFile = file)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't generate debug info ZIP", e)
            uiState = uiState.copy(error = e.localizedMessage)
        } finally {
            uiState = uiState.copy(zipInProgress = false)
        }
    }


    private fun dumpMainAccount(account: Account, writer: Writer) {
        writer.append("\n\n - Account: ${account.name}\n")
        writer.append(dumpAccount(account, AccountDumpInfo.mainAccount(context, account)))
        try {
            val accountSettings = AccountSettings(context, account)

            val credentials = accountSettings.credentials()
            val authStr = mutableListOf<String>()
            if (credentials.username != null)
                authStr += "user name"
            if (credentials.password != null)
                authStr += "password"
            if (credentials.certificateAlias != null)
                authStr += "client certificate"
            credentials.authState?.let { authState ->
                authStr += "OAuth [${authState.authorizationServiceConfiguration?.authorizationEndpoint}]"
            }
            if (authStr.isNotEmpty())
                writer  .append("  Authentication: ")
                    .append(authStr.joinToString(", "))
                    .append("\n")

            writer.append("  WiFi only: ${accountSettings.getSyncWifiOnly()}")
            accountSettings.getSyncWifiOnlySSIDs()?.let { ssids ->
                writer.append(", SSIDs: ${ssids.joinToString(", ")}")
            }
            writer.append(
                "\n  Contact group method: ${accountSettings.getGroupMethod()}\n" +
                        "  Time range (past days): ${accountSettings.getTimeRangePastDays()}\n" +
                        "  Default alarm (min before): ${accountSettings.getDefaultAlarm()}\n" +
                        "  Manage calendar colors: ${accountSettings.getManageCalendarColors()}\n" +
                        "  Use event colors: ${accountSettings.getEventColors()}\n"
            )

            writer.append("\nSync workers:\n")
                .append(dumpSyncWorkersInfo(account))
                .append("\n")
        } catch (e: InvalidAccountException) {
            writer.append("$e\n")
        }
        writer.append('\n')
    }

    private fun dumpAddressBookAccount(account: Account, accountManager: AccountManager, writer: Writer) {
        writer.append("  * Address book: ${account.name}\n")
        val table = dumpAccount(account, AccountDumpInfo.addressBookAccount(account))
        writer.append(TextTable.indent(table, 4))
            .append("URL: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_URL)}\n")
            .append("    Read-only: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_READ_ONLY) ?: 0}\n")
    }

    private fun dumpAccount(account: Account, infos: Iterable<AccountDumpInfo>): String {
        val table = TextTable("Authority", "isSyncable", "syncAutomatically", "Interval", "Entries")
        for (info in infos) {
            var nrEntries = "—"
            if (info.countUri != null)
                try {
                    context.contentResolver.acquireContentProviderClient(info.authority)?.use { client ->
                        client.query(info.countUri, null, null, null, null)?.use { cursor ->
                            nrEntries = "${cursor.count} ${info.countStr}"
                        }
                    }
                } catch (e: Exception) {
                    nrEntries = e.toString()
                }
            val accountSettings = AccountSettings(context, account)
            table.addLine(
                info.authority,
                ContentResolver.getIsSyncable(account, info.authority),
                ContentResolver.getSyncAutomatically(account, info.authority),  // content-triggered sync
                accountSettings.getSyncInterval(info.authority)?.let {"${it/60} min"},
                nrEntries
            )
        }
        return table.toString()
    }

    /**
     * Gets sync workers info
     * Note: WorkManager does not return worker names when queried, so we create them and ask
     * whether they exist one by one
     */
    private fun dumpSyncWorkersInfo(account: Account): String {
        val table = TextTable("Tags", "Authority", "State", "Next run", "Retries", "Generation", "Periodicity")
        listOf(
            context.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY,
            TaskProvider.ProviderName.JtxBoard.authority,
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority
        ).forEach { authority ->
            val tag = BaseSyncWorker.commonTag(account, authority)
            WorkManager.getInstance(context).getWorkInfos(
                WorkQuery.Builder.fromTags(listOf(tag)).build()
            ).get().forEach { workInfo ->
                table.addLine(
                    workInfo.tags.map { it.replace("\\bat\\.bitfire\\.davdroid\\.".toRegex(), ".") },
                    authority,
                    "${workInfo.state} (${workInfo.stopReason})",
                    workInfo.nextScheduleTimeMillis.let { nextRun ->
                        when (nextRun) {
                            Long.MAX_VALUE -> "—"
                            else -> DateUtils.getRelativeTimeSpanString(nextRun)
                        }
                    },
                    workInfo.runAttemptCount,
                    workInfo.generation,
                    workInfo.periodicityInfo?.let { periodicity ->
                        "every ${periodicity.repeatIntervalMillis/60000} min"
                    } ?: "not periodic"
                )
            }
        }
        return table.toString()
    }

    class IntentBuilder(context: Context) {

        companion object {
            const val MAX_ELEMENT_SIZE = 800 * FileUtils.ONE_KB.toInt()
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
                intent.putExtra(
                    EXTRA_LOCAL_RESOURCE,
                    StringUtils.abbreviate(dump, MAX_ELEMENT_SIZE)
                )
            return this
        }

        fun withLogs(logs: String?): IntentBuilder {
            if (logs != null)
                intent.putExtra(
                    EXTRA_LOGS,
                    StringUtils.abbreviate(logs, MAX_ELEMENT_SIZE)
                )
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

data class AccountDumpInfo(
    val account: Account,
    val authority: String,
    val countUri: Uri?,
    val countStr: String?,
) {

    companion object {

        fun mainAccount(context: Context, account: Account) = listOf(
            AccountDumpInfo(account, context.getString(R.string.address_books_authority), null, null),
            AccountDumpInfo(account, CalendarContract.AUTHORITY, CalendarContract.Events.CONTENT_URI.asCalendarSyncAdapter(account), "event(s)"),
            AccountDumpInfo(account, TaskProvider.ProviderName.JtxBoard.authority, JtxContract.JtxICalObject.CONTENT_URI.asJtxSyncAdapter(account), "jtx Board ICalObject(s)"),
            AccountDumpInfo(account, TaskProvider.ProviderName.OpenTasks.authority, TaskContract.Tasks.getContentUri(
                TaskProvider.ProviderName.OpenTasks.authority).asCalendarSyncAdapter(account), "OpenTasks task(s)"),
            AccountDumpInfo(account, TaskProvider.ProviderName.TasksOrg.authority, TaskContract.Tasks.getContentUri(
                TaskProvider.ProviderName.TasksOrg.authority).asCalendarSyncAdapter(account), "tasks.org task(s)"),
            AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "wrongly assigned raw contact(s)")
        )

        fun addressBookAccount(account: Account) = listOf(
            AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "raw contact(s)")
        )

    }

}