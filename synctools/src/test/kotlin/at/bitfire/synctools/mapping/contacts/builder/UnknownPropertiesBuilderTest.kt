/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.UnknownPropertyContract
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
            assertEquals(UnknownPropertyContract.CONTENT_ITEM_TYPE, result[0].values[UnknownPropertyContract.MIMETYPE])
            assertEquals("X-TEST:12345", result[0].values[UnknownPropertyContract.UNKNOWN_PROPERTIES])
        }
    }

}
