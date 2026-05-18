/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard

import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Address
import org.junit.Assert
import org.junit.Test

class EzVCardTest {

    // https://github.com/mangstadt/ez-vcard/issues/140
    @Test()
    fun testKind_GROUP_uppercase() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "KIND:GROUP\r\n" +
                "END:VCARD").first()
        Assert.assertTrue(vCard.kind.isGroup)
    }

    @Test
    fun `Parse PHOTO binary encoding (vCard3)`() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "PHOTO;ENCODING=b;TYPE=JPEG:dGVzdA==\r\n" +
                "END:VCARD").first()
        // decodes binary data
        Assert.assertArrayEquals("test".toByteArray(), vCard.photos.first().data)
    }

    @Test
    fun `Parse PHOTO data URI (vCard3)`() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "PHOTO;VALUE=uri:data:image/png;base64,dGVzdA==\r\n" +
                "END:VCARD").first()
        val photo = vCard.photos.first()
        // decodes binary data
        Assert.assertArrayEquals("test".toByteArray(), vCard.photos.first().data)
    }

    @Test
    fun `Parse PHOTO data URI (vCard4)`() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "PHOTO:data:image/png;base64,dGVzdA==\r\n" +
                "END:VCARD").first()
        // decodes data URI
        Assert.assertArrayEquals("test".toByteArray(), vCard.photos.first().data)
    }

    @Test
    fun testREV_UTC() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "REV:20161218T201900Z\r\n" +
                "END:VCARD").first()
        Assert.assertNotNull(vCard.revision)
    }

    @Test
    fun testREV_UTC_Milliseconds() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "REV:2016-11-27T15:49:53.762Z\r\n" +
                "END:VCARD").first()
        Assert.assertNotNull(vCard.revision)
    }

    @Test
    fun testREV_WithoutTZ() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "REV:20161218T201900\r\n" +
                "END:VCARD").first()
        Assert.assertNotNull(vCard.revision)
    }

    @Test
    fun testREV_TZHourOffset() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "REV:20161218T201900-05\r\n" +
                "END:VCARD").first()
        Assert.assertNotNull(vCard.revision)
    }

    @Test
    fun testREV_TZHourAndMinOffset() {
        val vCard = Ezvcard.parse("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "REV:20161218T201900-0530\r\n" +
                "END:VCARD").first()
        Assert.assertNotNull(vCard.revision)
    }

    @Test
    fun testGenerateCaretNewline() {
        val vCard = VCard()
        vCard.addAddress(Address().apply {
            label = "Li^ne 1,1\n- \" -"
            streetAddress = "Line 1"
            country = "Line 2"
        })
        val str = Ezvcard.write(vCard)
                .version(VCardVersion.V4_0)
                .caretEncoding(true)
                .go().lines().filter { it.startsWith("ADR") }.first()
        //assertEquals("ADR;LABEL=\"Li^^ne 1,1^n- ^' -\":;;Line 1;;;;Line 2", str)
        Assert.assertEquals("ADR;LABEL=\"Li^^ne 1,1\\n- ^' -\":;;Line 1;;;;Line 2", str)
    }

}