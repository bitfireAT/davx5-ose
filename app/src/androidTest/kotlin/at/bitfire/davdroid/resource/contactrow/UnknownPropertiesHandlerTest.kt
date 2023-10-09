/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.content.ContentValues
import at.bitfire.vcard4android.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnknownPropertiesHandlerTest {

    @Test
    fun testUnknownProperties_Empty() {
        val contact = Contact()
        UnknownPropertiesHandler.handle(ContentValues().apply {
            putNull(UnknownProperties.UNKNOWN_PROPERTIES)
        }, contact)
        assertNull(contact.unknownProperties)
    }

    @Test
    fun testUnknownProperties_Values() {
        val contact = Contact()
        UnknownPropertiesHandler.handle(ContentValues().apply {
            put(UnknownProperties.UNKNOWN_PROPERTIES, "X-TEST:12345")
        }, contact)
        assertEquals("X-TEST:12345", contact.unknownProperties)
    }

}