/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import ezvcard.VCardVersion
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.ImppType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Birthday
import ezvcard.property.Email
import ezvcard.util.PartialDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.LinkedList

class ContactTest {

    private suspend fun parseContact(fname: String, charset: Charset = Charsets.UTF_8): Contact =
        javaClass.classLoader!!.getResourceAsStream(fname).use { stream ->
            Contact.fromReader(InputStreamReader(stream, charset), null).first()
        }

    private suspend fun regenerate(c: Contact, vCardVersion: VCardVersion): Contact {
        val os = ByteArrayOutputStream()
        c.writeVCard(vCardVersion, os, testProductId)
        return Contact.fromReader(InputStreamReader(ByteArrayInputStream(os.toByteArray()), Charsets.UTF_8), null).first()
    }


    @Test
    fun testToString_TruncatesLargeFields() {
        val c = Contact(
            displayName = "Test",
            members = mutableSetOf("1", "2", "3"),
            emails = LinkedList(listOf(LabeledProperty(Email("test@example.com")))),
            note = "Some Text\n".repeat(1000),
            photo = ByteArray(10*1024*1024) { 'A'.code.toByte() },  // 10 MB
            unknownProperties = "UNKNOWN:Property\n".repeat(1000)
        )
        val result = c.toString()
        assertTrue(result.length < 4500)   // 2000 note + 2000 unknown properties + rest
    }


    @Test
    fun testVCard3FieldsAsVCard3() = runTest {
        val c = regenerate(parseContact("allfields-vcard3.vcf"), VCardVersion.V3_0)

        // UID
        assertEquals("mostfields1@at.bitfire.vcard4android", c.uid)

        // FN
        assertEquals("Ämi Display", c.displayName)

        // N
        assertEquals("Firstname", c.givenName)
        assertEquals("Middlename1 Middlename2", c.middleName)
        assertEquals("Lastname", c.familyName)
        assertEquals("Förstnehm", c.phoneticGivenName)
        assertEquals("Mittelnehm", c.phoneticMiddleName)
        assertEquals("Laastnehm", c.phoneticFamilyName)

        // phonetic names
        assertEquals("Förstnehm", c.phoneticGivenName)
        assertEquals("Mittelnehm", c.phoneticMiddleName)
        assertEquals("Laastnehm", c.phoneticFamilyName)

        // TEL
        assertEquals(2, c.phoneNumbers.size)
        var phone = c.phoneNumbers.first()
        assertEquals("Useless", phone.label)
        assertTrue(phone.property.types.contains(TelephoneType.VOICE))
        assertTrue(phone.property.types.contains(TelephoneType.HOME))
        assertTrue(phone.property.types.contains(TelephoneType.PREF))
        assertNull(phone.property.pref)
        assertEquals("+49 1234 56788", phone.property.text)
        phone = c.phoneNumbers[1]
        assertNull(phone.label)
        assertTrue(phone.property.types.contains(TelephoneType.FAX))
        assertEquals("+1-800-MYFAX", phone.property.text)

        // EMAIL
        assertEquals(2, c.emails.size)
        var email = c.emails.first()
        assertNull(email.label)
        assertTrue(email.property.types.contains(EmailType.HOME))
        assertTrue(email.property.types.contains(EmailType.PREF))
        assertNull(email.property.pref)
        assertEquals("private@example.com", email.property.value)
        email = c.emails[1]
        assertEquals("@work", email.label)
        assertTrue(email.property.types.contains(EmailType.WORK))
        assertEquals("work@example.com", email.property.value)

        // ORG, TITLE, ROLE
        assertEquals(
                listOf("ABC, Inc.", "North American Division", "Marketing"),
                c.organization!!.values
        )
        assertEquals("Director, Research and Development", c.jobTitle)
        assertEquals("Programmer", c.jobDescription)

        // IMPP
        assertEquals(3, c.impps.size)
        var impp = c.impps.first()
        assertEquals("MyIM", impp.label)
        assertTrue(impp.property.types.contains(ImppType.PERSONAL))
        assertTrue(impp.property.types.contains(ImppType.MOBILE))
        assertTrue(impp.property.types.contains(ImppType.PREF))
        assertNull(impp.property.pref)
        assertEquals("myIM", impp.property.protocol)
        assertEquals("anonymous@example.com", impp.property.handle)
        impp = c.impps[1]
        assertNull(impp.label)
        assertTrue(impp.property.types.contains(ImppType.BUSINESS))
        assertEquals("skype", impp.property.protocol)
        assertEquals("echo@example.com", impp.property.handle)
        impp = c.impps[2]
        assertNull(impp.label)
        assertEquals("sip", impp.property.protocol)
        assertEquals("mysip@example.com", impp.property.handle)

        // NICKNAME
        assertEquals(
                listOf("Nick1", "Nick2"),
                c.nickName!!.property.values
        )

        // ADR
        assertEquals(2, c.addresses.size)
        var addr = c.addresses.first()
        assertNull(addr.label)
        assertTrue(addr.property.types.contains(AddressType.WORK))
        assertTrue(addr.property.types.contains(AddressType.POSTAL))
        assertTrue(addr.property.types.contains(AddressType.PARCEL))
        assertTrue(addr.property.types.contains(AddressType.PREF))
        assertNull(addr.property.pref)
        assertNull(addr.property.poBox)
        assertNull(addr.property.extendedAddress)
        assertEquals("6544 Battleford Drive", addr.property.streetAddress)
        assertEquals("Raleigh", addr.property.locality)
        assertEquals("NC", addr.property.region)
        assertEquals("27613-3502", addr.property.postalCode)
        assertEquals("U.S.A.", addr.property.country)
        addr = c.addresses[1]
        assertEquals("Monkey Tree", addr.label)
        assertTrue(addr.property.types.contains(AddressType.WORK))
        assertEquals("Postfach 314", addr.property.poBox)
        assertEquals("vorne hinten", addr.property.extendedAddress)
        assertEquals("Teststraße 22", addr.property.streetAddress)
        assertEquals("Mönchspfaffingen", addr.property.locality)
        assertNull(addr.property.region)
        assertEquals("4043", addr.property.postalCode)
        assertEquals("Klöster-Reich", addr.property.country)
        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "X-TEST;A=B:Value\r\n" +
                "END:VCARD\r\n", c.unknownProperties)

        // NOTE
        val ln = System.lineSeparator()
        assertEquals("This fax number is operational 0800 to 1715 EST, Mon-Fri.${ln}${ln}${ln}Second note", c.note)

        // CATEGORIES
        assertEquals(
                listOf("A", "B'C"),
                c.categories
        )

        // URL
        assertEquals(2, c.urls.size)
        var url1 = false
        var url2 = false
        for (url in c.urls) {
            if ("https://www.davx5.com/" == url.property.value && url.property.type == null && url.label == null)
                url1 = true
            if ("http://www.swbyps.restaurant.french/~chezchic.html" == url.property.value && "x-blog" == url.property.type && "blog" == url.label)
                url2 = true
        }
        assertTrue(url1 && url2)

        // BDAY
        assertEquals(OffsetDateTime.of(1996, 4, 15, 20, 12, 43, 0, ZoneOffset.ofHours(4)), c.birthDay!!.date)
        // ANNIVERSARY
        assertEquals(LocalDate.of(2014, 8, 12), c.anniversary!!.date)
        // X-ABDATE
        assertEquals(1, c.customDates.size)
        c.customDates.first().also { date ->
            assertEquals("Custom Date", date.label)
            assertEquals(LocalDate.of(2021, 7, 29), date.property.date)
        }

        // RELATED
        assertEquals(2, c.relations.size)
        var rel = c.relations.first()
        assertTrue(rel.types.contains(RelatedType.CO_WORKER))
        assertTrue(rel.types.contains(RelatedType.CRUSH))
        assertEquals("Ägidius", rel.text)
        rel = c.relations[1]
        assertTrue(rel.types.contains(RelatedType.PARENT))
        assertEquals("muuum@example.com", rel.text)

        // PHOTO
        javaClass.classLoader!!.getResourceAsStream("lol.jpg").use { photo ->
            assertArrayEquals(photo.readBytes(), c.photo)
        }
    }

    @Test
    fun testVCard3FieldsAsVCard4() = runTest {
        val c = regenerate(parseContact("allfields-vcard3.vcf"), VCardVersion.V4_0)
        // let's check only things that should be different when VCard 4.0 is generated

        val phone = c.phoneNumbers.first().property
        assertFalse(phone.types.contains(TelephoneType.PREF))
        assertNotNull(phone.pref)

        val email = c.emails.first().property
        assertFalse(email.types.contains(EmailType.PREF))
        assertNotNull(email.pref)

        val impp = c.impps.first().property
        assertFalse(impp.types.contains(ImppType.PREF))
        assertNotNull(impp.pref)

        val addr = c.addresses.first().property
        assertFalse(addr.types.contains(AddressType.PREF))
        assertNotNull(addr.pref)
    }

    @Test
    fun testVCard4FieldsAsVCard3() = runTest {
        val c = regenerate(parseContact("vcard4.vcf"), VCardVersion.V3_0)
        assertEquals(Birthday(PartialDate.parse("--04-16")), c.birthDay)
    }

    @Test
    fun testVCard4FieldsAsVCard4() = runTest {
        val c = regenerate(parseContact("vcard4.vcf"), VCardVersion.V4_0)
        assertEquals(Birthday(PartialDate.parse("--04-16")), c.birthDay)
    }


    @Test
    fun testStrangeREV() = runTest {
        val c = parseContact("strange-rev.vcf")
        assertNull(c.unknownProperties)
    }

}