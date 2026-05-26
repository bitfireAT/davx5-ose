/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import at.bitfire.synctools.mapping.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteBuilderTest {

    @Test
    fun testNote_Empty() {
        NoteBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testNote_Blank() {
        NoteBuilder(Uri.EMPTY, null, Contact().apply {
            note = ""
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testNote_Value() {
        NoteBuilder(Uri.EMPTY, null, Contact().apply {
            note = "Some Note"
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(CommonDataKinds.Note.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Note.MIMETYPE])
            assertEquals("Some Note", result[0].values[CommonDataKinds.Note.NOTE])
        }
    }

}