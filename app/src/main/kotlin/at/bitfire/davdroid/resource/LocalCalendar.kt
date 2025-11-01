/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Application-specific subclass of [AndroidCalendar] for local calendars.
 *
 * [Calendars._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalCalendar @AssistedInject constructor(
    @Assisted internal val androidCalendar: AndroidCalendar,
    private val logger: Logger
) : LocalCollection<LocalEvent> {

    @AssistedFactory
    interface Factory {
        fun create(calendar: AndroidCalendar): LocalCalendar
    }


    // properties

    override val dbCollectionId: Long?
        get() = androidCalendar.syncId?.toLongOrNull()

    override val tag: String
        get() = "events-${androidCalendar.account.name}-${androidCalendar.id}"

    override val title: String
        get() = androidCalendar.displayName ?: androidCalendar.id.toString()

    override val readOnly
        get() = androidCalendar.accessLevel <= Calendars.CAL_ACCESS_READ

    override var lastSyncState: SyncState?
        get() = androidCalendar.readSyncState()?.let {
            SyncState.fromString(it)
        }
        set(state) {
            androidCalendar.writeSyncState(state.toString())
        }

    @VisibleForTesting
    internal val recurringCalendar = AndroidRecurringCalendar(androidCalendar)


    fun add(event: EventAndExceptions): Long {
        return recurringCalendar.addEventAndExceptions(event)
    }

    override fun findDeleted(): List<LocalEvent> {
        val result = LinkedList<LocalEvent>()
        recurringCalendar.iterateEventAndExceptions(
            "${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL", null
        ) { eventAndExceptions ->
            result += LocalEvent(recurringCalendar, eventAndExceptions)
        }
        return result
    }

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()
        recurringCalendar.iterateEventAndExceptions(
            "${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL", null
        ) { eventAndExceptions ->
            dirty += LocalEvent(recurringCalendar, eventAndExceptions)
        }
        return dirty
    }

    override fun findByName(name: String) =
        recurringCalendar.findEventAndExceptions("${Events._SYNC_ID}=? AND ${Events.ORIGINAL_SYNC_ID} IS null", arrayOf(name))?.let {
            LocalEvent(recurringCalendar, it)
        }

    override fun markNotDirty(flags: Int) =
        androidCalendar.updateEventRows(
            contentValuesOf(EventsContract.COLUMN_FLAGS to flags),
            // `dirty` can be 0, 1, or null. "NOT dirty" is not enough.
            """
                ${Events.CALENDAR_ID}=?
                AND (${Events.DIRTY} IS NULL OR ${Events.DIRTY}=0)
                AND ${Events.ORIGINAL_ID} IS NULL
            """.trimIndent(),
            arrayOf(androidCalendar.id.toString())
        )

    override fun removeNotDirtyMarked(flags: Int): Int {
        // list all non-dirty events with the given flags and delete every row + its exceptions
        val batch = CalendarBatchOperation(androidCalendar.client)
        androidCalendar.iterateEventRows(
            arrayOf(Events._ID),
            // `dirty` can be 0, 1, or null. "NOT dirty" is not enough.
            """
                ${Events.CALENDAR_ID}=?
                AND (${Events.DIRTY} IS NULL OR ${Events.DIRTY}=0)
                AND ${Events.ORIGINAL_ID} IS NULL
                AND ${EventsContract.COLUMN_FLAGS}=?
            """.trimIndent(),
            arrayOf(androidCalendar.id.toString(), flags.toString())
        ) { values ->
            val id = values.getAsLong(Events._ID)

            // delete event and possible exceptions (content provider doesn't delete exceptions itself)
            batch += BatchOperation.CpoBuilder
                .newDelete(androidCalendar.eventsUri)
                .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString()))
        }
        return batch.commit()
    }

    override fun forgetETags() {
        androidCalendar.updateEventRows(
            contentValuesOf(EventsContract.COLUMN_ETAG to null),
            "${Events.CALENDAR_ID}=?", arrayOf(androidCalendar.id.toString())
        )
    }

}