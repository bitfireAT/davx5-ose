/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UidBuilderTest {

    private val builder = UidBuilder()

    @Test
    fun `No UID`() {
        val task = VToDo()
        val result = Entity(ContentValues())

        builder.build(from = task, main = task, to = result)

        assertContentValuesEqual(contentValuesOf(JtxContract.JtxICalObject.UID to null), result.entityValues)
    }

    @Test
    fun `UID is set`() {
        val journal = VJournal(propertyListOf(Uid("some-uid")))
        val main = VJournal()
        val result = Entity(ContentValues())

        builder.build(from = journal, main = main, to = result)

        assertContentValuesEqual(contentValuesOf(JtxContract.JtxICalObject.UID to "some-uid"), result.entityValues)
    }
}
