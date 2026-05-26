/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentResolver
import android.provider.CalendarContract
import at.bitfire.synctools.storage.calendar.EventsContract.CATEGORIES_SEPARATOR

/**
 * How this library uses some Android event sync columns and data rows.
 */
object EventsContract {

    // event rows

    /**
     * Custom sync column to store the last known ETag of an event.
     *
     * Type: [String]
     */
    const val COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1

    /**
     * Custom sync column to store sync flags of an event.
     *
     * Type: [Int]
     */
    const val COLUMN_FLAGS = CalendarContract.Events.SYNC_DATA2

    /**
     * Custom sync column to store the SEQUENCE of an event.
     *
     * Type: [Int]
     */
    const val COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3

    /**
     * Custom sync column to store the Schedule-Tag of an event.
     *
     * Type: [String]
     */
    const val COLUMN_SCHEDULE_TAG = CalendarContract.Events.SYNC_DATA4


    // data rows

    /**
     * Name of the data row field that references the main row ID.
     *
     * Equals to [CalendarContract.AttendeesColumns.EVENT_ID], [CalendarContract.RemindersColumns.EVENT_ID] etc.
     */
    const val DATA_ROW_EVENT_ID = "event_id"

    /**
     * VEVENT CATEGORIES are stored as an extended property with this [CalendarContract.ExtendedPropertiesColumns.NAME].
     *
     * The [CalendarContract.ExtendedPropertiesColumns.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
     * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
     * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
     *
     * Example: `Cat1\Cat2`
     */
    const val EXTNAME_CATEGORIES = "categories"
    const val CATEGORIES_SEPARATOR = '\\'

    /**
     * Google Calendar uses an extended property called `iCalUid` for storing the event's UID, instead of the
     * standard [CalendarContract.EventsColumns.UID_2445].
     *
     * See also: https://github.com/bitfireAT/ical4android/issues/125
     */
    const val EXTNAME_GOOGLE_CALENDAR_UID = "iCalUid"

    /**
     * VEVENT URL is stored as an extended property with this [CalendarContract.ExtendedPropertiesColumns.NAME].
     * The URL is directly put into [CalendarContract.ExtendedPropertiesColumns.VALUE].
     */
    const val EXTNAME_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"

}