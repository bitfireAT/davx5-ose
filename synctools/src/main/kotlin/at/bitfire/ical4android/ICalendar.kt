/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.validate.ValidationException
import java.io.Reader
import java.io.StringReader
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

open class ICalendar {

    open var uid: String? = null
    open var sequence: Int? = null

    /** list of CUAs which have edited the event since last sync */
    open var userAgents = LinkedList<String>()

    companion object {

        private val logger
            get() = Logger.getLogger(ICalendar::class.java.name)

        // known iCalendar properties

        const val CALENDAR_NAME = "X-WR-CALNAME"
        const val CALENDAR_COLOR = "X-APPLE-CALENDAR-COLOR"


        // PRODID generation

        /**
         * Extends the given `PRODID` with the user agents (typically calendar app name and version).
         * This way the `PRODID` does not only identify the app that actually produces the iCalendar,
         * but also the used front-end app, which may be helpful when debugging the iCalendar.
         *
         * @param userAgents    list of involved user agents
         *                      (preferably in `package name/version` format, for instance `com.example.mycalendar/1.0`)
         *
         * @return original `PRODID` with user agents in parentheses
         */
        fun ProdId.withUserAgents(userAgents: List<String>) =
            if (userAgents.isEmpty())
                this
            else
                ProdId(value + " (${userAgents.joinToString(", ")})")


        // parser

        /**
         * Parses an iCalendar resource and applies [ICalPreprocessor] to increase compatibility.
         *
         * @param reader        where the iCalendar is read from
         * @param properties    Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map. Key: property name; value: property value
         *
         * @return parsed iCalendar resource
         *
         * @throws InvalidICalendarException when the iCalendar can't be parsed
         */
        @Deprecated("Use ICalendarParser directly")
        fun fromReader(
            reader: Reader,
            properties: MutableMap<String, String>? = null
        ): Calendar {
            logger.fine("Parsing iCalendar stream")

            val calendar = ICalendarParser().parse(reader)

            // fill calendar properties
            properties?.let {
                calendar.getProperty<Property>(CALENDAR_NAME).getOrNull()?.let { calName ->
                    properties[CALENDAR_NAME] = calName.value
                }

                calendar.getProperty<Property>(Color.PROPERTY_NAME).getOrNull()?.let { calColor ->
                    properties[Color.PROPERTY_NAME] = calColor.value
                }
                calendar.getProperty<Property>(CALENDAR_COLOR).getOrNull()?.let { calColor ->
                    properties[CALENDAR_COLOR] = calColor.value
                }
            }

            return calendar
        }


        // time zone helpers

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        fun timezoneDefToTzId(timezoneDef: String): String? {
            try {
                val builder = CalendarBuilder()
                val cal = builder.build(StringReader(timezoneDef))
                val timezone = cal.getComponent<VTimeZone>(VTimeZone.VTIMEZONE).getOrNull()
                timezone?.timeZoneId?.let { return it.value }
            } catch (e: ParserException) {
                logger.log(Level.SEVERE, "Can't understand time zone definition", e)
            }
            return null
        }

        /**
         * Validates an iCalendar resource.
         *
         * Debug builds only: throws [ValidationException] when the resource is invalid.
         * Release builds only: prints a warning to the log when the resource is invalid.
         *
         * @param ical iCalendar resource to be validated
         *
         * @throws ValidationException when the resource is invalid (only if [BuildConfig.DEBUG] is set)
         */
        fun softValidate(ical: Calendar) {
            try {
                ical.validate(true)
            } catch (e: ValidationException) {
                if (BuildConfig.DEBUG)
                    // debug build, re-throw ValidationException
                    throw e
                else
                    logger.log(Level.WARNING, "iCalendar validation failed - This is only a warning!", e)
            }
        }

    }


    fun generateUID() {
        uid = UUID.randomUUID().toString()
    }

}