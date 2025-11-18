/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract
import com.google.common.base.Ascii
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.HttpUrl
import java.io.File
import java.time.Instant
import javax.inject.Inject

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
class DebugInfoActivity: AppCompatActivity() {

    @Inject
    lateinit var tasksAppManager: Lazy<TasksAppManager>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        val viewResourceIntent = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_LOCAL_RESOURCE_URI,
            Uri::class.java
        )?.let { uri ->
            buildViewLocalResourceIntent(uri)
        }

        val remoteResource = extras?.getString(EXTRA_REMOTE_RESOURCE)
        setContent { 
            DebugInfoScreen(
                account = IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java),
                syncDataType = extras?.getString(EXTRA_SYNC_DATA_TYPE),
                cause = IntentCompat.getSerializableExtra(intent, EXTRA_CAUSE, Throwable::class.java),
                canViewResource = viewResourceIntent != null,
                localResource = extras?.getString(EXTRA_LOCAL_RESOURCE_SUMMARY),
                remoteResource = remoteResource,
                logs = extras?.getString(EXTRA_LOGS),
                timestamp = extras?.getLong(EXTRA_TIMESTAMP),
                onShareZipFile = ::shareZipFile,
                onViewFile = ::viewFile,
                onCopyRemoteUrl = {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("Remote resource", remoteResource)
                    clipboard.setPrimaryClip(clipData)
                },
                onViewLocalResource = { viewResource(viewResourceIntent) },
                onNavUp = ::onSupportNavigateUp
            )
        }
    }

    private fun shareZipFile(file: File) {
        shareFile(
            file,
            "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} debug info",
            getString(R.string.debug_info_attached),
            "*/*",    // application/zip won't show all apps that can manage binary files, like ShareViaHttp
        )
    }

    /**
     * Starts an activity passing sharing intent along
     */
    private fun shareFile(
        file: File,
        subject: String? = null,
        text: String? = null,
        type: String = "text/plain"
    ) {
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.authority_debug_provider),
            file
        )
        ShareCompat.IntentBuilder(this)
            .setSubject(subject)
            .setText(text)
            .setType(type)
            .setStream(uri)
            .startChooser()
    }

    /**
     * Starts an activity passing file viewer intent along
     */
    private fun viewFile(
        file: File,
        title: String? = null
    ) {
        val uri = FileProvider.getUriForFile(
            this,
            getString(R.string.authority_debug_provider),
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    /**
     * Starts activity to view the affected/problematic resource
     */
    private fun viewResource(intent: Intent?) = try {
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            this,
            getString(R.string.debug_info_can_not_view_resource),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Builds intent to view the problematic local resource at given Uri.
     *
     * Note that the TasksOrg app does not support viewing tasks via intent-filter.
     * @see [at.bitfire.davdroid.resource.LocalResource.getViewUri]
     */
    private fun buildViewLocalResourceIntent(uri: Uri): Intent? {
        // Support ACTION_VIEW
        val supportActionView = listOf(
            CalendarContract.AUTHORITY, // any calendar app
            JtxContract.JtxICalObject.VIEW_INTENT_HOST // jtx Board for journals, notes, tasks
        )
        if (uri.authority in supportActionView)
            return Intent(Intent.ACTION_VIEW, uri)

        // Need ACTION_EDIT (OpenTasks crashes on using ACTION_VIEW)
        if (uri.authority == TaskProvider.ProviderName.OpenTasks.authority) // OpenTasks app
            return Intent(Intent.ACTION_EDIT, uri)

        // Need CONTENT_ITEM_TYPE to be set
        if (uri.authority == ContactsContract.AUTHORITY) // any contacts app
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            }

        return null
    }

    /**
     * Builder for [DebugInfoActivity] intents
     */
    class IntentBuilder(context: Context) {

        companion object {
            const val MAX_ELEMENT_SIZE = 800 * 1024     // 800 kB
        }

        val intent = Intent(context, DebugInfoActivity::class.java)
            .putExtra(EXTRA_TIMESTAMP, Instant.now().epochSecond)

        fun newTask(): IntentBuilder {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return this
        }

        fun withAccount(account: Account?): IntentBuilder {
            if (account != null)
                intent.putExtra(EXTRA_ACCOUNT, account)
            return this
        }

        fun withSyncDataType(dataType: SyncDataType?): IntentBuilder {
            if (dataType != null)
                intent.putExtra(EXTRA_SYNC_DATA_TYPE, dataType.name)
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
                    EXTRA_LOCAL_RESOURCE_SUMMARY,
                    Ascii.truncate(dump, MAX_ELEMENT_SIZE, "...")
                )
            return this
        }

        fun withLocalResourceUri(uri: Uri?): IntentBuilder {
            if (uri == null)
                return this
            intent.putExtra(EXTRA_LOCAL_RESOURCE_URI, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return this
        }

        fun withLogs(logs: String?): IntentBuilder {
            if (logs != null)
                intent.putExtra(
                    EXTRA_LOGS,
                    Ascii.truncate(logs, MAX_ELEMENT_SIZE, "...")
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

    companion object {
        /** [android.accounts.Account] (as [android.os.Parcelable]) related to problem */
        private const val EXTRA_ACCOUNT = "account"

        /** sync data type related to problem */
        private const val EXTRA_SYNC_DATA_TYPE = "syncDataType"

        /** serialized [Throwable] that causes the problem */
        private const val EXTRA_CAUSE = "cause"

        /** Summary (dump of [at.bitfire.davdroid.resource.LocalResource] properties) of local resource related to the problem (plain-text [String]) */
        internal const val EXTRA_LOCAL_RESOURCE_SUMMARY = "localResourceSummary"

        /** [Uri] of local resource related to the problem (as [android.os.Parcelable]) */
        internal const val EXTRA_LOCAL_RESOURCE_URI = "localResourceUri"

        /** logs related to the problem (plain-text [String]) */
        private const val EXTRA_LOGS = "logs"

        /** URL of remote resource related to the problem (plain-text [String]) */
        private const val EXTRA_REMOTE_RESOURCE = "remoteResource"

        /** A timestamp of the moment at which the error took place. */
        private const val EXTRA_TIMESTAMP = "timestamp"
    }

}