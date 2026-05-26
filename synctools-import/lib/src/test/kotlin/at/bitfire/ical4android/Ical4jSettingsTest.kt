/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Test

class Ical4jSettingsTest {

    @Test
    fun testDatesAreUtc() {
        /* ical4j can treat DATE values either as
           - floating (= system time zone), or
           - UTC.

           This is controlled by the "net.fortuna.ical4j.timezone.date.floating" setting.

           The Calendar provider requires date timestamps to be in UTC, so we will test that.
        */
        assertEquals(TimeZones.getUtcTimeZone(), TimeZones.getDateTimeZone())
    }

}