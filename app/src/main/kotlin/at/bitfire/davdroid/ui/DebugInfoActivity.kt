/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.ContentProviderClient
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
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.DrawerState
import androidx.compose.material.DrawerValue
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TextTable
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.PeriodicSyncWorker
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName
import at.techbee.jtx.JtxContract
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
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
import java.io.IOError
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

/**
 * Debug info activity. Provides verbose information for debugging and support. Should enable users
 * to debug problems themselves, but also to send it to the support.
 *
 * Important use cases to test:
 *
 * - debug info from App settings / Debug info (should provide debug info)
 * - login with some broken login URL (should provide debug info + logs; check logs, too)
 * - enable App settings / Verbose logs, then open debug info activity (should provide debug info + logs; check logs, too)
 */
@AndroidEntryPoint
class DebugInfoActivity : AppCompatActivity() {

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

    @Inject lateinit var modelFactory: ReportModel.Factory
    private val model by viewModels<ReportModel> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(intent.extras) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { 
            MdcTheme {
                val debugInfo by model.debugInfo.observeAsState()
                val zipProgress by model.zipProgress.observeAsState(false)

                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    floatingActionButton = {
                        if (debugInfo != null && !zipProgress) {
                            FloatingActionButton(
                                onClick = model::generateZip
                            ) {
                                Icon(Icons.Rounded.Share, stringResource(R.string.share))
                            }
                        }
                    },
                    scaffoldState = ScaffoldState(
                        drawerState = remember { DrawerState(DrawerValue.Closed) },
                        snackbarHostState = snackbarHostState
                    )
                ) { paddingValues ->
                    val error by model.error.observeAsState()
                    LaunchedEffect(error) {
                        error?.let {
                            snackbarHostState.showSnackbar(
                                message = it,
                                duration = SnackbarDuration.Long
                            )

                            model.error.value = null
                        }
                    }

                    Content(paddingValues)
                }
            }
        }
    }

    @Composable
    fun Content(paddingValues: PaddingValues) {
        val debugInfo by model.debugInfo.observeAsState()
        val zipProgress by model.zipProgress.observeAsState(false)
        val modelCause by model.cause.observeAsState()
        val localResource by model.localResource.observeAsState()
        val remoteResource by model.remoteResource.observeAsState()
        val logFile by model.logFile.observeAsState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (debugInfo == null) item { LinearProgressIndicator() }
            if (debugInfo != null) {
                if (zipProgress) item { LinearProgressIndicator() }
                item {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_archive_caption),
                        subtitle = stringResource(R.string.debug_info_archive_subtitle),
                        message = stringResource(R.string.debug_info_archive_text),
                        icon = Icons.Rounded.Share
                    ) {
                        TextButton(
                            onClick = model::generateZip,
                            enabled = !zipProgress
                        ) {
                            Text(stringResource(R.string.debug_info_archive_share))
                        }
                    }
                }
            }
            modelCause?.let { cause ->
                item {
                    CardWithImage(
                        image = painterResource(R.drawable.undraw_server_down),
                        title = when (cause) {
                            is HttpException -> stringResource(if (cause.code / 100 == 5) R.string.debug_info_server_error else R.string.debug_info_http_error)
                            is DavException -> stringResource(R.string.debug_info_webdav_error)
                            is IOException, is IOError -> stringResource(R.string.debug_info_io_error)
                            else -> cause::class.java.simpleName
                        },
                        subtitle = cause.localizedMessage,
                        message = stringResource(
                            if (cause is HttpException)
                                when {
                                    cause.code == 403 -> R.string.debug_info_http_403_description
                                    cause.code == 404 -> R.string.debug_info_http_404_description
                                    cause.code / 100 == 5 -> R.string.debug_info_http_5xx_description
                                    else -> R.string.debug_info_unexpected_error
                                }
                            else
                                R.string.debug_info_unexpected_error
                        ),
                        icon = Icons.Rounded.Info
                    ) {
                        TextButton(
                            enabled = debugInfo != null,
                            onClick = { shareFile(debugInfo!!) }
                        ) {
                            Text(stringResource(R.string.debug_info_view_details))
                        }
                    }
                }
            }
            debugInfo?.let { info ->
                item {
                    CardWithImage(
                        image = painterResource(R.drawable.undraw_server_down),
                        title = stringResource(R.string.debug_info_title),
                        subtitle = stringResource(R.string.debug_info_subtitle),
                        icon = Icons.Rounded.BugReport
                    ) {
                        TextButton(
                            onClick = { shareFile(info) }
                        ) {
                            Text(stringResource(R.string.debug_info_view_details))
                        }
                    }
                }
            }
            (localResource to remoteResource)
                .takeIf { (l, r) -> l != null || r != null }
                ?.let { (local, remote) ->
                    item {
                        CardWithImage(
                            title = stringResource(R.string.debug_info_involved_caption),
                            subtitle = stringResource(R.string.debug_info_involved_subtitle),
                            icon = Icons.Rounded.Adb
                        ) {
                            if (remote != null) {
                                Text(
                                    text = stringResource(R.string.debug_info_involved_remote),
                                    style = MaterialTheme.typography.body1
                                )
                                Text(
                                    text = remote,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (local != null) {
                                Text(
                                    text = stringResource(R.string.debug_info_involved_local),
                                    style = MaterialTheme.typography.body1
                                )
                                Text(
                                    text = local,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            logFile?.let { logs ->
                item {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_logs_caption),
                        subtitle = stringResource(R.string.debug_info_logs_subtitle),
                        icon = Icons.Rounded.BugReport
                    ) {
                        TextButton(
                            onClick = { shareFile(logs) }
                        ) {
                            Text(stringResource(R.string.debug_info_logs_view))
                        }
                    }
                }
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.authority_debug_provider),
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "text/plain")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, null))
    }


    class ReportModel @AssistedInject constructor (
        val context: Application,
        @Assisted extras: Bundle?
    ) : AndroidViewModel(context) {

        @AssistedFactory
        interface Factory {
            fun create(extras: Bundle?): ReportModel
        }

        @Inject
        lateinit var db: AppDatabase
        @Inject
        lateinit var settings: SettingsManager

        val cause = MutableLiveData<Throwable>()
        var logFile = MutableLiveData<File>()
        val localResource = MutableLiveData<String>()
        val remoteResource = MutableLiveData<String>()
        val debugInfo = MutableLiveData<File>()

        // feedback for UI
        val zipProgress = MutableLiveData(false)
        val zipFile = MutableLiveData<File>()
        val error = MutableLiveData<String>()

        init {
            // create debug info directory
            val debugDir = Logger.debugDir() ?: throw IOException("Couldn't create debug info directory")

            viewModelScope.launch(Dispatchers.Default) {
                // create log file from EXTRA_LOGS or log file
                val logsText = extras?.getString(EXTRA_LOGS)
                if (logsText != null) {
                    val file = File(debugDir, FILE_LOGS)
                    if (!file.exists() || file.canWrite()) {
                        file.writer().buffered().use { writer ->
                            IOUtils.copy(StringReader(logsText), writer)
                        }
                        logFile.postValue(file)
                    } else
                        Logger.log.warning("Can't write logs to $file")
                } else Logger.getDebugLogFile()?.let { debugLogFile ->
                    if (debugLogFile.isFile && debugLogFile.canRead())
                        logFile.postValue(debugLogFile)
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
                // power saving
                if (Build.VERSION.SDK_INT >= 28)
                    context.getSystemService<UsageStatsManager>()?.let { statsManager ->
                        val bucket = statsManager.appStandbyBucket
                        writer.append("App standby bucket: $bucket")
                        if (bucket > UsageStatsManager.STANDBY_BUCKET_ACTIVE)
                            writer.append(" (RESTRICTED!)")
                        writer.append('\n')
                    }
                context.getSystemService<PowerManager>()?.let { powerManager ->
                    writer.append("Power saving disabled: ")
                        .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes" else "no")
                        .append('\n')
                }
                // system-wide sync
                writer.append("System-wide synchronization: ")
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
            debugInfo.postValue(debugInfoFile)
        }

        fun generateZip() {
            try {
                zipProgress.postValue(true)

                val file = File(Logger.debugDir(), "davx5-debug.zip")
                Logger.log.fine("Writing debug info to ${file.absolutePath}")
                ZipOutputStream(file.outputStream().buffered()).use { zip ->
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

                // success, show ZIP file
                zipFile.postValue(file)
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't generate debug info ZIP", e)
                error.postValue(e.localizedMessage)
            } finally {
                zipProgress.postValue(false)
            }
        }


        private fun dumpMainAccount(account: Account, writer: Writer) {
            writer.append("\n\n - Account: ${account.name}\n")
            writer.append(dumpAccount(account, AccountDumpInfo.mainAccount(context, account)))
            try {
                val accountSettings = AccountSettings(context, account)

                val credentials = accountSettings.credentials()
                val authStr = mutableListOf<String>()
                if (credentials.userName != null)
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
                var client: ContentProviderClient? = null
                if (info.countUri != null)
                    try {
                        client = context.contentResolver.acquireContentProviderClient(info.authority)
                        if (client != null)
                            client.query(info.countUri, null, null, null, null)?.use { cursor ->
                                nrEntries = "${cursor.count} ${info.countStr}"
                            }
                        else
                            nrEntries = "n/a"
                    } catch (e: Exception) {
                        nrEntries = e.toString()
                    } finally {
                        client?.close()
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
                for (workerName in listOf(
                    SyncWorker.workerName(account, authority),
                    PeriodicSyncWorker.workerName(account, authority)
                )) {
                    WorkManager.getInstance(context).getWorkInfos(
                        WorkQuery.Builder.fromUniqueWorkNames(listOf(workerName)).build()
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
                AccountDumpInfo(account, ProviderName.JtxBoard.authority, JtxContract.JtxICalObject.CONTENT_URI.asJtxSyncAdapter(account), "jtx Board ICalObject(s)"),
                AccountDumpInfo(account, ProviderName.OpenTasks.authority, TaskContract.Tasks.getContentUri(ProviderName.OpenTasks.authority).asCalendarSyncAdapter(account), "OpenTasks task(s)"),
                AccountDumpInfo(account, ProviderName.TasksOrg.authority, TaskContract.Tasks.getContentUri(ProviderName.TasksOrg.authority).asCalendarSyncAdapter(account), "tasks.org task(s)"),
                AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "wrongly assigned raw contact(s)")
            )

            fun addressBookAccount(account: Account) = listOf(
                AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "raw contact(s)")
            )

        }

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
                intent.putExtra(EXTRA_LOCAL_RESOURCE, StringUtils.abbreviate(dump, MAX_ELEMENT_SIZE))
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