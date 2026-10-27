/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard

import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.VCardVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        assertEquals(VCardVersion.V3_0, vCard!!.version)
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
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        assertEquals(VCardVersion.V4_0, vCard!!.version)
        assertEquals("test-uid-4", vCard.uid.value)
        assertEquals("Test Contact 4", vCard.formattedName.value)
        assertEquals("Firstname", vCard.structuredName.given)
        assertEquals("Lastname", vCard.structuredName.family)
    }

    @Test
    fun testParse_OnlyFirstOfMultipleVCards() {
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
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        assertEquals("uid1", vCard!!.uid.value)
        assertEquals("Contact 1", vCard.formattedName.value)
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
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        // X-ABDATE is a custom property that should be parsed as XAbDate
        val xAbDate = vCard!!.getProperty(XAbDate::class.java)
        assertNotNull("X-ABDATE should be parsed as XAbDate", xAbDate)
    }

    @Test
    fun testParse_EmptyInput() {
        val parser = VCardParser()
        val vCard = parser.parse(StringReader(""))
        assertNull(vCard)
    }

    @Test
    fun testParse_NonVCardGarbage_ReturnsNull() {
        // no BEGIN:VCARD/END:VCARD at all, so there's nothing to return - but it doesn't throw either
        val parser = VCardParser()
        val vCard = parser.parse(StringReader("this is not a vCard\njust some random text\n"))
        assertNull(vCard)
    }

    @Test
    fun testParse_MalformedLine_IsSkipped() {
        // a line without a colon is malformed and gets skipped, rest of the vCard still parses
        val vCardString = """BEGIN:VCARD
VERSION:3.0
UID:test-uid
this line has no colon and is malformed
FN:Test Contact
END:VCARD"""

        val parser = VCardParser()
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        assertEquals("test-uid", vCard!!.uid.value)
        assertEquals("Test Contact", vCard.formattedName.value)
    }

    @Test
    fun testParse_UnparsableValue_DoesNotThrow() {
        // REV expects a timestamp; a garbage value doesn't cause an exception
        val vCardString = """BEGIN:VCARD
VERSION:3.0
UID:test-uid
FN:Test Contact
REV:not-a-valid-timestamp
END:VCARD"""

        val parser = VCardParser()
        val vCard = parser.parse(StringReader(vCardString))

        assertNotNull(vCard)
        assertEquals("test-uid", vCard!!.uid.value)
    }

}
