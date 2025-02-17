/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.ui.account.AccountSettingsActivity
import at.bitfire.ical4android.TaskProvider
import com.google.common.base.Ascii
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import org.dmfs.tasks.contract.TaskContract
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class SyncNotificationManager @AssistedInject constructor(
    @Assisted val account: Account,
    @Assisted val authority: String,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, authority: String): SyncNotificationManager
    }

    fun dismiss(notificationTag: String = authority) = NotificationManagerCompat.from(context)
        .cancel(notificationTag, NotificationRegistry.NOTIFY_SYNC_ERROR)

    fun notifyContentProviderError() = notificationRegistry.notifyIfPossible(
        NotificationRegistry.NOTIFY_SYNC_ERROR,
        tag = authority
    ) {
        NotificationCompat.Builder(context, notificationRegistry.CHANNEL_SYNC_ERRORS)
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .apply {
                when (authority) {
                    ContactsContract.AUTHORITY -> {
                        setContentTitle(context.getString(R.string.account_list_contacts_storage_disabled_title))
                        setContentText(context.getString(R.string.account_list_contacts_storage_disabled))
                    }
                    CalendarContract.AUTHORITY -> {
                        setContentTitle(context.getString(R.string.account_list_calendar_storage_disabled_title))
                        setContentText(context.getString(R.string.account_list_calendar_storage_disabled))
                    }
                }
            }
            .setSubText(account.name)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.account_list_manage_apps),
                PendingIntent.getActivity(context, 0,
                    Intent(Settings.ACTION_APPLICATION_SETTINGS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ))
            .build()
    }

    /**
     * Tries to inform the user that an exception occurred during synchronization. Includes the affected
     * local resource, its collection, the URL, the exception and a user message.
     */
    fun notifyException(
        notificationTag: String,
        message: String,
        localCollection: LocalCollection<*>,
        e: Throwable,
        local: LocalResource<*>?,
        remote: HttpUrl?
    ) = notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_SYNC_ERROR, tag = notificationTag) {
        val contentIntent: Intent
        var viewItemAction: NotificationCompat.Action? = null
        if (e is UnauthorizedException) {
            contentIntent = Intent(context, AccountSettingsActivity::class.java)
            contentIntent.putExtra(
                AccountSettingsActivity.EXTRA_ACCOUNT,
                account
            )
        } else {
            contentIntent = buildDebugInfoIntentForLocalResource(e, local, remote)
            if (local != null)
                viewItemAction = buildViewItemActionForLocalResource(local)
        }

        // to make the PendingIntent unique
        contentIntent.data = Uri.parse("davdroid:exception/${e.hashCode()}")

        val channel: String
        val priority: Int
        if (e is IOException) {
            channel = notificationRegistry.CHANNEL_SYNC_IO_ERRORS
            priority = NotificationCompat.PRIORITY_MIN
        } else {
            channel = notificationRegistry.CHANNEL_SYNC_ERRORS
            priority = NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationCompat.Builder(context, channel)
        builder.setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(localCollection.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle(builder).bigText(message))
            .setSubText(account.name)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(contentIntent)
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
        viewItemAction?.let { builder.addAction(it) }

        builder.build()
    }

    /**
     * Sends a notification to inform the user that a push notification has been received, the
     * sync has been scheduled, but it still has not run.
     */
    fun notifyInvalidResource(
        notificationTag: String,
        collection: Collection,
        e: Throwable,
        fileName: String,
        notifyInvalidResourceTitle: String
    ) {
        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_INVALID_RESOURCE, tag = notificationTag) {
            val intent = buildDebugInfoIntentForLocalResource(e, null, collection.url.resolve(fileName))

            val builder = NotificationCompat.Builder(context, notificationRegistry.CHANNEL_SYNC_WARNINGS)
            builder.setSmallIcon(R.drawable.ic_warning_notify)
                .setContentTitle(notifyInvalidResourceTitle)
                .setContentText(context.getString(R.string.sync_invalid_resources_ignoring))
                .setSubText(account.name)
                .setContentIntent(
                    TaskStackBuilder.create(context)
                        .addNextIntent(intent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .priority = NotificationCompat.PRIORITY_LOW
            builder.build()
        }
    }


    // helpers

    /**
     * Builds intent to go to debug information with the given exception, resource and remote address.
     */
    private fun buildDebugInfoIntentForLocalResource(
        e: Throwable,
        local: LocalResource<*>?,
        remote: HttpUrl?
    ): Intent {
        val builder = DebugInfoActivity.IntentBuilder(context)
            .withAccount(account)
            .withAuthority(authority)
            .withCause(e)

        if (local != null)
            try {
                // Truncate the string to avoid the Intent to be > 1 MB, which doesn't work (IPC limit)
                builder.withLocalResource(Ascii.truncate(local.toString(), 10000, "[…]"))
            } catch (_: OutOfMemoryError) {
                // For instance because of a huge contact photo; maybe we're lucky and can catch it
            }

        if (remote != null)
            builder.withRemoteResource(remote)

        return builder.build()
    }

    /**
     * Builds view action for notification, based on the given local resource.
     */
    private fun buildViewItemActionForLocalResource(local: LocalResource<*>): NotificationCompat.Action? {
        logger.log(Level.FINE, "Adding view action for local resource", local)
        val intent = local.id?.let { id ->
            when (local) {
                is LocalContact ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id))
                is LocalEvent ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                is LocalTask ->
                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), id))
                else ->
                    null
            }
        }
        return if (intent != null && context.packageManager.resolveActivity(intent, 0) != null)
            NotificationCompat.Action(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.sync_error_view_item),
                TaskStackBuilder.create(context)
                    .addNextIntent(intent)
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        else
            null
    }

}