/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.ContactContract
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
            putNull(ContactContract.UnknownProperty.UNKNOWN_PROPERTIES)
        }, contact)
        assertNull(contact.unknownProperties)
    }

    @Test
    fun testUnknownProperties_Values() {
        val contact = Contact()
        UnknownPropertiesHandler.handle(ContentValues().apply {
            put(ContactContract.UnknownProperty.UNKNOWN_PROPERTIES, "X-TEST:12345")
        }, contact)
        assertEquals("X-TEST:12345", contact.unknownProperties)
    }

}
