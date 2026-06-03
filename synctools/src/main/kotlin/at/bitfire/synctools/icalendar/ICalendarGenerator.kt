/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.PropertyContainer
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.Writer
import java.time.temporal.Temporal
import java.util.logging.Logger
import javax.annotation.WillNotClose
import kotlin.jvm.optionals.getOrNull

/**
 * Writes an ical4j [net.fortuna.ical4j.model.Calendar] to a stream that contains an iCalendar
 * (VCALENDAR with respective components and optional VTIMEZONEs).
 */
class ICalendarGenerator {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Generates an iCalendar from the given [AssociatedComponents].
     *
     * @param event     event to generate iCalendar from
     * @param to        stream that the iCalendar is written to
     */
    fun write(event: AssociatedComponents<*>, @WillNotClose to: Writer) {
        val ical = Calendar()
        ical += ImmutableVersion.VERSION_2_0

        // add PRODID
        if (event.prodId != null)
            ical += event.prodId

        // keep record of used timezone IDs and earliest DTSTART in order to be able to add VTIMEZONEs
        var earliestStart: Temporal? = null
        val usedTimezoneIds = mutableSetOf<String>()

        // add main event
        if (event.main != null) {
            ical += event.main

            earliestStart = event.main.dtStart<Temporal>()?.date
            usedTimezoneIds += timeZonesOf(event.main)
        }

        // recurrence exceptions
        for (exception in event.exceptions) {
            ical += exception

            exception.dtStart<Temporal>()?.date?.let { start ->
                if (earliestStart == null || TemporalAdapter.isBefore(start, earliestStart))
                    earliestStart = start
            }
            usedTimezoneIds += timeZonesOf(exception)
        }

        /* Add VTIMEZONE components. Unfortunately we can't generate VTIMEZONEs from the actual ZoneIds,
        so we have to include the VTIMEZONEs shipped with ical4j – even if those are not the same
        as the system time zones. This is a known problem, but there's currently no known solution.
        Most clients ignore the VTIMEZONE anyway if they know the TZID [RFC 7809 3.1.3 "Observation
        and experiments"], and Android/Java/IANA timezones are usually known to all clients. */
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        for (tzId in usedTimezoneIds) {
            var vTimeZone = tzReg.getTimeZone(tzId)?.vTimeZone ?: continue

            /* Special case: sometimes, the timezone may have been loaded by an alias.
            For instance, old Androids may use the "Europe/Kiev" timezone (which is then in tzId),
            but ical4j returns the new "Europe/Kyiv" timezone. In that case, we want the original
            name used by Android because if we would use the new TZ ID, it wouldn't be understood
            by Android (and thus downgraded to the system default timezone) if we get it back
            again from the server. */
            val ical4jTzId = vTimeZone.timeZoneId.value
            if (ical4jTzId != tzId) {
                logger.warning("Android timezone $tzId maps to ical4j $ical4jTzId. Using Android TZID.")

                /* Better not modify the VTIMEZONE because it's cached by TimeZoneRegistry, and we don't
                want to modify the cache. Create a copy instead. */
                vTimeZone = copyVTimeZone(vTimeZone)
                vTimeZone.replace<PropertyContainer>(net.fortuna.ical4j.model.property.TzId(tzId))
            }

            // Minify VTIMEZONE and attach to iCalendar
            val minifiedVTimeZone = VTimeZoneMinifier().minify(vTimeZone, earliestStart)
            ical += minifiedVTimeZone
        }

        CalendarOutputter(false).output(ical, to)
    }

    /**
     * Creates a one-level deep copy of the given [VTimeZone] instance.
     *
     * This method copies the property list and observances list from the original [VTimeZone],
     * **but does not perform a deep copy of the individual properties or observances**.
     *
     * This allows properties and observances to be added or removed in the copied instance without affecting
     * the original, but modifications to existing properties or observances will still impact the original.
     *
     * @param vTimeZone The [VTimeZone] instance to be copied.
     * @return A new [VTimeZone] instance, safe for properties/observances to be added and removed, **but not to be modified**
     */
    @VisibleForTesting
    internal fun copyVTimeZone(vTimeZone: VTimeZone): VTimeZone = VTimeZone(
        PropertyList(vTimeZone.propertyList.all),
        ComponentList(vTimeZone.observances.toList())
    )

    /**
     * Extracts all unique time zone identifiers from the given component and its subcomponents.
     *
     * This method searches through all properties of the component, filtering for date properties
     * that contain a TZID parameter. It also recursively processes subcomponents (such as alarms)
     * if the component is a VEvent.
     *
     * @param component The component to extract time zone identifiers from.
     * @return A set of unique time zone identifiers found in the component and its subcomponents.
     */
    @VisibleForTesting
    internal fun timeZonesOf(component: Component): Set<String> {
        val timeZones = mutableSetOf<String>()

        // iterate through all properties
        timeZones += component.propertyList.all
            .filterIsInstance<DateProperty<*>>()
            .mapNotNull {
                /* Note: When a property like DTSTART is created like DtStart(ZonedDateTime()),
                the setDate() calls refreshParameters.refreshParameters() and that one sets the TZID
                from the actual timezone ID. */
                it.getParameter<TzId>(Parameter.TZID).getOrNull()?.value?.trimToNull()
            }
            .toSet()

        // also iterate through subcomponents like alarms recursively
        if (component is VEvent)
            for (subcomponent in component.componentList.all)
                timeZones += timeZonesOf(subcomponent)

        return timeZones
    }

}
