/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Organizer
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerHandler : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(
        from: Entity,
        main: Entity,
        to: CalendarComponent
    ) {
        val row = from.subValues
            .firstOrNull { it.uri == JtxContract.JtxOrganizer.CONTENT_URI }
            ?.values ?: return

        populateOrganizer(row, to)
    }

    private fun populateOrganizer(row: ContentValues, to: CalendarComponent) {
        val calAddress = row.getAsString(JtxContract.JtxOrganizer.CALADDRESS)
            ?.takeIf { it.isNotEmpty() } ?: return

        val organizer = Organizer()
        try {
            organizer.calAddress = URI(calAddress)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Ignoring invalid organizer URI: $calAddress", e)
        }

        row.getAsString(JtxContract.JtxOrganizer.CN)?.let { organizer += Cn(it) }
        row.getAsString(JtxContract.JtxOrganizer.DIR)?.let { organizer += Dir(it) }
        row.getAsString(JtxContract.JtxOrganizer.LANGUAGE)?.let { organizer += Language(it) }
        row.getAsString(JtxContract.JtxOrganizer.SENTBY)?.let { organizer += SentBy(it) }
        row.getAsString(JtxContract.JtxOrganizer.OTHER)?.let { other ->
            val otherParameters = JtxContract.getXParametersFromJson(other)
            for (parameter in otherParameters) {
                organizer += parameter
            }
        }

        to += organizer
    }
}
