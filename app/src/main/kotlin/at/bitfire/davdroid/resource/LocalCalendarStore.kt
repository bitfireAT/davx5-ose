/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendar.Companion.calendarBaseValues
import at.bitfire.ical4android.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class LocalCalendarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
): LocalDataStore<LocalCalendar> {

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalCalendar? {
        val service = serviceRepository.get(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        val values = valuesFromCollectionInfo(
            // If the collection doesn't have a color, use a default color.
            info = fromCollection.copy(color = fromCollection.color ?: Constants.DAVDROID_GREEN_RGBA),
            withColor = true
        )

        // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
        values.put(Calendars.ACCOUNT_NAME, account.name)
        values.put(Calendars.ACCOUNT_TYPE, account.type)

        // Email address for scheduling. Used by the calendar provider to determine whether the
        // user is ORGANIZER/ATTENDEE for a certain event.
        values.put(Calendars.OWNER_ACCOUNT, account.name)

        // flag as visible & syncable at creation, might be changed by user at any time
        values.put(Calendars.VISIBLE, 1)
        values.put(Calendars.SYNC_EVENTS, 1)

        logger.log(Level.INFO, "Adding local calendar", values)
        val uri = AndroidCalendar.create(account, provider, values)
        return AndroidCalendar.findByID(account, provider, LocalCalendar.Factory, ContentUris.parseId(uri))
    }


    override fun getAll(account: Account, provider: ContentProviderClient) =
        AndroidCalendar.find(account, provider, LocalCalendar.Factory, "${Calendars.SYNC_EVENTS}!=0", null)


    override fun update(provider: ContentProviderClient, localCollection: LocalCalendar, fromCollection: Collection) {
        val accountSettings = accountSettingsFactory.create(localCollection.account)
        val values = valuesFromCollectionInfo(fromCollection, withColor = accountSettings.getManageCalendarColors())

        logger.log(Level.FINE, "Updating local calendar ${fromCollection.url}", values)
        localCollection.update(values)
    }

    private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
        val values = ContentValues()
        values.put(Calendars.NAME, info.url.toString())
        values.put(Calendars.CALENDAR_DISPLAY_NAME,
            if (info.displayName.isNullOrBlank()) info.url.lastSegment else info.displayName)

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

        // add base values for Calendars
        values.putAll(calendarBaseValues)

        return values
    }


    override fun delete(localCollection: LocalCalendar) {
        logger.log(Level.INFO, "Deleting local calendar", localCollection)
        localCollection.delete()
    }

}