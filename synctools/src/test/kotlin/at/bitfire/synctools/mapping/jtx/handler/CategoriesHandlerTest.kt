/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class CategoriesHandlerTest {

    private val handler = CategoriesHandler()

    @Test
    fun `No category sub-values produces no CATEGORIES property`() {
        val from = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<Categories>(Property.CATEGORIES).getOrNull())
    }

    @Test
    fun `Single category is mapped`() {
        val from = Entity(ContentValues()).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "work"))
        }
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val categories = output.getProperty<Categories>(Property.CATEGORIES).getOrNull()
        assertEquals(1, categories?.categories?.texts?.size)
        assertEquals("work", categories?.categories?.texts?.first())
    }

    @Test
    fun `Multiple categories are combined into one CATEGORIES property`() {
        val from = Entity(ContentValues()).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "work"))
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "personal"))
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "urgent"))
        }
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val categories = output.getProperty<Categories>(Property.CATEGORIES).getOrNull()
        assertEquals(3, categories?.categories?.texts?.size)
        assertEquals(setOf("work", "personal", "urgent"), categories?.categories?.texts?.toSet())
    }

    @Test
    fun `Category sub-value with null TEXT is skipped`() {
        val from = Entity(ContentValues()).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "work"))
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, ContentValues()) // no TEXT
        }
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val categories = output.getProperty<Categories>(Property.CATEGORIES).getOrNull()
        assertEquals(1, categories?.categories?.texts?.size)
        assertEquals("work", categories?.categories?.texts?.first())
    }

    @Test
    fun `Sub-values with other URIs are ignored`() {
        val from = Entity(ContentValues()).apply {
            addSubValue(JtxContract.JtxAlarm.CONTENT_URI, contentValuesOf(JtxContract.JtxCategory.TEXT to "should-be-ignored"))
        }
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<Categories>(Property.CATEGORIES).getOrNull())
    }
}
