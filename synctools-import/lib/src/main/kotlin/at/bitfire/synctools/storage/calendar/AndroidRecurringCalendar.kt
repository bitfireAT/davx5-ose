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
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.containsNotNull
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Adds support for [EventAndExceptions] data objects to [AndroidCalendar].
 *
 * There are basically two methods for inserting an exception event:
 *
 * 1. Insert it using [Events.CONTENT_EXCEPTION_URI] – then the calendar provider will take care
 * of validating and cleaning up various fields, however it's then not possible to set some
 * sync fields (all sync fields but [Events.SYNC_DATA1], [Events.SYNC_DATA3] and [Events.SYNC_DATA7] are filtered
 * for some reason.) It also supports splitting the main event ("exception from this date"). Usually this method
 * is used by calendar apps.
 * 2. Insert it directly as normal event (using [Events.CONTENT_URI]). In this case [Events.ORIGINAL_SYNC_ID]
 * must be set to the [Events._SYNC_ID] of the original event so that the calendar provider can associate the
 * exception with the main event. It's not enough to set [Events.ORIGINAL_ID]!
 *
 * This class only uses the second method because it needs to support all sync fields.
 */
class AndroidRecurringCalendar(
    val calendar: AndroidCalendar
) {

    val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Inserts an event and all its exceptions. Input data is first cleaned up using [cleanUp].
     *
     * If you want to insert exceptions, [Events._SYNC_ID] must be set on the main
     * event and [Events.ORIGINAL_SYNC_ID] should be set to the same value for the
     * exception events. **It's not enough to just set [Events.ORIGINAL_ID] in the
     * exceptions**.
     *
     * @param eventAndExceptions    event and exceptions to insert
     *
     * @return ID of the resulting main event
     */
    fun addEventAndExceptions(eventAndExceptions: EventAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(eventAndExceptions)

            // add main event
            val batch = CalendarBatchOperation(calendar.client)
            calendar.addEvent(cleaned.main, batch)

            // add exceptions
            for (exception in cleaned.exceptions)
                calendar.addEvent(exception, batch)

            batch.commit()

            // main event was created as first row (index 0), return its insert result (= ID)
            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert event/exceptions", e)
        }
    }

    /**
     * Find first event (including exceptions) that matches the query from the content provider.
     *
     * Note that the exceptions may contain deleted events.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     */
    fun findEventAndExceptions(where: String?, whereArgs: Array<String>?): EventAndExceptions? {
        val main = calendar.findEvent(where, whereArgs) ?: return null

        // attach exceptions
        val mainEventId = main.entityValues.getAsLong(Events._ID)
        return EventAndExceptions(
            main = main,
            exceptions = calendar.findEvents("${Events.ORIGINAL_ID}=?", arrayOf(mainEventId.toString()))
        )
    }

    /**
     * Retrieves an event and its exceptions from the content provider (associated by [Events.ORIGINAL_ID]).
     *
     * @param mainEventId   [Events._ID] of the main event
     *
     * @return event and exceptions
     */
    fun getById(mainEventId: Long): EventAndExceptions? {
        val mainEvent = calendar.getEvent(mainEventId) ?: return null
        return EventAndExceptions(
            main = mainEvent,
            exceptions = calendar.findEvents("${Events.ORIGINAL_ID}=?", arrayOf(mainEventId.toString()))
        )
    }

    /**
     * Iterates through events together with their exceptions from the content provider.
     *
     * Note that the exceptions may contain deleted events.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each event (including exceptions)
     */
    fun iterateEventAndExceptions(where: String?, whereArgs: Array<String>?, body: (EventAndExceptions) -> Unit) {
        // iterate through main events and attach exceptions
        calendar.iterateEvents(where, whereArgs) { main ->
            val mainEventId = main.entityValues.getAsLong(Events._ID)
            body(EventAndExceptions(
                main = main,
                exceptions = calendar.findEvents("${Events.ORIGINAL_ID}=?", arrayOf(mainEventId.toString()))
            ))
        }
    }

    /**
     * Updates an event and all its exceptions. Input data is first cleaned up using
     * [cleanMainEvent] and [cleanException].
     *
     * @param id                    ID of the main event row
     * @param eventAndExceptions    new event (including exceptions)
     *
     * @return main event ID of the updated row (may be different than [id] when the event had to be re-created)
     */
    fun updateEventAndExceptions(id: Long, eventAndExceptions: EventAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(eventAndExceptions)

            // remove old exceptions (because they may be invalid for the updated event)
            val batch = CalendarBatchOperation(calendar.client)
            batch += CpoBuilder.newDelete(calendar.eventsUri)
                .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(id.toString()))

            // update main event (also applies eventStatus workaround, if needed)
            val newEventIdIdx = calendar.updateEvent(id, cleaned.main, batch)

            // add updated exceptions
            for (exception in cleaned.exceptions)
                calendar.addEvent(exception, batch)

            batch.commit()

            if (newEventIdIdx == null) {
                // original row was updated, so return original ID
                return id
            } else {
                // event was re-built
                val result = batch.getResult(newEventIdIdx)
                val newEventUri = result?.uri ?: throw LocalStorageException("Content provider returned null on insert")
                return ContentUris.parseId(newEventUri)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event/exceptions", e)
        }
    }

    /**
     * Deletes an event and all its potential exceptions.
     *
     * @param id    ID of the event
     */
    fun deleteEventAndExceptions(id: Long) {
        try {
            val batch = CalendarBatchOperation(calendar.client)

            // delete main event
            batch += CpoBuilder.newDelete(calendar.eventUri(id))

            // delete exceptions, too (not automatically done by provider)
            batch += CpoBuilder
                .newDelete(calendar.eventsUri)
                .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(id.toString()))

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete event $id", e)
        }
    }


    // validation / clean-up logic

    /**
     * Prepares an event and exceptions so that it can be inserted into the calendar provider:
     *
     * - If the main event is not recurring or doesn't have a [Events._SYNC_ID], exceptions are ignored.
     * - Cleans up the main event with [cleanMainEvent].
     * - Cleans up exceptions with [cleanException].
     *
     * @param original  original event and exceptions
     *
     * @return event and exceptions that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanUp(original: EventAndExceptions): EventAndExceptions {
        val main = cleanMainEvent(original.main)

        val mainValues = main.entityValues
        val syncId = mainValues.getAsString(Events._SYNC_ID)
        val recurring = mainValues.containsNotNull(Events.RRULE) || mainValues.containsNotNull(Events.RDATE)

        if (syncId == null || !recurring) {
            // 1. main event doesn't have sync id → exceptions wouldn't be associated to main event by calendar provider, so ignore them
            // 2. main event not recurring → exceptions are useless, ignore them

            if (original.exceptions.isNotEmpty())
                logger.log(Level.WARNING, "Dropping exceptions of event because event is not recurring or _SYNC_ID is not set", main)

            return EventAndExceptions(main = main, exceptions = emptyList())
        }

        return EventAndExceptions(
            main = main,
            exceptions = original.exceptions.map { originalException ->
                cleanException(originalException, syncId)
            }
        )
    }

    /**
     * Prepares a main event for insertion into the calendar provider by making sure it
     * doesn't have fields that a main event shouldn't have (original_...).
     *
     * @param original  original event to insert
     *
     * @return cleaned event that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanMainEvent(original: Entity): Entity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entityValues)

        // remove values that a main event shouldn't have
        val originalFields = arrayOf(
            Events.ORIGINAL_ID, Events.ORIGINAL_SYNC_ID,
            Events.ORIGINAL_INSTANCE_TIME, Events.ORIGINAL_ALL_DAY
        )
        for (field in originalFields)
            values.remove(field)

        // create new result with subvalues
        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }

    /**
     * Prepares an exception for insertion into the calendar provider:
     *
     * - Removes values that an exception shouldn't have (`RRULE`, `RDATE`, `EXRULE`, `EXDATE`).
     * - Makes sure that the `ORIGINAL_SYNC_ID` is set to [syncId].
     *
     * @param original  original exception
     * @param syncId    [Events._SYNC_ID] of the main event
     *
     * @return cleaned exception that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanException(original: Entity, syncId: String): Entity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entityValues)

        // remove values that an exception shouldn't have
        val recurrenceFields = arrayOf(Events.RRULE, Events.RDATE, Events.EXRULE, Events.EXDATE)
        for (field in recurrenceFields)
            values.remove(field)

        // make sure that ORIGINAL_SYNC_ID is set so that the exception can be associated to the main event
        values.put(Events.ORIGINAL_SYNC_ID, syncId)

        // create new result with subvalues
        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }


    // helpers for dirty/deleted events and exceptions

    /**
     * Iterates through all exceptions in [calendar] that are marked as deleted.
     * For every found exception:
     *
     * - the SEQUENCE field of the main event is increased by one,
     * - the main event is marked as dirty (so that it will be synced),
     * - and then the exception is actually deleted (so that it won't show up anymore during sync).
     */
    fun processDeletedExceptions() {
        val batch = CalendarBatchOperation(calendar.client)

        // iterate through deleted exceptions
        calendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID),
            "${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Events._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(Events.ORIGINAL_ID)       // can't be null (by query)
            logger.fine("Found deleted exception #$exceptionId, removing it and marking original event #$mainId as dirty")

            // main event: get current sequence
            val mainValues = calendar.getEventRow(mainId, arrayOf(EventsContract.COLUMN_SEQUENCE))
            val mainSeq = mainValues?.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0

            // increase sequence and mark as dirty
            calendar.updateEventRow(mainId, contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to mainSeq + 1,
                Events.DIRTY to 1
            ), batch)

            // actually remove deleted exception
            calendar.deleteEvent(exceptionId, batch)
        }

        batch.commit()
    }

    /**
     * Iterates through all exceptions in [calendar] that are marked as dirty
     * and not marked as deleted.
     *
     * For every found exception:
     *
     * - the SEQUENCE field of the exception is increased by one,
     * - the exception is marked as not dirty anymore,
     * - but the main event is marked as dirty (so that it will be synced).
     */
    fun processDirtyExceptions() {
        val batch = CalendarBatchOperation(calendar.client)

        // iterate through dirty exceptions
        calendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID, EventsContract.COLUMN_SEQUENCE),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Events._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(Events.ORIGINAL_ID)       // can't be null (by query)
            val exceptionSeq = values.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0
            logger.fine("Found dirty exception $exceptionId, increasing SEQUENCE and marking main event $mainId as dirty")

            // mark main event as dirty
            calendar.updateEventRow(mainId, contentValuesOf(
                Events.DIRTY to 1
            ), batch)

            // increase exception SEQUENCE and set DIRTY to 0
            calendar.updateEventRow(exceptionId, contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to exceptionSeq + 1,
                Events.DIRTY to 0
            ), batch)
        }

        batch.commit()
    }


}