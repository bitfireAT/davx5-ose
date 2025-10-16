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
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.getContactLookupUri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
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
import dagger.Lazy
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
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): SyncNotificationManager
    }

    /**
     * Tries to inform the user that the content provider is missing or disabled.
     * Use [dismissProviderError] to dismiss the notification.
     *
     * @param authority The authority of the content provider.
     */
    fun notifyProviderError(authority: String) {
        val (titleResource, textResource) = when (authority) {
            ContactsContract.AUTHORITY ->
                R.string.sync_warning_contacts_storage_disabled_title to
                R.string.sync_warning_contacts_storage_disabled_description
            CalendarContract.AUTHORITY ->
                R.string.sync_warning_calendar_storage_disabled_title to
                R.string.sync_warning_calendar_storage_disabled_description
            else -> {
                logger.log(Level.WARNING, "Content provider error for unknown authority: $authority")
                return
            }
        }

        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_SYNC_ERROR, tag = authority) {
            NotificationCompat.Builder(context, notificationRegistry.CHANNEL_SYNC_ERRORS)
                .setSmallIcon(R.drawable.ic_sync_problem_notify)
                .setContentTitle(context.getString(titleResource))
                .setContentText(context.getString(textResource))
                .setSubText(account.name)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .addAction(NotificationCompat.Action(
                    android.R.drawable.ic_menu_view,
                    context.getString(R.string.sync_warning_manage_apps),
                    PendingIntent.getActivity(context, 0,
                        Intent(Settings.ACTION_APPLICATION_SETTINGS),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ))
                .build()
        }
    }

    /**
     * Dismisses the notification for content provider errors.
     *
     * @param authority The authority of the content provider used as notification tag.
     */
    fun dismissProviderError(authority: String) =
        dismissNotification(authority)

    /**
     * Tries to inform the user that an exception occurred during synchronization. Includes the affected
     * local resource, its collection, the URL, the exception and a user message.
     *
     * @param syncDataType      The type of data which was synced.
     * @param notificationTag   The tag to use for the notification.
     * @param message           The message to show to the user.
     * @param localCollection   The affected local collection.
     * @param e                 The exception that occurred.
     * @param local             The affected local resource.
     * @param remote            The remote URL that caused the exception.
     */
    fun notifyException(
        syncDataType: SyncDataType,
        notificationTag: String,
        message: String,
        localCollection: LocalCollection<*>,
        e: Throwable,
        local: LocalResource<*>?,
        remote: HttpUrl?
    ) = notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_SYNC_ERROR, tag = notificationTag) {
        val contentIntent: Intent
        if (e is UnauthorizedException) {
            contentIntent = Intent(context, AccountSettingsActivity::class.java)
            contentIntent.putExtra(
                AccountSettingsActivity.EXTRA_ACCOUNT,
                account
            )
        } else {
            contentIntent = buildDebugInfoIntent(syncDataType, e, local, remote)
        }

        // to make the PendingIntent unique
        contentIntent.data = "davdroid:exception/${e.hashCode()}".toUri()

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

        builder.build()
    }

    /**
     * Sends a notification to inform the user that a push notification has been received, the
     * sync has been scheduled, but it still has not run.
     * Use [dismissInvalidResource] to dismiss the notification.
     *
     * @param dataType          The type of data which was synced.
     * @param notificationTag   The tag to use for the notification.
     * @param collection        The affected collection.
     * @param fileName          The name of the file containing the invalid resource.
     * @param title             The title of the notification.
     */
    fun notifyInvalidResource(
        dataType: SyncDataType,
        notificationTag: String,
        collection: Collection,
        e: Throwable,
        fileName: String,
        title: String
    ) {
        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_INVALID_RESOURCE, tag = notificationTag) {
            val intent = buildDebugInfoIntent(dataType, e, null, collection.url.resolve(fileName))

            val builder = NotificationCompat.Builder(context, notificationRegistry.CHANNEL_SYNC_WARNINGS)
            builder.setSmallIcon(R.drawable.ic_warning_notify)
                .setContentTitle(title)
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

    /**
     * Dismisses the (error) notification for a specific collection.
     *
     * @param localCollectionTag The tag of the local collection which is used as notification tag also.
     */
    fun dismissInvalidResource(localCollectionTag: String) =
        dismissNotification(localCollectionTag)


    // helpers

    /**
     * Dismisses the sync error notification for a specific tag.
     */
    private fun dismissNotification(tag: String) = NotificationManagerCompat.from(context)
        .cancel(tag, NotificationRegistry.NOTIFY_SYNC_ERROR)

    /**
     * Builds intent to go to debug information with the given exception, resource and remote address.
     */
    private fun buildDebugInfoIntent(
        dataType: SyncDataType,
        e: Throwable,
        local: LocalResource<*>?,
        remote: HttpUrl?
    ): Intent {
        val builder = DebugInfoActivity.IntentBuilder(context)
            .withAccount(account)
            .withSyncDataType(dataType)
            .withCause(e)

        if (local != null)
            try {
                // Add local resource dump to intent
                builder.withLocalResource(local.toSummaryString())

                // Add id and type to view local resource
                builder.withLocalResourceUri(getLocalResourceUri(local))
            } catch (_: OutOfMemoryError) {
                // For instance because of a huge contact photo; maybe we're lucky and can catch it
            }

        if (remote != null)
            builder.withRemoteResource(remote)

        return builder.build()
    }

    /**
     * Retrieves/Creates local resource uri to view the given local resource.
     * Note: Only OpenTasks supported. TasksOrg and jtxBoard do not support viewing
     * tasks via intent-filter (yet).
     */
    private fun getLocalResourceUri(local: LocalResource<*>): Uri? = local.id?.let { id ->
        when (local) {
            is LocalContact ->
                getContactLookupUri(
                    context.contentResolver,
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, id)
                )

            is LocalEvent ->
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, local.id)
            is LocalTask ->
                tasksAppManager.get().currentProvider()?.let { provider ->
                    if (provider == TaskProvider.ProviderName.OpenTasks) {
                        // Only OpenTasks supported. TasksOrg and jtxBoard do not support viewing
                        // tasks via intent-filter (yet)
                        val contentUri = TaskContract.Tasks.getContentUri(provider.authority)
                        ContentUris.withAppendedId(contentUri, id)
                    } else null
                }
            else -> null
        }
    }

}