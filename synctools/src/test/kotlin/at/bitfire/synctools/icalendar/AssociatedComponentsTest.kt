/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.dateValue
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Uid
import org.junit.Test

class AssociatedComponentsTest {

    @Test(expected = IllegalArgumentException::class)
    fun testEmpty() {
        AssociatedEvents(null, emptyList())
    }

    @Test
    fun testOnlyExceptions_UidNull() {
        AssociatedEvents(null, listOf(
            VEvent(propertyListOf(
                RecurrenceId(dateValue("20250629"))
            ))
        ))
    }

    @Test
    fun testOnlyExceptions_UidNotNull() {
        AssociatedEvents(null, listOf(
            VEvent(propertyListOf(
                Uid("test1"),
                RecurrenceId(dateValue("20250629"))
            ))
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnlyExceptions_UidNotIdentical() {
        AssociatedEvents(null, listOf(
            VEvent(propertyListOf(
                RecurrenceId(dateValue("20250629"))
            )),
            VEvent(propertyListOf(
                Uid("test1"),
                RecurrenceId(dateValue("20250630"))
            ))
        ))
    }

    @Test
    fun testOnlyMain_NoUid() {
        AssociatedEvents(VEvent(), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnlyMain_RecurId() {
        AssociatedEvents(VEvent(propertyListOf(
            RecurrenceId(dateValue("20250629"))
        )), emptyList())
    }

    @Test
    fun testOnlyMain_Uid() {
        AssociatedEvents(VEvent(propertyListOf(Uid("test1"))), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnlyMain_UidAndRecurId() {
        AssociatedEvents(VEvent(propertyListOf(
            Uid("test1"),
            RecurrenceId(dateValue("20250629"))
        )), emptyList())
    }

}