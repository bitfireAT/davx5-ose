/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.net.Uri
import at.bitfire.vcard4android.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class UnknownPropertiesBuilderTest {

    @Test
    fun testUnknownProperties_None() {
        UnknownPropertiesBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testUnknownProperties_Properties() {
        UnknownPropertiesBuilder(Uri.EMPTY, null, Contact().apply {
            unknownProperties = "X-TEST:12345"
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(UnknownProperties.CONTENT_ITEM_TYPE, result[0].values[UnknownProperties.MIMETYPE])
            assertEquals("X-TEST:12345", result[0].values[UnknownProperties.UNKNOWN_PROPERTIES])
        }
    }

}