/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.UnknownPropertyContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesHandlerTest {

    @Test
    fun testUnknownProperties_Empty() {
        val contact = Contact()
        UnknownPropertiesHandler.handle(ContentValues().apply {
            putNull(UnknownPropertyContract.UNKNOWN_PROPERTIES)
        }, contact)
        assertNull(contact.unknownProperties)
    }

    @Test
    fun testUnknownProperties_Values() {
        val contact = Contact()
        UnknownPropertiesHandler.handle(ContentValues().apply {
            put(UnknownPropertyContract.UNKNOWN_PROPERTIES, "X-TEST:12345")
        }, contact)
        assertEquals("X-TEST:12345", contact.unknownProperties)
    }

}
