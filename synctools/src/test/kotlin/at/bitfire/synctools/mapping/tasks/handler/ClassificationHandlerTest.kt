/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationHandlerTest {

    private val handler = ClassificationHandler()


    @Test
    fun `No CLASSIFICATION`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.classification)
    }

    @Test
    fun `CLASSIFICATION_PUBLIC maps to CLASS PUBLIC`() {
        val input = Entity(contentValuesOf(Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PUBLIC))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(ImmutableClazz.PUBLIC, task.classification)
    }

    @Test
    fun `CLASSIFICATION_PRIVATE maps to CLASS PRIVATE`() {
        val input = Entity(contentValuesOf(Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PRIVATE))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(ImmutableClazz.PRIVATE, task.classification)
    }

    @Test
    fun `CLASSIFICATION_CONFIDENTIAL maps to CLASS CONFIDENTIAL`() {
        val input = Entity(contentValuesOf(Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_CONFIDENTIAL))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(ImmutableClazz.CONFIDENTIAL, task.classification)
    }

    @Test
    fun `Unknown CLASSIFICATION maps to null`() {
        val input = Entity(contentValuesOf(Tasks.CLASSIFICATION to 99))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.classification)
    }
}
