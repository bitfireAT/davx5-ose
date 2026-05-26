/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.property.TzOffsetFrom
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class LocaleNonWesternDigitsTest {

    companion object {
        val origLocale = Locale.getDefault()
        val testLocale = Locale("fa", "ir", "u-un-arabext")

        @BeforeClass
        @JvmStatic
        fun setFaIrArabLocale() {
            Assume.assumeTrue("Persian (Iran) locale not available", testLocale.language == "fa")
            Locale.setDefault(testLocale)
        }

        @AfterClass
        @JvmStatic
        fun resetLocale() {
            Locale.setDefault(origLocale)
        }

    }

    @Test
    fun testLocale_StringFormat() {
        // does not fail if the Locale with Persian digits is available
        assertEquals("۲۰۲۰", String.format("%d", 2020))
    }

    @Test
    fun testLocale_StringFormat_Root() {
        assertEquals("2020", String.format(Locale.ROOT, "%d", 2020))
    }

    @Test()
    fun testLocale_ical4j() {
        val offset = TzOffsetFrom(ZoneOffset.ofHours(1))
        val iCal = offset.toString()
        assertEquals("TZOFFSETFROM:+0100\r\n", iCal)        // fails: is "TZOFFSETFROM:+۰۱۰۰\r\n" instead
    }

}