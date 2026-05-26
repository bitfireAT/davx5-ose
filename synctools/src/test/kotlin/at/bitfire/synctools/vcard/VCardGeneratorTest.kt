/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard

import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Address
import ezvcard.property.FormattedName
import ezvcard.property.StructuredName
import ezvcard.property.Uid
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDate

class VCardGeneratorTest {

    @Test
    fun testWrite_VCard3() {
        val vCard = VCard().apply {
            uid = Uid("test-uid")
            formattedName = FormattedName("Test Contact")
            structuredName = StructuredName().apply {
                given = "Firstname"
                family = "Lastname"
            }
        }

        val result = StringWriter().apply {
            val generator = VCardGenerator(VCardVersion.V3_0, includeTrailingSemicolons = true)
            generator.write(vCard, this)
        }.toString()

        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "UID:test-uid\r\n" +
                "FN:Test Contact\r\n" +
                "N:Lastname;Firstname;;;\r\n" +
                "END:VCARD\r\n", result)
    }

    @Test
    fun testWrite_VCard4() {
        val vCard = VCard().apply {
            uid = Uid("test-uid-4")
            formattedName = FormattedName("Test Contact 4")
            structuredName = StructuredName().apply {
                given = "Firstname"
                family = "Lastname"
            }
        }

        val result = StringWriter().apply {
            val generator = VCardGenerator(VCardVersion.V4_0, includeTrailingSemicolons = true)
            generator.write(vCard, this)
        }.toString()

        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "UID:test-uid-4\r\n" +
                "FN:Test Contact 4\r\n" +
                "N:Lastname;Firstname;;;\r\n" +
                "END:VCARD\r\n", result)
    }

    @Test
    fun testWrite_WithoutTrailingSemicolons() {
        val vCard = VCard().apply {
            uid = Uid("test")
            structuredName = StructuredName().apply {
                given = "Firstname"
                family = "Lastname"
            }
        }

        val result = StringWriter().apply {
            val generator = VCardGenerator(VCardVersion.V3_0, includeTrailingSemicolons = false)
            generator.write(vCard, this)
        }.toString()

        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "UID:test\r\n" +
                "N:Lastname;Firstname\r\n" +
                "END:VCARD\r\n", result)
    }

    @Test
    fun testWrite_WithCustomProperty() {
        val vCard = VCard().apply {
            uid = Uid("test")
            formattedName = FormattedName("Test")
            // Add a custom property
            addProperty(XAbDate(LocalDate.of(2021, 7, 29)))
        }

        val result = StringWriter().apply {
            val generator = VCardGenerator(VCardVersion.V3_0, includeTrailingSemicolons = true)
            generator.write(vCard, this)
        }.toString()

        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "UID:test\r\n" +
                "FN:Test\r\n" +
                "X-ABDATE:2021-07-29\r\n" +
                "END:VCARD\r\n", result)
    }

    @Test
    fun testWrite_EnablesCaretEncoding() {
        val vCard = VCard().apply {
            uid = Uid("test")
            formattedName = FormattedName("Test")
            addAddress(Address().apply {
                label = "Li^ne \"1\""
                streetAddress = "Street"
                country = "Country"
            })
        }

        val result = StringWriter().apply {
            val generator = VCardGenerator(VCardVersion.V4_0, includeTrailingSemicolons = true)
            generator.write(vCard, this)
        }.toString()

        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "UID:test\r\n" +
                "FN:Test\r\n" +
                "ADR;LABEL=Li^^ne ^'1^':;;Street;;;;Country\r\n" +
                "END:VCARD\r\n", result)
    }

}
