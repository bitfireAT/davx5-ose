/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.storage.jtx.BinaryDataRow
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

interface JtxObjectBinaryDataRowBuilder {

    /**
     * Maps a specific part of the given component ([VToDo] or [VJournal]) into a list of [BinaryDataRow]s.
     *
     * Note: The result of the mapping is used to create new sub-rows in the jtx Board content provider.
     * The binary data, if available, will be written to the content URI of the newly created sub-row.
     *
     * @param from component to map (will always be [VToDo] or [VJournal])
     *
     * @throws InvalidICalendarException on missing or invalid required properties
     */
    fun build(from: CalendarComponent): List<BinaryDataRow>
}
