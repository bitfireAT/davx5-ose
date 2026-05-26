/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import ezvcard.parameter.AddressType
import ezvcard.property.Address
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StructuredPostalBuilderTest {

    @Test
    fun testEmpty() {
        StructuredPostalBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testLabel() {
        val c = Contact().apply {
            addresses += LabeledProperty(Address().apply {
                streetAddress = "Street"
            }, "Label")
        }
        StructuredPostalBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(StructuredPostal.TYPE_CUSTOM, result[0].values[StructuredPostal.TYPE])
            assertEquals("Label", result[0].values[StructuredPostal.LABEL])
        }
    }


    @Test
    fun testValues() {
        StructuredPostalBuilder(Uri.EMPTY, null, Contact().apply {
            addresses += LabeledProperty(Address().apply {
                streetAddresses += "Street 1"
                streetAddresses += "(Corner Street 2)"
                extendedAddress = "Hood"
                poBox = "PO Box 123"
                locality = "City"
                region = "Region"
                postalCode = "ZIP"
                country = "Country"
            })
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(StructuredPostal.CONTENT_ITEM_TYPE, result[0].values[StructuredPostal.MIMETYPE])

            assertEquals("Street 1\n(Corner Street 2)", result[0].values[StructuredPostal.STREET])
            assertEquals("PO Box 123", result[0].values[StructuredPostal.POBOX])
            assertEquals("Hood", result[0].values[StructuredPostal.NEIGHBORHOOD])
            assertEquals("City", result[0].values[StructuredPostal.CITY])
            assertEquals("Region", result[0].values[StructuredPostal.REGION])
            assertEquals("ZIP", result[0].values[StructuredPostal.POSTCODE])
            assertEquals("Country", result[0].values[StructuredPostal.COUNTRY])

            assertEquals("Street 1\n" +     // European format
                    "(Corner Street 2)\n" +
                    "PO Box 123\n" +
                    "Hood\n" +
                    "ZIP City\n" +
                    "Country (Region)", result[0].values[StructuredPostal.FORMATTED_ADDRESS])
        }
    }


    @Test
    fun testType_Home() {
        val c = Contact().apply {
            addresses += LabeledProperty(Address().apply {
                streetAddress = "Street"
                types += AddressType.HOME
            })
        }
        StructuredPostalBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(StructuredPostal.TYPE_HOME, result[0].values[StructuredPostal.TYPE])
        }
    }

    @Test
    fun testType_None() {
        val c = Contact().apply {
            addresses += LabeledProperty(Address().apply {
                streetAddress = "Street"
            })
        }
        StructuredPostalBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(StructuredPostal.TYPE_OTHER, result[0].values[StructuredPostal.TYPE])
        }
    }

    @Test
    fun testType_Work() {
        val c = Contact().apply {
            addresses += LabeledProperty(Address().apply {
                streetAddress = "Street"
                types += AddressType.WORK
            })
        }
        StructuredPostalBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(StructuredPostal.TYPE_WORK, result[0].values[StructuredPostal.TYPE])
        }
    }

}