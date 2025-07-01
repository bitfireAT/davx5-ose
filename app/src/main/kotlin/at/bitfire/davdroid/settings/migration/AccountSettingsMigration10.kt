/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.ical4android.TaskProvider
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import org.dmfs.tasks.contract.TaskContract
import javax.inject.Inject
import kotlin.use

/**
 * Task synchronization now handles alarms, categories, relations and unknown properties.
 * Setting task ETags to null will cause them to be downloaded (and parsed) again.
 *
 * Also update the allowed reminder types for calendars.
 */
class AccountSettingsMigration10 @Inject constructor(
    @ApplicationContext private val context: Context
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.use { provider ->
            val tasksUri = provider.tasksUri().asSyncAdapter(account)
            val emptyETag = contentValuesOf(LocalTask.COLUMN_ETAG to null)
            provider.client.update(tasksUri, emptyETag, "${TaskContract.Tasks._DIRTY}=0 AND ${TaskContract.Tasks._DELETED}=0", null)
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { provider ->
                provider.update(
                    CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(account),
                    contentValuesOf(
                        Calendars.ALLOWED_REMINDERS to arrayOf(
                            Reminders.METHOD_DEFAULT,
                            Reminders.METHOD_ALERT,
                            Reminders.METHOD_EMAIL
                        ).joinToString(",") { it.toString() }
                    ), null, null)
            }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(10)
        abstract fun provide(impl: AccountSettingsMigration10): AccountSettingsMigration
    }

}