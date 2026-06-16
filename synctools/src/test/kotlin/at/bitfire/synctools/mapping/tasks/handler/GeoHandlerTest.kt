/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
class GeoHandlerTest {

    private val handler = GeoHandler()


    @Test
    fun `No GEO`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Geo>(), task.getProperty<Geo>(Property.GEO))
    }

    @Test
    fun `GEO is set`() {
        val input = Entity(contentValuesOf(Tasks.GEO to "16.3,48.2"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal()), task.getRequiredProperty<Geo>(Property.GEO))
    }

    @Test
    fun `GEO with trailing comma is ignored`() {
        val input = Entity(contentValuesOf(Tasks.GEO to "16.3,"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Geo>(), task.getProperty<Geo>(Property.GEO))
    }

    @Test
    fun `GEO without comma is ignored`() {
        val input = Entity(contentValuesOf(Tasks.GEO to "invalid"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Geo>(), task.getProperty<Geo>(Property.GEO))
    }

    @Test
    fun `GEO with invalid number is ignored`() {
        val input = Entity(contentValuesOf(Tasks.GEO to "not,a-number"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Geo>(), task.getProperty<Geo>(Property.GEO))
    }
}
