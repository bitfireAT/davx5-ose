/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.EventsEntity
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import org.jetbrains.annotations.TestOnly
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Represents a locally stored calendar containing events, each represented by an [Entity]. Communicates with
 * the Android calendar provider which uses an SQLite database to store the events.
 *
 * Methods that use [ContentValues] operate directly on rows of the [Events] table.
 * Methods that use [Entity] operate on [EventsEntity] URIs to access the [Events] rows together with
 * associated data rows (reminders, attendees etc.)
 *
 * @param client    calendar provider
 * @param values    content values as read from the calendar provider; [android.provider.BaseColumns._ID] must be set
 *
 * @throws IllegalArgumentException when [Calendars._ID] is not set
 */
class AndroidCalendar(
    internal val provider: AndroidCalendarProvider,
    internal val values: ContentValues
) {

    val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /** see [Calendars._ID] */
    val id: Long = values.getAsLong(Calendars._ID)
        ?: throw IllegalArgumentException("Calendars._ID must be available")


    // data fields

    /** see [Calendars.CALENDAR_ACCESS_LEVEL] */
    val accessLevel: Int
        get() = values.getAsInteger(Calendars.CALENDAR_ACCESS_LEVEL) ?: 0

    /** see [Calendars.CALENDAR_DISPLAY_NAME] */
    val displayName: String?
        get() = values.getAsString(Calendars.CALENDAR_DISPLAY_NAME)

    /** see [Calendars.NAME] */
    val name: String?
        get() = values.getAsString(Calendars.NAME)

    /** see [Calendars.OWNER_ACCOUNT] */
    val ownerAccount: String?
        get() = values.getAsString(Calendars.OWNER_ACCOUNT)

    /** see [Calendars._SYNC_ID] */
    val syncId: String?
        get() = values.getAsString(Calendars._SYNC_ID)


    // CRUD events

    /**
     * Inserts an event to the calendar provider.
     *
     * @param entity    event to insert
     *
     * @return ID of the new event
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun addEvent(entity: Entity): Long {
        try {
            val batch = CalendarBatchOperation(client)
            addEvent(entity, batch)
            batch.commit()

            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert event", e)
        }
    }

    fun addEvent(entity: Entity, batch: CalendarBatchOperation) {
        // insert event row
        val eventRowIdx = batch.nextBackrefIdx()
        batch += CpoBuilder.newInsert(eventsUri).withValues(entity.entityValues)

        // insert data rows (with reference to event row ID)
        for (row in entity.subValues)
            batch += CpoBuilder.newInsert(row.uri.asSyncAdapter(account))
                .withValues(row.values)
                .withValueBackReference(EventsContract.DATA_ROW_EVENT_ID, eventRowIdx)
    }

    /**
     * Counts the number of events in this calendar that match the given selection criteria.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of events matching the selection criteria.
     * @throws LocalStorageException when the content provider returns an error
     */
    fun countEvents(where: String?, whereArgs: Array<String>?): Int {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, arrayOf(Events._ID),
                protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                return cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't count events", e)
        }
        // If the query was invalid, an exception should have been thrown. So this should never be reached:
        return 0
    }

    /**
     * Gets the first event from this calendar that matches the given query.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     * @param sortOrder sort oder
     *
     * @return first event from this calendar that matches the selection, or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEvent(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): Entity? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, sortOrder)?.use { cursor ->
                val iter = EventsEntity.newEntityIterator(cursor, client)
                if (iter.hasNext())
                    return iter.next()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query events", e)
        }
        return null
    }

    /**
     * Queries events from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return events from this calendar which match the selection
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEvents(where: String?, whereArgs: Array<String>?): List<Entity> {
        val entities = LinkedList<Entity>()
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                for (entity in EventsEntity.newEntityIterator(cursor, client))
                    entities += entity
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query events", e)
        }
        return entities
    }

    /**
     * Gets the first event row that matches the given query.
     *
     * @return first event row that matches [where]/[whereArgs] (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEventRow(projection: Array<String>?, where: String?, whereArgs: Array<String>?): ContentValues? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event rows", e)
        }
        return null
    }

    /**
     * Gets a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     *
     * @return event (or `null` if not found)
     */
    fun getEvent(id: Long, where: String? = null, whereArgs: Array<String>? = null): Entity? {
        try {
            client.query(eventEntityUri(id), null, where, whereArgs, null)?.use { cursor ->
                val iterator = EventsEntity.newEntityIterator(cursor, client)
                if (iterator.hasNext())
                    return iterator.next()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event entity", e)
        }
        return null
    }

    /**
     * Gets the event row of a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     *
     * @return event row (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getEventRow(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): ContentValues? {
        try {
            client.query(eventUri(id), projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event row", e)
        }
        return null
    }

    /**
     * Iterates event rows from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each main row
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateEventRows(projection: Array<String>?, where: String?, whereArgs: Array<String>?, body: (ContentValues) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    body(cursor.toContentValues())
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate event rows", e)
        }
    }

    /**
     * Iterates event entities from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each entity
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateEvents(where: String?, whereArgs: Array<String>?, body: (Entity) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                val iterator = EventsEntity.newEntityIterator(cursor, client)
                for (entity in iterator)
                    body(entity)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate events", e)
        }
    }

    /**
     * Updates a specific event's main row with the given values. Doesn't influence data rows.
     *
     * This method always uses the update method of the content provider and does not
     * re-create rows, as it is required for some operations (see [updateEvent] and [getStatusUpdateWorkaround]
     * for more information).
     *
     * @param id        event ID
     * @param values    new values
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEventRow(id: Long, values: ContentValues) {
        try {
            client.update(eventUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event row $id", e)
        }
    }

    /**
     * Updates a specific event's main row with the given values. Doesn't influence data rows.
     *
     * This method always uses the update method of the content provider and does not
     * re-create rows, as it is required for some operations (see [updateEvent] and [getStatusUpdateWorkaround]
     * for more information).
     *
     * @param id        event ID
     * @param values    new values
     * @param batch     batch operation in which the update is enqueued
     */
    fun updateEventRow(id: Long, values: ContentValues, batch: CalendarBatchOperation) {
        batch += CpoBuilder.newUpdate(eventUri(id))
            .withValues(values)
    }

    /**
     * Updates an event and applies the eventStatus=null workaround, if necessary.
     *
     * While the event row can be updated, sub-values (data rows) are always deleted and created from scratch.
     *
     * @param id        ID of the event to update
     * @param entity    new values of the event
     *
     * @return ID of the updated event (not necessarily the same as the original event)
     */
    fun updateEvent(id: Long, entity: Entity): Long {
        try {
            val batch = CalendarBatchOperation(client)
            val newEventIdIdx = updateEvent(id, entity, batch)
            batch.commit()

            if (newEventIdIdx == null)
                // event was updated
                return id
            else {
                // event was re-built
                val result = batch.getResult(newEventIdIdx)
                val newEventUri = result?.uri ?: throw LocalStorageException("Content provider returned null on insert")
                return ContentUris.parseId(newEventUri)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event $id", e)
        }
    }

    /**
     * Enqueues an update of an event and applies the eventStatus=null workaround, if necessary.
     *
     * While the event row can be updated, sub-values (data rows) are always deleted and created from scratch.
     *
     * @param id        ID of the event to update
     * @param entity    new values of the event
     * @param batch     batch operation in which the update is enqueued
     *
     * @return `null` if an event update was enqueued so that its ID won't change;
     * otherwise (if re-build is needed) the result index of the new event ID.
     */
    fun updateEvent(id: Long, entity: Entity, batch: CalendarBatchOperation): Int? {
        val workaround = getStatusUpdateWorkaround(id, entity.entityValues)
        if (workaround == StatusUpdateWorkaround.REBUILD_EVENT) {
            deleteEvent(id, batch)

            val idx = batch.nextBackrefIdx()
            addEvent(entity, batch)
            return idx
        }

        // remove existing data rows which are created by us (don't touch 3rd-party calendar apps rows)
        deleteDataRows(id, batch)

        // update main row
        val newValues = ContentValues(entity.entityValues).apply {
            // don't update event ID
            remove(Events._ID)

            // don't update status if that is our required workaround
            if (workaround == StatusUpdateWorkaround.DONT_UPDATE_STATUS)
                remove(Events.STATUS)
        }
        batch += CpoBuilder.newUpdate(eventUri(id))
            .withValues(newValues)

        // insert data rows (with reference to main row ID)
        for (row in entity.subValues)
            batch += CpoBuilder.newInsert(row.uri.asSyncAdapter(account))
                .withValues(ContentValues(row.values).apply {
                    put(EventsContract.DATA_ROW_EVENT_ID, id)      // always keep reference to main row ID
                })

        return null
    }

    /**
     * Deletes data rows from events, but only those with a known CONTENT_URI that we are also able to
     * build. This should prevent accidental deletion of unknown data rows like they may be used by calendar
     * apps to for instance tag events in the UI.
     */
    private fun deleteDataRows(eventId: Long, batch: CalendarBatchOperation) {
        batch += CpoBuilder
            .newDelete(Reminders.CONTENT_URI.asSyncAdapter(account))
            .withSelection("${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        batch += CpoBuilder
            .newDelete(Attendees.CONTENT_URI.asSyncAdapter(account))
            .withSelection("${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()))
        batch += CpoBuilder
            .newDelete(ExtendedProperties.CONTENT_URI.asSyncAdapter(account))
            .withSelection(
                "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?,?,?)",
                arrayOf(
                    eventId.toString(),
                    EventsContract.EXTNAME_CATEGORIES,
                    EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,       // UID is stored in UID_2445, don't leave iCalUid rows in events that we have written
                    EventsContract.EXTNAME_URL,
                    UnknownProperty.CONTENT_ITEM_TYPE
                )
            )
    }

    /**
     * There is a bug in the calendar provider that prevent events from being updated from a non-null STATUS value
     * to STATUS=null (see `AndroidCalendarProviderBehaviorTest` test class).
     *
     * In that case we can't update the event, so we completely re-create it.
     *
     * @param id            event of existing ID
     * @param newValues     new values that the event shall be updated to
     *
     * @return whether the event can't be updated/needs to be re-created; or `null` if existing values couldn't be determined
     */
    @VisibleForTesting
    internal fun getStatusUpdateWorkaround(id: Long, newValues: ContentValues): StatusUpdateWorkaround {
        // No workaround needed if STATUS is a) not updated at all, or b) updated to a non-null value.
        if (!newValues.containsKey(Events.STATUS) || newValues.getAsInteger(Events.STATUS) != null)
            return StatusUpdateWorkaround.NO_WORKAROUND
        // We're now sure that STATUS shall be updated to null.

        // If STATUS is null before the update, just don't include the STATUS in the update.
        // In case that the old values can't be determined, rebuild the row to be on the safe side.
        val existingValues = getEventRow(id, arrayOf(Events.STATUS)) ?: return StatusUpdateWorkaround.REBUILD_EVENT
        if (existingValues.getAsInteger(Events.STATUS) == null)
            return StatusUpdateWorkaround.DONT_UPDATE_STATUS

        // Update from non-null to null → rebuild (delete/insert) event instead of updating it.
        return StatusUpdateWorkaround.REBUILD_EVENT
    }

    /**
     * Updates event rows in this calendar.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEventRows(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.update(eventsUri, values, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update events", e)
        }

    /**
     * Deletes all events of this calendar from the local storage.
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    @TestOnly
    fun deleteAllEvents() {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(null, null)
            client.delete(eventsUri, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete all events from calendar", e)
        }
    }

    /**
     * Deletes an event row.
     *
     * The content provider automatically deletes associated data rows, but doesn't touch exceptions.
     *
     * @param id    ID of the event
     */
    fun deleteEvent(id: Long) {
        try {
            client.delete(eventUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete event $id", e)
        }
    }

    internal fun deleteEvent(id: Long, batch: CalendarBatchOperation) {
        batch += CpoBuilder.newDelete(eventUri(id))
    }


    // Event instances (these methods operate directly with event IDs without additional logic
    // and thus belong to the calendar class, not to AndroidRecurringCalendar)

    /**
     * Queries the [Instances.CONTENT_URI] to finds the number of instances of the given event,
     * excluding deleted and canceled exceptions.
     *
     * _Note:_ The corresponding calendar must have `SYNC_EVENTS=1`, otherwise the
     * calendar provider won't expand the instances (see `CalendarInstancesHelper.getEntries` /
     * `CalendarInstancesHelper.SQL_WHERE_GET_EVENTS_ENTRIES`).
     *
     * @param eventId Event ID to query number of instances for
     * @param checkSyncEvents If true, verifies that [Calendars.SYNC_EVENTS] is set in [values]
     * before querying the instances
     *
     * @return number of event instances (not counting deleted and canceled exceptions); *null* if
     * [Calendars.SYNC_EVENTS] is not set, or the number can't be determined, or the event has no last date
     * (recurring event without last instance)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun numInstances(eventId: Long, checkSyncEvents: Boolean = true): Int? {
        if (checkSyncEvents) {
            /* Check that the calendar has SYNC_EVENTS set. If it is not, the Instances query won't
            expand instances and may return 0 although there are instances. */
            val syncEvents = values.getAsInteger(Calendars.SYNC_EVENTS)
            if (syncEvents == null || syncEvents <= 0)
                return null
        }

        /* Query first and last instance of the event. If the event doesn't have a last occurrence,
        it's either endless (and always has instances) or we can't determine the number of instances. */
        val (firstTs, lastTs) = getFirstAndLastTimestamp(eventId)
        if (firstTs == null || lastTs == null)
            return null

        /* When the calendar provider receives an instances query, it
        *
        * 1. expands the instances of all events for the requested window into the Instances table
        *   (but only if Calendars.SYNC_EVENTS=1, and it ignores instances of deleted/canceled exceptions),
        * 2. and then queries the Instances table with the given query.
        *
        * So we request only the time window of the actual event to avoid unnecessary instance expansion. */
        val instancesUri = Instances.CONTENT_URI.asSyncAdapter(account)
            .buildUpon()
            .appendPath(firstTs.toString())   // begin timestamp
            .appendPath(lastTs.toString())    // end timestamp
            .build()

        // We're interested in instances of the original event, but also of exceptions.
        val safeEventIdsSql = getExceptionIds(eventId).joinToString(",")
        logger.fine("Querying instances between $firstTs and $lastTs and filtering for event IDs: $safeEventIdsSql")

        var numInstances: Int? = null
        try {
            client.query(
                instancesUri, arrayOf(Instances._ID),
                // SQL injection not possible because safeEventIdsSql is built from numeric IDs
                "${Instances.EVENT_ID} IN ($safeEventIdsSql)", null,
                null
            )?.use { cursor ->
                numInstances = cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query number of instances for event $eventId", e)
        }
        return numInstances
    }

    /**
     * Retrieves the first and last timestamps of an event with the given event ID.
     *
     * @param eventId The ID of the event for which to retrieve the first and last timestamps.
     * @return A Pair containing 1. the first timestamp (or null if not available) and 2.
     * the last timestamp (or null if not available or if the event is endless).
     */
    private fun getFirstAndLastTimestamp(eventId: Long): Pair<Long?, Long?> {
        var first: Long? = null
        var last: Long? = null
        getEventRow(eventId, arrayOf(Events.DTSTART, Events.RRULE, Events.LAST_DATE))?.let { values ->
            first = values.getAsLong(Events.DTSTART)
            last = values.getAsLong(Events.LAST_DATE)
        }
        return first to last
    }

    private fun getExceptionIds(eventId: Long): List<Long> {
        val result = mutableSetOf(eventId)
        iterateEventRows(
            arrayOf(Events._ID),
            "${Events.ORIGINAL_ID}=?", arrayOf(eventId.toString())
        ) { exception ->
            result += exception.getAsLong(Events._ID)
        }
        return result.toList()
    }

    /**
     * Marks dirty events
     *
     * - which are not already marked as deleted AND
     * - which don't have any instances
     *
     * as deleted.
     */
    fun deleteDirtyEventsWithoutInstances() {
        val batch = CalendarBatchOperation(client)

        // Iterate dirty main events without exceptions
        iterateEventRows(
            arrayOf(Events._ID),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",
            null
        ) { values ->
            val eventId = values.getAsLong(Events._ID)

            // get number of instances
            val numEventInstances = numInstances(eventId)

            // delete event if there are no instances
            if (numEventInstances == 0) {
                logger.warning("Marking event #$eventId without instances as deleted")
                updateEventRow(eventId, contentValuesOf(Events.DELETED to 1), batch)
            }
        }

        batch.commit()
    }


    // shortcuts to upper level

    /** Calls [AndroidCalendarProvider.deleteCalendar] for this calendar. */
    fun delete() = provider.deleteCalendar(id)

    /**
     * Calls [AndroidCalendarProvider.updateCalendar] for this calendar and
     * merges [newValues] into the cached [values].
     * */
    fun update(newValues: ContentValues) {
        provider.updateCalendar(id, newValues)
        values.putAll(newValues)
    }

    /** Calls [AndroidCalendarProvider.readCalendarSyncState] for this calendar. */
    fun readSyncState() = provider.readCalendarSyncState(id)

    /** Calls [AndroidCalendarProvider.writeCalendarSyncState] for this calendar. */
    fun writeSyncState(newState: String?) {
        provider.writeCalendarSyncState(id, newState)
    }


    // helpers

    enum class StatusUpdateWorkaround {
        /** no workaround needed */
        NO_WORKAROUND,
        /** don't update eventStatus (no need to change value) */
        DONT_UPDATE_STATUS,
        /** rebuild event (delete+insert instead of update) */
        REBUILD_EVENT
    }

    val account
        get() = provider.account

    val client
        get() = provider.client

    val eventsUri
        get() = Events.CONTENT_URI.asSyncAdapter(account)

    fun eventUri(id: Long) =
        ContentUris.withAppendedId(eventsUri, id)

    val eventEntitiesUri
        get() = EventsEntity.CONTENT_URI.asSyncAdapter(account)

    fun eventEntityUri(id: Long) =
        ContentUris.withAppendedId(eventEntitiesUri, id)

    /**
     * Restricts a given selection/where clause to this calendar ID.
     *
     * @param where      selection
     * @param whereArgs  arguments for selection
     * @return           restricted selection and arguments
     */
    private fun whereWithCalendarId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND " + Events.CALENDAR_ID + "=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}