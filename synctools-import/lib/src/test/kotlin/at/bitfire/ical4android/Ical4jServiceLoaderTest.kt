/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.io.StringReader

class Ical4jServiceLoaderTest {

    @Test
    fun Ical4j_ServiceLoader_DoesntNeedContextClassLoader() {
        Thread.currentThread().contextClassLoader = null

        val iCal = "BEGIN:VCALENDAR\n" +
                "PRODID:-//xyz Corp//NONSGML PDA Calendar Version 1.0//EN\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "UID:uid1@example.com\n" +
                "DTSTART:19960918T143000Z\n" +
                "DTEND:19960920T220000Z\n" +
                "SUMMARY:Networld+Interop Conference\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n"
        val result = CalendarBuilder().build(StringReader(iCal))
        val vEvent = result.getComponent<VEvent>(Component.VEVENT).get()
        assertEquals("Networld+Interop Conference", vEvent.summary.value)
    }

}