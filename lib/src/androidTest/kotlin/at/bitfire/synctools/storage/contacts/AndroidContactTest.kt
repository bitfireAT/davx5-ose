/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.contacts

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.ContactsContract
import android.util.Base64
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.vcard.property.XAbDate
import ezvcard.VCardVersion
import ezvcard.property.Address
import ezvcard.property.Birthday
import ezvcard.property.Email
import ezvcard.util.PartialDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AndroidContactTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private val testAddressBookAccount = Account("AndroidContactTest", "at.bitfire.vcard4android")

        private lateinit var provider: ContentProviderClient
        private lateinit var addressBook: TestAddressBook

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)

            addressBook = TestAddressBook(testAddressBookAccount, provider)
        }

        @BeforeClass
        @JvmStatic
        fun disconnect() {
            @Suppress("DEPRECATION")
            provider.release()
        }
    }


    @Test
    fun testAddAndReadContact() {
        val samplePhoto = Base64.decode("/9j/4AAQSkZJRgABAQEASABIAAD//gATQ3JlYXRlZCB3aXRoIEdJTVD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wgARCAAFAAUDAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQBAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhADEAAAAVSf/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABCf/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPxB//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPxB//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxB//9k=", Base64.DEFAULT)

        val vcard = Contact()
        vcard.displayName = "Mya Contact"
        vcard.prefix = "Magª"
        vcard.givenName = "Mya"
        vcard.familyName = "Contact"
        vcard.suffix = "BSc"
        vcard.phoneticGivenName = "Först"
        vcard.phoneticMiddleName = "Mittelerde"
        vcard.phoneticFamilyName = "Fämilie"
        vcard.birthDay = Birthday(LocalDate.parse("1980-04-16"))
        vcard.customDates += LabeledProperty(XAbDate(PartialDate.parse("--0102")), "Custom Date")
        vcard.photo = samplePhoto

        val contact = AndroidContact(addressBook, vcard, null, null)
        contact.add()

        val contact2 = addressBook.findContactById(contact.id!!)
        try {
            val vcard2 = contact2.getContact()
            assertEquals(vcard.displayName, vcard2.displayName)
            assertEquals(vcard.prefix, vcard2.prefix)
            assertEquals(vcard.givenName, vcard2.givenName)
            assertEquals(vcard.familyName, vcard2.familyName)
            assertEquals(vcard.suffix, vcard2.suffix)
            assertEquals(vcard.phoneticGivenName, vcard2.phoneticGivenName)
            assertEquals(vcard.phoneticMiddleName, vcard2.phoneticMiddleName)
            assertEquals(vcard.phoneticFamilyName, vcard2.phoneticFamilyName)
            assertEquals(vcard.birthDay, vcard2.birthDay)
            assertArrayEquals(vcard.customDates.toArray(), vcard2.customDates.toArray())
            assertNotNull(vcard.photo)
        } finally {
            contact2.delete()
        }
    }

    @Test
    fun testInvalidPREF() = runTest {
        val vCard = "BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "FN:Test\r\n" +
                "TEL;CELL=;PREF=:+12345\r\n" +
                "EMAIL;PREF=invalid:test@example.com\r\n" +
                "END:VCARD\r\n"
        val contacts = Contact.fromReader(StringReader(vCard), null)

        val dbContact = AndroidContact(addressBook, contacts.first(), null, null)
        dbContact.add()

        val dbContact2 = addressBook.findContactById(dbContact.id!!)
        try {
            val contact2 = dbContact2.getContact()
            assertEquals("Test", contact2.displayName)
            assertEquals("+12345", contact2.phoneNumbers.first.property.text)
            assertEquals("test@example.com", contact2.emails.first.property.value)
        } finally {
            dbContact2.delete()
        }
    }

    @Test
    fun testBirthdayWithOffset() = runTest {
        val vCard = "BEGIN:VCARD\r\n" +
            "VERSION:3.0\n\n" +
            "N:Doe;John;;;\n\n" +
            "FN:John Doe\n\n" +
            "BDAY:20010415T000000+0200\n\n" +
            "END:VCARD\n\n"
        val contacts = Contact.fromReader(StringReader(vCard), null)

        assertEquals(1, contacts.size)
        contacts.first().birthDay.let { birthday ->
            assertNotNull(birthday)

            val date = birthday?.date
            assertNotNull(date)

            assertEquals(
                OffsetDateTime.of(2001, 4, 15, 0, 0, 0, 0, ZoneOffset.ofHours(2)), date
            )
        }
    }

    @Test
    @MediumTest
    fun testLargeTransactionManyRows() {
        val vcard = Contact()
        vcard.displayName = "Large Transaction (many rows)"
        for (i in 0 until 4000)
            vcard.emails += LabeledProperty(Email("test$i@example.com"))

        val contact = AndroidContact(addressBook, vcard, null, null)
        contact.add()

        val contact2 = addressBook.findContactById(contact.id!!)
        try {
            val vcard2 = contact2.getContact()
            assertEquals(4000, vcard2.emails.size)
        } finally {
            contact2.delete()
        }
    }

    @Test(expected = LocalStorageException::class)
    fun testLargeTransactionSingleRow() {
        val vcard = Contact()
        vcard.displayName = "Large Transaction (one row which is too large)"

        // 1 MB eTag ... have fun
        val data = CharArray(1024*1024) { 'x' }
        val eTag = String(data)

        val contact = AndroidContact(addressBook, vcard, null, eTag)
        contact.add()
    }

    @Test
    fun testAddressCaretEncoding() {
        val address = Address()
        address.label = "My \"Label\"\nLine 2"
        address.streetAddress = "Street \"Address\""
        val contact = Contact()
        contact.addresses += LabeledProperty(address)

        /* label-param = "LABEL=" param-value
         * param-values must not contain DQUOTE and should be encoded as defined in RFC 6868
         *
         * ADR-value = ADR-component-pobox ";" ADR-component-ext ";"
         *             ADR-component-street ";" ADR-component-locality ";"
         *             ADR-component-region ";" ADR-component-code ";"
         *             ADR-component-country
         * ADR-component-pobox    = list-component
         *
         * list-component = component *("," component)
         * component = "\\" / "\," / "\;" / "\n" / WSP / NON-ASCII / %x21-2B / %x2D-3A / %x3C-5B / %x5D-7E
         *
         * So, ADR value components may contain DQUOTE (0x22) and don't have to be encoded as defined in RFC 6868 */

        val os = ByteArrayOutputStream()
        contact.writeVCard(VCardVersion.V4_0, os, testProductId)
        assertTrue(os.toString().contains("ADR;LABEL=My ^'Label^'\\nLine 2:;;Street \"Address\";;;;"))
    }

}