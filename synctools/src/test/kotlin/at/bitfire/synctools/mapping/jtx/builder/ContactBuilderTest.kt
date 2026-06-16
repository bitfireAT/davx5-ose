/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.propertyListOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContactBuilderTest {

    private val builder = ContactBuilder()

    @Test
    fun `No CONTACT`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.CONTACT))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.CONTACT))
    }

    @Test
    fun `CONTACT has value`() {
        val task = VToDo(propertyListOf(Contact("John Doe <john@example.com>")))
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals("John Doe <john@example.com>", output.entityValues.get(JtxContract.JtxICalObject.CONTACT))
    }
}
