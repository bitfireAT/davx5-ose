/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class CategoriesHandlerTest {

    private val handler = CategoriesHandler()

    @Test
    fun `legacy No category`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertTrue(task.categories.isEmpty())
    }

    @Test
    fun `legacy Single category`() {
        val task = Task()
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Work"), task)
        assertEquals(1, task.categories.size)
        assertEquals("Work", task.categories.first())
    }

    @Test
    fun `legacy Multiple categories accumulate`() {
        val task = Task()
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Work"), task)
        handler.process(contentValuesOf(Category.CATEGORY_NAME to "Personal"), task)

        assertEquals(2, task.categories.size)
        assertTrue(task.categories.containsAll(listOf("Work", "Personal")))
    }

    @Test
    fun `legacy Null category is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(Category.CATEGORY_NAME)
        }, task)
        assertTrue(task.categories.isEmpty())
    }

    @Test
    fun `No category`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues())
        handler.process(from = input, main = input, to = vToDo)

        val categories = vToDo.getProperty<Categories>(Property.CATEGORIES).getOrNull()
        assertNull(categories)
    }

    @Test
    fun `Single category`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
                Category.CATEGORY_NAME to "Work"
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        // Collect all category texts from all Categories properties
        val allCategories = mutableListOf<String>()
        val catProps = vToDo.getProperties<Categories>(Property.CATEGORIES)
        for (catProp in catProps) {
            allCategories.addAll(catProp.categories.texts)
        }
        assertEquals(1, catProps.size)
        assertEquals(1, allCategories.size)
        assertEquals("Work", allCategories.first())
    }

    @Test
    fun `Multiple categories accumulate`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
                Category.CATEGORY_NAME to "Work"
            ))
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
                Category.CATEGORY_NAME to "Personal"
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        // Collect all category texts from all Categories properties
        val allCategories = mutableListOf<String>()
        val catProps = vToDo.getProperties<Categories>(Property.CATEGORIES)
        for (catProp in catProps) {
            allCategories.addAll(catProp.categories.texts)
        }
        // With current implementation, each category is added as a separate Categories property
        // So we should have 2 properties, each with 1 category
        assertEquals(2, catProps.size)
        assertEquals(2, allCategories.size)
        assertTrue(allCategories.containsAll(listOf("Work", "Personal")))
    }

    @Test
    fun `Null category is skipped`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Category.MIMETYPE to Category.CONTENT_ITEM_TYPE
            ).apply {
                putNull(Category.CATEGORY_NAME)
            })
        }
        handler.process(from = input, main = input, to = vToDo)

        val categories = vToDo.getProperty<Categories>(Property.CATEGORIES).getOrNull()
        assertNull(categories)
    }

}
