/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VTimeZone
import java.io.StringReader

object ICalendarUtils {

    /**
     * Extracts the timezone ID from a VTimeZone definition string.
     *
     * @param vTimeZoneDef The VTimeZone definition string from which to extract the timezone ID.
     * @return The extracted timezone ID as a String, or null if the timezone ID could not be parsed.
     */
    fun getVTimeZoneId(vTimeZoneDef: String): String? {
        // CalendarBuilder requires a whole iCalendar and not only a VTIMEZONE.
        val iCalendar = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                vTimeZoneDef +
                "END:VCALENDAR\r\n"
        val calendar = CalendarBuilder().build(StringReader(iCalendar))
        val timezone = calendar.getComponent<VTimeZone>(Component.VTIMEZONE) ?: return null
        return timezone.timeZoneId?.value
    }

}