/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
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
import android.os.Environment
import android.os.LocaleList
import android.os.PowerManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.LogFileHandler
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.worker.BaseSyncWorker
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.TimeZone
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dmfs.tasks.contract.TaskContract
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter as asCalendarSyncAdapter
import at.bitfire.vcard4android.Utils.asSyncAdapter as asContactsSyncAdapter
import at.techbee.jtx.JtxContract.asSyncAdapter as asJtxSyncAdapter

@HiltViewModel(assistedFactory = DebugInfoModel.Factory::class)
class DebugInfoModel @AssistedInject constructor(
    @Assisted private val details: DebugInfoDetails,
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext val context: Context,
    private val db: AppDatabase,
    private val logger: Logger,
    private val settings: SettingsManager
) : ViewModel() {

    data class DebugInfoDetails(
        val account: Account?,
        val authority: String?,
        val cause: Throwable?,
        val localResource: String?,
        val remoteResource: String?,
        val logs: String?
    )

    @AssistedFactory
    interface Factory {
        fun createWithDetails(details: DebugInfoDetails): DebugInfoModel
    }

    companion object {
        private const val FILE_DEBUG_INFO = "debug-info.txt"
        private const val FILE_LOGS = "logs.txt"
    }

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
        val debugDir = LogFileHandler.debugDir(context) ?: throw IOException("Couldn't create debug info directory")

        viewModelScope.launch(Dispatchers.Main) {
            // create log file from EXTRA_LOGS or log file
            if (details.logs != null) {
                val file = File(debugDir, FILE_LOGS)
                if (!file.exists() || file.canWrite()) {
                    file.printWriter().use { writer ->
                        writer.write(details.logs)
                    }
                    uiState = uiState.copy(logFile = file)
                } else
                    logger.warning("Can't write logs to $file")
            } else LogFileHandler.getDebugLogFile(context)?.let { debugLogFile ->
                if (debugLogFile.isFile && debugLogFile.canRead())
                    uiState = uiState.copy(logFile = debugLogFile)
            }

            uiState = uiState.copy(
                cause = details.cause,
                localResource = details.localResource,
                remoteResource = details.remoteResource
            )

            generateDebugInfo(
                syncAccount = details.account,
                syncAuthority = details.authority,
                cause = details.cause,
                localResource = details.localResource,
                remoteResource = details.remoteResource
            )
        }
    }

    /**
     * Creates debug info and saves it to [FILE_DEBUG_INFO] in [LogFileHandler.debugDir]
     *
     * Note: Part of this method and all of it's helpers (listed below) should probably be extracted in the future
     */
    private fun generateDebugInfo(syncAccount: Account?, syncAuthority: String?, cause: Throwable?, localResource: String?, remoteResource: String?) {
        val debugInfoFile = File(LogFileHandler.debugDir(context), FILE_DEBUG_INFO)
        debugInfoFile.printWriter().use { writer ->
            writer.println("--- BEGIN DEBUG INFO ---")
            writer.println()

            // begin with most specific information
            if (syncAccount != null || syncAuthority != null) {
                writer.append("SYNCHRONIZATION INFO\n")
                if (syncAccount != null)
                    writer.append("Account: $syncAccount\n")
                if (syncAuthority != null)
                    writer.append("Authority: $syncAuthority\n")
                writer.append("\n")
            }

            if (cause != null) {
                writer.println("EXCEPTION")
                cause.printStackTrace(writer)
                writer.println()
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
                        if (appInfo?.enabled == false)
                            notes += "disabled"
                        if (appInfo?.flags?.and(ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0)
                            notes += "<em>on external storage</em>"
                        table.addLine(
                            info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info),
                            pm.getInstallerPackageName(info.packageName) ?: '—', notes.joinToString(", ")
                        )
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                writer.append(table.toString())
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't get software information", e)
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
                .append(Formatter.formatFileSize(context, statFs.availableBytes))
                .append(" free of ")
                .append(Formatter.formatFileSize(context, statFs.totalBytes))
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
            for (permission in ownPkgInfo.requestedPermissions ?: emptyArray()) {
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

            // accounts (grouped by main account)
            writer.append("\nACCOUNTS\n\n")
            val accountManager = AccountManager.get(context)
            val addressBookAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).toMutableList()
            for (account in accountRepository.getAll()) {
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

    /**
     * Creates the ZIP file containing both [FILE_DEBUG_INFO] and [FILE_LOGS].
     *
     * Note: Part of this method should probably be extracted to a more suitable location
     */
    fun generateZip() {
        try {
            uiState = uiState.copy(zipInProgress = true)

            val file = File(LogFileHandler.debugDir(context), "davx5-debug.zip")
            logger.fine("Writing debug info to ${file.absolutePath}")
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.setLevel(9)
                uiState.debugInfo?.let { debugInfo ->
                    zip.putNextEntry(ZipEntry("debug-info.txt"))
                    Files.copy(debugInfo, zip)
                    zip.closeEntry()
                }

                val logs = uiState.logFile
                if (logs != null) {
                    // verbose logs available
                    zip.putNextEntry(ZipEntry(logs.name))
                    Files.copy(logs, zip)
                    zip.closeEntry()
                } else {
                    // logcat (short logs)
                    try {
                        Runtime.getRuntime().exec("logcat -d").also { logcat ->
                            zip.putNextEntry(ZipEntry("logcat.txt"))
                            ByteStreams.copy(logcat.inputStream, zip)
                        }
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Couldn't attach logcat", e)
                    }
                }
            }

            // success, show ZIP file
            uiState = uiState.copy(zipFile = file)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't generate debug info ZIP", e)
            uiState = uiState.copy(error = e.localizedMessage)
        } finally {
            uiState = uiState.copy(zipInProgress = false)
        }
    }


    /**
     * Appends relevant android account information the given writer.
     *
     * Note: Helper method of [generateDebugInfo].
     */
    private fun dumpMainAccount(account: Account, writer: Writer) {
        writer.append("\n\n - Account: ${account.name}\n")
        writer.append(dumpAccount(account, AccountDumpInfo.mainAccount(context, account)))
        try {
            val accountSettings = accountSettingsFactory.forAccount(account)

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

    /**
     * Appends relevant address book type android account information to the given writer.
     *
     * Note: Helper method of [generateDebugInfo].
     */
    private fun dumpAddressBookAccount(account: Account, accountManager: AccountManager, writer: Writer) {
        writer.append("  * Address book: ${account.name}\n")
        val table = dumpAccount(account, AccountDumpInfo.addressBookAccount(account))
        writer.append(TextTable.indent(table, 4))
            .append("URL: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_URL)}\n")
            .append("    Read-only: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_READ_ONLY) ?: 0}\n")
    }

    /**
     * Retrieves specified information from an android account
     *
     * Note: Helper method of [generateDebugInfo].
     *
     * @return the requested information
     */
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
            val accountSettings = accountSettingsFactory.forAccount(account)
            table.addLine(
                info.authority,
                ContentResolver.getIsSyncable(account, info.authority),
                ContentResolver.getSyncAutomatically(account, info.authority),  // content-triggered sync
                accountSettings.getSyncInterval(info.authority)?.takeIf { it >= 0 }?.let {"${it/60} min"},
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