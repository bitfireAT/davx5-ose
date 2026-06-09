/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.isAllDay
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

class AllDayBuilder : DmfsTaskEntityBuilder {

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    override fun build(from: VToDo, to: Entity) {
        val allDay = from.isAllDay()
        if (allDay) {
            to.entityValues.put(Tasks.IS_ALLDAY, 1)
            to.entityValues.putNull(Tasks.TZ)
        } else {
            to.entityValues.put(Tasks.IS_ALLDAY, 0)
            to.entityValues.put(Tasks.TZ, getTimeZone(from).id)
        }
    }

    fun getTimeZone(vtodo: VToDo): TimeZone {
        val dtStart = vtodo.getProperty<DtStart<*>>(DtStart.DTSTART).getOrNull()
        val due = vtodo.getProperty<Due<*>>(Due.DUE).getOrNull()
        var tzId = dtStart?.let { ds ->
            if (ds.isUtc)
                TimeZones.UTC_ID
            else
                ds.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
        } ?:
        due?.let { d ->
            if (d.isUtc)
                TimeZones.UTC_ID
            else
                d.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
        } ?:
        ZoneId.systemDefault().id

        // 'Z' is not a valid timezone id, replace it by the UTC definition
        if (tzId == "Z") tzId = TimeZones.UTC_ID

        val timeZone: TimeZone? = tzRegistry.getTimeZone(tzId)
        return timeZone ?: throw NullPointerException("Could not find timezone '$tzId' in registry.")
    }

}
