/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import ezvcard.property.Email
import ezvcard.property.Nickname
import ezvcard.property.Organization
import ezvcard.property.Telephone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StructuredNameBuilderTest {

    @Test
    fun testEmpty() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_Email() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "test@example.com"
            emails.add(LabeledProperty(Email("test@example.com")))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_Nickname() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "Foo"
            nickName = LabeledProperty(Nickname().apply {
                values.add("Foo")
            })
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_Org() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "Only A Company"
            organization = Organization().apply {
                values.add("Only A Company")
            }
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_OrgJoined() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "Only / A / Company"
            organization = Organization().apply {
                values.addAll(arrayOf("Only", "A", "Company"))
            }
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_PhoneNumber() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "+12345"
            phoneNumbers.add(LabeledProperty(Telephone("+12345")))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testSkipWhenOnly_Uid() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            displayName = "e6d19ebc-992a-4fef-9a56-84eab8b3bd9c"
            uid = "e6d19ebc-992a-4fef-9a56-84eab8b3bd9c"
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testValues() {
        StructuredNameBuilder(Uri.EMPTY, null, Contact().apply {
            prefix = "P."
            givenName = "Given"
            middleName = "Middle"
            familyName = "Family"
            suffix = "S"

            phoneticGivenName = "Phonetic Given"
            phoneticMiddleName = "Phonetic Middle"
            phoneticFamilyName = "Phonetic Family"
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(StructuredName.CONTENT_ITEM_TYPE, result[0].values[StructuredName.MIMETYPE])

            assertEquals("P.", result[0].values[StructuredName.PREFIX])
            assertEquals("Given", result[0].values[StructuredName.GIVEN_NAME])
            assertEquals("Middle", result[0].values[StructuredName.MIDDLE_NAME])
            assertEquals("Family", result[0].values[StructuredName.FAMILY_NAME])
            assertEquals("S", result[0].values[StructuredName.SUFFIX])

            assertEquals("Phonetic Given", result[0].values[StructuredName.PHONETIC_GIVEN_NAME])
            assertEquals("Phonetic Middle", result[0].values[StructuredName.PHONETIC_MIDDLE_NAME])
            assertEquals("Phonetic Family", result[0].values[StructuredName.PHONETIC_FAMILY_NAME])
        }
    }

}