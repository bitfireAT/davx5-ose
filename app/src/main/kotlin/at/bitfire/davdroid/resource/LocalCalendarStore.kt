/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Calendars
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalCalendar.Companion.valuesFromCollectionInfo
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class LocalCalendarStore @Inject constructor(
    @ApplicationContext val context: Context,
    val accountSettingsFactory: AccountSettings.Factory,
    db: AppDatabase,
    val logger: Logger
): LocalDataStore<LocalCalendar> {

    private val serviceDao = db.serviceDao()


    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalCalendar? {
        val service = serviceDao.get(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        // If the collection doesn't have a color, use a default color.
        if (fromCollection.color != null)
            fromCollection.color = Constants.DAVDROID_GREEN_RGBA

        val values = valuesFromCollectionInfo(fromCollection, withColor = true)

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
        logger.log(Level.FINE, "Updating local calendar ${fromCollection.url}", fromCollection)
        val accountSettings = accountSettingsFactory.create(localCollection.account)
        localCollection.update(fromCollection, accountSettings.getManageCalendarColors())
    }

    override fun delete(localCollection: LocalCalendar) {
        logger.log(Level.INFO, "Deleting local calendar", localCollection)
        localCollection.delete()
    }

}