/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.mockk
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesBuilderTest {

    private val propertiesUri = Uri.parse("content://org.dmfs.tasks/properties")
    private val taskList = mockk<DmfsTaskList> {
        every { tasksPropertiesUri() } returns propertiesUri
    }
    private val builder = CategoriesBuilder(taskList)

    @Test
    fun `No categories`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `One category`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also { it.categories += "Work" },
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
            Category.CATEGORY_NAME to "Work"
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `Multiple categories`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.categories += "Work"
                it.categories += "Personal"
            },
            to = result
        )
        assertEquals(2, result.subValues.size)
    }

}
