/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.dateTimeValue
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Duration

class TaskWriterTest {

    val testProdId = ProdId(javaClass.name)

    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!


    @Test
    fun testWrite() {
        val t = Task()
        t.uid = "SAMPLEUID"
        t.dtStart = DtStart(dateTimeValue("20190101T100000", tzBerlin))

        val alarm = VAlarm(Duration.ofHours(-1))
        alarm += ImmutableAction.AUDIO
        t.alarms += alarm

        val icalWriter = StringWriter()
        TaskWriter(testProdId).write(t, icalWriter)
        val raw = icalWriter.toString()

        assertTrue(raw.contains("PRODID:${testProdId.value}"))
        assertTrue(raw.contains("UID:SAMPLEUID"))
        assertTrue(raw.contains("DTSTAMP:"))
        assertTrue(raw.contains("DTSTART;TZID=Europe/Berlin:20190101T100000"))
        assertTrue(
            raw.contains(
                "BEGIN:VALARM\r\n" +
                        "TRIGGER:-PT1H\r\n" +
                        "ACTION:AUDIO\r\n" +
                        "END:VALARM\r\n"
            )
        )
        assertTrue(raw.contains("BEGIN:VTIMEZONE"))
    }

}