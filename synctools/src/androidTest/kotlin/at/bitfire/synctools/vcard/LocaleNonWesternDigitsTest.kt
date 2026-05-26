/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard

import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Birthday
import ezvcard.property.Geo
import ezvcard.util.PartialDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.ComparisonFailure
import org.junit.Test
import java.util.Locale

class LocaleNonWesternDigitsTest {

    companion object {
        val locale = Locale("fa", "ir", "u-un-arabext")
    }

    val defaultLocale = Locale.getDefault()

    @Before
    fun verifyLocale() {
        Locale.setDefault(locale)
        Assume.assumeTrue("Persian (Iran) locale not available", locale.language == "fa")
    }

    @After
    fun resetLocale() {
        Locale.setDefault(defaultLocale)
    }


    @Test
    fun testLocale_StringFormat() {
        assertEquals("۲۰۲۰", String.format("%d", 2020))
    }

    @Test
    fun testLocale_StringFormat_Root() {
        assertEquals("2020", String.format(Locale.ROOT, "%d", 2020))
    }

    @Test(expected = ComparisonFailure::class)
    fun testLocale_ezVCard() {
        // see https://github.com/mangstadt/ez-vcard/issues/113
        val vCard = VCard(VCardVersion.V4_0).apply {
            geo = Geo(1.0, 2.0)
            birthday = Birthday(PartialDate.parse("--0820"))
        }
        assertEquals(
            "BEGIN:VCARD\r\n" +
                    "VERSION:4.0\r\n" +
                    "PRODID:ez-vcard 0.11.2\r\n" +
                    "GEO:geo:1.0,2.0\r\n" +     // failed before 0.11.2: was "GEO:geo:۱.۰,۲.۰\r\n" instead
                    "BDAY:--08-20\r\n" +        // currently fails
                    "END:VCARD\r\n", Ezvcard.write(vCard).go()
        )
    }

}