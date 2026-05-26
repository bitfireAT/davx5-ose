/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalendarUidSplitterTest {

    @Test
    fun testAssociatedVEventsByUid_Empty() {
        val calendar = Calendar(ComponentList())
        val result = CalendarUidSplitter<VEvent>().associateByUid(calendar, Component.VEVENT)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testAssociatedVEventsByUid_ExceptionOnly_NoUid() {
        val exception = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z")
        ))
        val calendar = Calendar(componentListOf(exception))
        val result = CalendarUidSplitter<VEvent>().associateByUid(calendar, Component.VEVENT)
        assertEquals(
            mapOf(
                null to AssociatedEvents(null, listOf(exception))
            ),
            result
        )
    }

    @Test
    fun testAssociatedVEventsByUid_MainOnly_NoUid() {
        val mainEvent = VEvent()
        val calendar = Calendar(componentListOf(mainEvent))
        val result = CalendarUidSplitter<VEvent>().associateByUid(calendar, Component.VEVENT)
        assertEquals(
            mapOf(
                null to AssociatedEvents(mainEvent, emptyList())
            ),
            result
        )
    }

    @Test
    fun testAssociatedVEventsByUid_MainOnly_WithUid() {
        val mainEvent = VEvent(propertyListOf(
            Uid("main")
        ))
        val calendar = Calendar(componentListOf(mainEvent))
        val result = CalendarUidSplitter<VEvent>().associateByUid(calendar, Component.VEVENT)
        assertEquals(
            mapOf(
                "main" to AssociatedEvents(mainEvent, emptyList())
            ),
            result
        )
    }


    @Test
    fun testFilterBySequence_Empty() {
        val result = CalendarUidSplitter<VEvent>().filterBySequence(emptyList())
        assertEquals(emptyList<VEvent>(), result)
    }

    @Test
    fun testFilterBySequence_MainAndExceptions_MultipleSequences() {
        val mainEvent1a = VEvent(propertyListOf(Sequence(1)))
        val mainEvent1b = VEvent(propertyListOf(Sequence(2)))
        val exception1a = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(1)
        ))
        val exception1b = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(2)
        ))
        val exception1c = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(3)
        ))
        val exception2a = VEvent(propertyListOf(
            RecurrenceId<LocalDate>("20250629")
            // Sequence(0)
        ))
        val exception2b = VEvent(propertyListOf(
            RecurrenceId<LocalDate>("20250629"),
            Sequence(1)
        ))
        val result = CalendarUidSplitter<VEvent>().filterBySequence(
            listOf(mainEvent1a, mainEvent1b, exception1a, exception1c, exception1b, exception2a, exception2b)
        )
        assertEquals(listOf(mainEvent1b, exception1c, exception2b), result)
    }

    @Test
    fun testFilterBySequence_MainAndExceptions_SingleSequence() {
        val mainEvent = VEvent(propertyListOf(Sequence(1)))
        val exception1 = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(1)
        ))
        val exception2 = VEvent(propertyListOf(
            RecurrenceId<LocalDate>("20250629")
            // Sequence(0)
        ))
        val result = CalendarUidSplitter<VEvent>().filterBySequence(
            listOf(mainEvent, exception1, exception2)
        )
        assertEquals(listOf(mainEvent, exception1, exception2), result)
    }

    @Test
    fun testFilterBySequence_OnlyException_SingleSequence() {
        val exception = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z")
        ))
        val result = CalendarUidSplitter<VEvent>().filterBySequence(listOf(exception))
        assertEquals(listOf(exception), result)
    }

    @Test
    fun testFilterBySequence_OnlyExceptions_MultipleSequences() {
        val exception1a = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(1)
        ))
        val exception1b = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(2)
        ))
        val exception1c = VEvent(propertyListOf(
            RecurrenceId<Instant>("20250629T000000Z"),
            Sequence(3)
        ))
        val exception2a = VEvent(propertyListOf(
            RecurrenceId<LocalDate>("20250629")
            // Sequence(0)
        ))
        val exception2b = VEvent(propertyListOf(
            RecurrenceId<LocalDate>("20250629"),
            Sequence(1)
        ))
        val result = CalendarUidSplitter<VEvent>().filterBySequence(
            listOf(exception1a, exception1c, exception1b, exception2a, exception2b)
        )
        assertEquals(listOf(exception1c, exception2b), result)
    }

    @Test
    fun testFilterBySequence_OnlyMain_SingleSequence() {
        val mainEvent = VEvent()
        val result = CalendarUidSplitter<VEvent>().filterBySequence(listOf(mainEvent))
        assertEquals(listOf(mainEvent), result)
    }

    @Test
    fun testFilterBySequence_OnlyMain_MultipleSequences() {
        val mainEvent1a = VEvent(propertyListOf(Sequence(1)))
        val mainEvent1b = VEvent(propertyListOf(Sequence(2)))
        val mainEvent1c = VEvent(propertyListOf(Sequence(2)))
        val result = CalendarUidSplitter<VEvent>().filterBySequence(listOf(mainEvent1a, mainEvent1c, mainEvent1b))
        assertEquals(listOf(mainEvent1c), result)
    }

}