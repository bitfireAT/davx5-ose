/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.util.TimeZones
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

/**
 * Builds the shared [Tasks.IS_ALLDAY] flag (RFC 5545 §3.3.4 DATE vs §3.3.5 DATE-TIME). Also
 * offers [tzId] / [referenceTimeZone], reused by [StartTimeBuilder], [DueBuilder] and
 * [RecurrenceFieldsBuilder] — unlike the DMFS backend, DTSTART and DUE each keep their *own*
 * TZID column ([Tasks.DTSTART_TZ] / [Tasks.DUE_TZ]) rather than one shared column, per §3.2 of
 * the design doc.
 */
class AllDayBuilder : DavTaskEntityBuilder {

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    override fun build(from: VToDo, to: Entity) {
        to.entityValues.put(Tasks.IS_ALLDAY, from.isAllDay())
    }

    /** The TZID for a single date property: `"UTC"` if UTC, the TZID parameter value, or `null` if floating. */
    fun tzId(prop: DateProperty<*>?): String? {
        if (prop == null) return null
        if (prop.isUtc) return TimeZones.UTC_ID
        val tzid = prop.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
        return if (tzid == "Z") TimeZones.UTC_ID else tzid
    }

    /** A single reference timezone for the whole task (used to serialize RDATE/EXDATE), preferring DTSTART, then DUE, then the system default. */
    fun referenceTimeZone(from: VToDo): TimeZone {
        val dtStart = from.getProperty<DtStart<*>>(DtStart.DTSTART).getOrNull()
        val due = from.getProperty<Due<*>>(Due.DUE).getOrNull()
        val tzid = tzId(dtStart) ?: tzId(due) ?: ZoneId.systemDefault().id
        return tzRegistry.getTimeZone(tzid) ?: throw NullPointerException("Could not find timezone '$tzid' in registry.")
    }

}
