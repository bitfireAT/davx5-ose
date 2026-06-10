/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentBuilderTest {

    private val builder = ComponentBuilder()

    @Test
    fun `VToDo writes VTODO`() {
        val output = Entity(ContentValues())
        val toDo = VToDo()

        builder.build(from = toDo, main = toDo, to = output)

        assertEquals("VTODO", output.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }

    @Test
    fun `VJournal writes VJOURNAL`() {
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, to = output)

        assertEquals("VJOURNAL", output.entityValues.get(JtxContract.JtxICalObject.COMPONENT))
    }
}
