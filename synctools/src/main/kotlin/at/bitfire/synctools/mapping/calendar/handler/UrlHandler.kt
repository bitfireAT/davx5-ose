/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Url
import java.net.URI
import java.net.URISyntaxException

class UrlHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val urlRow = extended.firstOrNull { it.getAsString(ExtendedProperties.NAME) == EventsContract.EXTNAME_URL }
        val url = urlRow?.getAsString(ExtendedProperties.VALUE)
        if (url != null) {
            val uri = try {
                URI(url)
            } catch (_: URISyntaxException) {
                null
            }
            if (uri != null)
                to += Url(uri)
        }
    }

}