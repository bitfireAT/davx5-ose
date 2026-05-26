/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard

import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.VCardVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.StringReader

class VCardParserTest {

    @Test
    fun testParse_VCard3() {
        val vCardString = """BEGIN:VCARD
VERSION:3.0
UID:test-uid
FN:Test Contact
N:Lastname;Firstname;;;
END:VCARD"""

        val parser = VCardParser()
        val vCards = parser.parse(StringReader(vCardString))

        assertEquals(1, vCards.size)
        val vCard = vCards.first()
        assertEquals(VCardVersion.V3_0, vCard.version)
        assertEquals("test-uid", vCard.uid.value)
        assertEquals("Test Contact", vCard.formattedName.value)
        assertEquals("Firstname", vCard.structuredName.given)
        assertEquals("Lastname", vCard.structuredName.family)
    }

    @Test
    fun testParse_VCard4() {
        val vCardString = """BEGIN:VCARD
VERSION:4.0
UID:test-uid-4
FN:Test Contact 4
N:Lastname;Firstname;;;
END:VCARD"""

        val parser = VCardParser()
        val vCards = parser.parse(StringReader(vCardString))

        assertEquals(1, vCards.size)
        val vCard = vCards.first()
        assertEquals(VCardVersion.V4_0, vCard.version)
        assertEquals("test-uid-4", vCard.uid.value)
        assertEquals("Test Contact 4", vCard.formattedName.value)
        assertEquals("Firstname", vCard.structuredName.given)
        assertEquals("Lastname", vCard.structuredName.family)
    }

    @Test
    fun testParse_MultipleVCards() {
        val vCardString = """BEGIN:VCARD
VERSION:3.0
UID:uid1
FN:Contact 1
END:VCARD
BEGIN:VCARD
VERSION:4.0
UID:uid2
FN:Contact 2
END:VCARD"""

        val parser = VCardParser()
        val vCards = parser.parse(StringReader(vCardString))

        assertEquals(2, vCards.size)
        assertEquals("uid1", vCards[0].uid.value)
        assertEquals("Contact 1", vCards[0].formattedName.value)
        assertEquals("uid2", vCards[1].uid.value)
        assertEquals("Contact 2", vCards[1].formattedName.value)
    }

    @Test
    fun testParse_WithCustomProperties() {
        val vCardString = """BEGIN:VCARD
VERSION:3.0
UID:test-uid
FN:Test Contact
X-ABDATE:20210729
END:VCARD"""

        val parser = VCardParser()
        val vCards = parser.parse(StringReader(vCardString))

        assertEquals(1, vCards.size)
        val vCard = vCards.first()
        // X-ABDATE is a custom property that should be parsed as XAbDate
        val xAbDate = vCard.getProperty(XAbDate::class.java)
        assertNotNull("X-ABDATE should be parsed as XAbDate", xAbDate)
    }

    @Test
    fun testParse_EmptyInput() {
        val parser = VCardParser()
        val vCards = parser.parse(StringReader(""))
        assertEquals(0, vCards.size)
    }

}
