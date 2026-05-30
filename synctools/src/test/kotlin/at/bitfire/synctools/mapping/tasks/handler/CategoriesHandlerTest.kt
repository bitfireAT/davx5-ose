/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesHandlerTest {

    private val handler = CategoriesHandler()

    @Test
    fun `No category`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertTrue(task.categories.isEmpty())
    }

    @Test
    fun `Single category`() {
        val task = Task()
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Work"), task)
        assertEquals(1, task.categories.size)
        assertEquals("Work", task.categories.first())
    }

    @Test
    fun `Multiple categories accumulate`() {
        val task = Task()
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Work"), task)
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Personal"), task)

        assertEquals(2, task.categories.size)
        assertTrue(task.categories.containsAll(listOf("Work", "Personal")))
    }

    @Test
    fun `Null category is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(Category.CATEGORY_NAME)
        }, task)
        assertTrue(task.categories.isEmpty())
    }

}
