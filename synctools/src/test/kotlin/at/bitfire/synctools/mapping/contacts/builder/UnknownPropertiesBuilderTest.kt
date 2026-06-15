/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
            assertEquals(AddressContract.UnknownProperty.CONTENT_ITEM_TYPE, result[0].values[AddressContract.UnknownProperty.MIMETYPE])
            assertEquals("X-TEST:12345", result[0].values[AddressContract.UnknownProperty.UNKNOWN_PROPERTIES])
        }
    }

}
