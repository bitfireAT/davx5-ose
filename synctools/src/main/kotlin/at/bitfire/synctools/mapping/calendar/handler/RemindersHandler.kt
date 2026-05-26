/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Patterns
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import java.net.URI
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

class RemindersHandler(
    private val accountName: String
): AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        for (row in from.subValues.filter { it.uri == Reminders.CONTENT_URI })
            populateReminder(row.values, from, to)
    }

    private fun populateReminder(row: ContentValues, event: Entity, to: VEvent) {
        logger.log(Level.FINE, "Read event reminder from calendar provider", row)

        val eventTitle = event.entityValues.getAsString(Events.TITLE) ?: "Calendar Event Reminder"

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    alarm += ImmutableAction.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    alarm += Summary(eventTitle)
                    alarm += Description(eventTitle)
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    alarm += Attendee(URI("mailto", accountName, null))
                } else {
                    logger.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    alarm += ImmutableAction.DISPLAY
                    alarm += Description(eventTitle)
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                alarm += ImmutableAction.DISPLAY
                alarm += Description(eventTitle)
            }
        }
        to += alarm
    }

}