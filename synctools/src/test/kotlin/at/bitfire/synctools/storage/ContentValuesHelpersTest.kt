/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.content.ContentValues
import android.database.MatrixCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentValuesHelpersTest {

    @Test
    fun testCursorToContentValues() {
        val columns = arrayOf("col1", "col2")
        val c = MatrixCursor(columns)
        c.addRow(arrayOf("row1_val1", "row1_val2"))
        c.moveToFirst()
        val values = c.toContentValues()
        assertEquals("row1_val1", values.getAsString("col1"))
        assertEquals("row1_val2", values.getAsString("col2"))
    }

    @Test
    fun testContentValuesRemoveBlank() {
        val values = ContentValues()
        values.put("key1", "value")
        values.put("key2", 1L)
        values.put("key3", "")
        values.put("key4", "\n")
        values.put("key5", " \n ")
        values.put("key6", " ")
        values.removeBlank()
        assertEquals("value", values.getAsString("key1"))
        assertEquals(1L, values.getAsLong("key2"))
        assertNull(values.get("key3"))
        assertNull(values.get("key4"))
        assertNull(values.get("key5"))
        assertNull(values.get("key6"))
    }

}