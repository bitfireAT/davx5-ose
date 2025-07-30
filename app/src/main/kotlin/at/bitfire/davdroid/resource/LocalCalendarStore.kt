/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class LocalCalendarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val localCalendarFactory: LocalCalendar.Factory,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
): LocalDataStore<LocalCalendar> {

    override val authority: String
        get() = CalendarContract.AUTHORITY

    override fun acquireContentProvider(throwOnMissingPermissions: Boolean) = try {
        context.contentResolver.acquireContentProviderClient(authority)
    } catch (e: SecurityException) {
        // The content provider is not available for some reason. Probably because the permission is no longer granted.
        if (throwOnMissingPermissions) throw e
        null
    }

    override fun create(client: ContentProviderClient, fromCollection: Collection): LocalCalendar? {
        val service = serviceRepository.getBlocking(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        // If the collection doesn't have a color, use a default color.
        val collectionWithColor =
            if (fromCollection.color != null)
                fromCollection
            else
                fromCollection.copy(color = Constants.DAVDROID_GREEN_RGBA)

        val values = valuesFromCollectionInfo(
            info = collectionWithColor,
            withColor = true
        ).apply {
            // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
            put(Calendars.ACCOUNT_NAME, account.name)
            put(Calendars.ACCOUNT_TYPE, account.type)

            // Email address for scheduling. Used by the calendar provider to determine whether the
            // user is ORGANIZER/ATTENDEE for a certain event.
            put(Calendars.OWNER_ACCOUNT, account.name)

            // flag as visible & syncable at creation, might be changed by user at any time
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }

        logger.log(Level.INFO, "Adding local calendar", values)
        val provider = AndroidCalendarProvider(account, client)
        return localCalendarFactory.create(provider.createAndGetCalendar(values))
    }

    override fun getAll(account: Account, client: ContentProviderClient) =
        AndroidCalendarProvider(account, client)
            .findCalendars("${Calendars.SYNC_EVENTS}!=0", null)
            .map { localCalendarFactory.create(it) }

    override fun update(client: ContentProviderClient, localCollection: LocalCalendar, fromCollection: Collection) {
        val accountSettings = accountSettingsFactory.create(localCollection.androidCalendar.account)
        val values = valuesFromCollectionInfo(fromCollection, withColor = accountSettings.getManageCalendarColors())

        logger.log(Level.FINE, "Updating local calendar ${fromCollection.url}", values)
        val androidCalendar = localCollection.androidCalendar
        val provider = AndroidCalendarProvider(androidCalendar.account, client)
        provider.updateCalendar(androidCalendar.id, values)
    }

    private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
        val values = contentValuesOf(
            Calendars._SYNC_ID to info.id,
            Calendars.CALENDAR_DISPLAY_NAME to
                    if (info.displayName.isNullOrBlank()) info.url.lastSegment else info.displayName,

            Calendars.ALLOWED_AVAILABILITY to arrayOf(
                Events.AVAILABILITY_BUSY,
                Events.AVAILABILITY_FREE
            ).joinToString(",") { it.toString() },

            Calendars.ALLOWED_ATTENDEE_TYPES to arrayOf(
                Attendees.TYPE_NONE,
                Attendees.TYPE_OPTIONAL,
                Attendees.TYPE_REQUIRED,
                Attendees.TYPE_RESOURCE
            ).joinToString(",") { it.toString() },

            Calendars.ALLOWED_REMINDERS to arrayOf(
                Reminders.METHOD_DEFAULT,
                Reminders.METHOD_ALERT,
                Reminders.METHOD_EMAIL
            ).joinToString(",") { it.toString() },
        )

        if (withColor && info.color != null)
            values.put(Calendars.CALENDAR_COLOR, info.color)

        if (info.privWriteContent && !info.forceReadOnly) {
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1)
            values.put(Calendars.CAN_ORGANIZER_RESPOND, 1)
        } else
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)

        info.timezoneId?.let { tzId ->
            values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(tzId))
        }

        return values
    }

    override fun updateAccount(oldAccount: Account, newAccount: Account) {
        val values = contentValuesOf(Calendars.ACCOUNT_NAME to newAccount.name)
        val uri = Calendars.CONTENT_URI.asSyncAdapter(oldAccount)
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use {
            it.update(uri, values, "${Calendars.ACCOUNT_NAME}=?", arrayOf(oldAccount.name))
        }
    }

    override fun delete(localCollection: LocalCalendar) {
        logger.log(Level.INFO, "Deleting local calendar", localCollection)
        localCollection.androidCalendar.delete()
    }

}