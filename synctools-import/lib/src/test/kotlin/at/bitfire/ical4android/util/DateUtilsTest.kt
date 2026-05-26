/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.ical4android.util.DateUtils.isDate
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.temporal.Temporal

class DateUtilsTest {

    @Test
    fun isDate_DateProperty() {
        assertTrue(isDate(DtStart(dateValue("20200101"))))
        assertFalse(isDate(DtStart(dateTimeValue("20200101T010203Z"))))
        assertFalse(isDate(null as DateProperty<*>?))
    }

    @Test
    fun isDateTime_DateProperty() {
        assertFalse(DateUtils.isDateTime(DtEnd(dateValue("20200101"))))
        assertTrue(DateUtils.isDateTime(DtEnd(dateTimeValue("20200101T010203Z"))))
        assertFalse(DateUtils.isDateTime(null as DateProperty<*>?))
    }

    @Test
    fun isDate_Temporal() {
        assertTrue(isDate(dateValue("20200101")))
        assertFalse(isDate(dateTimeValue("20200101T010203Z")))
        assertFalse(isDate(null as Temporal?))
    }

    @Test
    fun isDateTime_Temporal() {
        assertFalse(DateUtils.isDateTime(dateValue("20200101")))
        assertTrue(DateUtils.isDateTime(dateTimeValue("20200101T010203Z")))
        assertFalse(DateUtils.isDateTime(null as Temporal?))
    }

}
