/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider.Companion.COLUMN_CALENDAR_SYNC_STATE
import at.bitfire.synctools.storage.toContentValues
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages locally stored calendars (represented by [AndroidCalendar]) in the
 * Android calendar provider.
 *
 * @param account   Account that all operations are bound to
 * @param client    content provider client
 */
class AndroidCalendarProvider(
    val account: Account,
    internal val client: ContentProviderClient
) {

    private val logger = Logger.getLogger(javaClass.name)


    // AndroidCalendar CRUD

    /**
     * Creates a new calendar.
     *
     * @param values    values to create the calendar from (account name and type are inserted)
     * @return calendar ID of the newly created calendar
     * @throws LocalStorageException when the content provider returns nothing or an error
     */
    fun createCalendar(values: ContentValues): Long {
        logger.log(Level.FINE, "Creating local calendar", values)

        values.put(Calendars.ACCOUNT_NAME, account.name)
        values.put(Calendars.ACCOUNT_TYPE, account.type)

        val uri =
            try {
                client.insert(calendarsUri, values)
            } catch (e: RemoteException) {
                throw LocalStorageException("Couldn't create calendar", e)
            }
        if (uri == null)
            throw LocalStorageException("Couldn't create calendar")
        return ContentUris.parseId(uri)
    }

    /**
     * Creates a new calendar and directly returns it.
     *
     * @param values    values to create the calendar from (account name and type are inserted)
     *
     * @return the created calendar
     * @throws LocalStorageException when the content provider returns nothing or an error
     */
    fun createAndGetCalendar(values: ContentValues): AndroidCalendar {
        val id = createCalendar(values)
        return getCalendar(id) ?: throw LocalStorageException("Couldn't query calendar that was just created")
    }

    /**
     * Queries existing calendars.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     *
     * @return list of calendars
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findCalendars(where: String? = null, whereArgs: Array<String>? = null, sortOrder: String? = null): List<AndroidCalendar> {
        val result = LinkedList<AndroidCalendar>()
        try {
            client.query(calendarsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext())
                    result += AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendars", e)
        }
        return result
    }

    /**
     * Queries existing calendars and returns the first calendar that matches the search criteria.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     *
     * @return first calendar that matches the search criteria (or `null`)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findFirstCalendar(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): AndroidCalendar? {
        try {
            client.query(calendarsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendars", e)
        }
        return null
    }

    /**
     * Gets an existing calendar by its ID.
     *
     * @param id    calendar ID
     *
     * @return calendar (or `null` if not found)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getCalendar(id: Long): AndroidCalendar? {
        try {
            client.query(calendarUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendar", e)
        }
        return null
    }

    /**
     * Updates an existing calendar.
     *
     * @param id        calendar ID
     * @param values    values to update
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateCalendar(id: Long, values: ContentValues, where: String? = null, whereArgs: Array<String>? = null): Int {
        logger.log(Level.FINE, "Updating local calendar #$id", values)
        try {
            return client.update(calendarUri(id), values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update calendar", e)
        }
    }

    /**
     * Deletes an existing calendar.
     *
     * @param id    calendar ID
     *
     * @return number of deleted rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun deleteCalendar(id: Long): Int {
        logger.fine("Deleting local calendar #$id")
        try {
            return client.delete(calendarUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete calendar", e)
        }
    }


    // other methods: sync state, event colors

    /**
     * Reads the calendar sync state ([COLUMN_CALENDAR_SYNC_STATE] field).
     *
     * _Note: This is not to be confused with the totally unrelated [CalendarContract.SyncState]
     * (which is per account, not per calendar)._
     *
     * @param id    calendar ID
     * @return sync state (or `null` if not set)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun readCalendarSyncState(id: Long): String? =
        try {
            client.query(calendarUri(id), arrayOf(COLUMN_CALENDAR_SYNC_STATE), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.getString(0)
                else
                    null
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendar sync state", e)
        }

    /**
     * Writes the calendar sync state ([COLUMN_CALENDAR_SYNC_STATE] field).
     *
     * _Note: This is not to be confused with the totally unrelated [CalendarContract.SyncState]
     * (which is per account, not per calendar)._
     *
     * @param id    calendar ID
     * @param state sync state (may be `null`)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun writeCalendarSyncState(id: Long, state: String?) {
        updateCalendar(id, contentValuesOf(COLUMN_CALENDAR_SYNC_STATE to state))
    }

    /**
     * Inserts all possible [Css3Color] values into the color index table of the account. This means that
     *
     * - calendar apps can allow users to select an event color from the possible values;
     * - when [CalendarContract.Events.CALENDAR_COLOR_KEY] of an event is set to a CSS3 color name, the
     *   [CalendarContract.Events.CALENDAR_COLOR] will be completed by the calendar provider.
     *
     * This method does a quick check whether the current number of colors is the same as the number
     * of possible [Css3Color] values, in which case nothing will be inserted.
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun provideCss3ColorIndices() {
        client.query(colorsUri, arrayOf(Colors.COLOR_KEY), null, null, null)?.use { cursor ->
            if (cursor.count == Css3Color.entries.size)
                // colors already inserted and up to date
                return
        }

        logger.fine("Inserting CSS3 colors to account $account")
        try {
            client.bulkInsert(
                colorsUri,
                Css3Color.entries.map { color ->
                    contentValuesOf(
                        Colors.ACCOUNT_NAME to account.name,
                        Colors.ACCOUNT_TYPE to account.type,
                        Colors.COLOR_TYPE to Colors.TYPE_EVENT,
                        Colors.COLOR_KEY to color.name,
                        Colors.COLOR to color.argb
                    )
                }.toTypedArray()
            )
        } catch(e: RemoteException) {
            throw LocalStorageException("Couldn't insert CSS3 colors", e)
        }
    }

    /**
     * Unassigns colors from all events in this account and removes all entries from the color index table of the account.
     */
    fun removeColorIndices() {
        logger.fine("Removing CSS3 colors from account $account")

        // unassign colors from events
        /* ANDROID STRANGENESS:
           1) updating Events.CONTENT_URI affects events of all accounts, not just the selected one
           2) account_type and account_name can't be specified in selection (causes SQLiteException)
           WORKAROUND: unassign event colors for each calendar
        */
        try {
            client.query(calendarsUri, arrayOf(Calendars._ID), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(0)

                    val values = ContentValues(1)
                    values.putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                    client.update(
                        CalendarContract.Events.CONTENT_URI.asSyncAdapter(account), values,
                        "${CalendarContract.Events.EVENT_COLOR_KEY} IS NOT NULL AND ${CalendarContract.Events.CALENDAR_ID}=?", arrayOf(calendarId.toString())
                    )
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't unassign event colors", e)
        }

        // remove entries from color table
        client.delete(colorsUri, null, null)
    }


    // helpers

    val calendarsUri
        get() = Calendars.CONTENT_URI.asSyncAdapter(account)

    fun calendarUri(id: Long) =
        ContentUris.withAppendedId(calendarsUri, id)

    @VisibleForTesting
    internal val colorsUri
        get() = Colors.CONTENT_URI.asSyncAdapter(account)


    companion object {

        /**
         * Column to store per-calendar sync state as a [String].
         *
         * _Note: This is not to be confused with the totally unrelated [CalendarContract.SyncState]
         * (which is per account, not per calendar)._
         */
        const val COLUMN_CALENDAR_SYNC_STATE = Calendars.CAL_SYNC1

        /**
         * Not all calendar provider versions support recurring events up to the year 2074.
         *
         * With Android 12, the calendar provider added support for
         *
         * - events up to 2074,
         * - matching recurring event exceptions with millisecond resolution.
         */
        private val hasTimeApiFixes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val matchesExceptionsWithMilliseconds get() = hasTimeApiFixes
        val supportsYear2074 get() = hasTimeApiFixes

    }

}