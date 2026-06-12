/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.DateUtils.isDate
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertFalse
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
