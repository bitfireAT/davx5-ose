/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DirtyAndDeletedBuilderTest {

    private val builder = DirtyAndDeletedBuilder()

    @Test
    fun happyPath() {
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, output)

        assertEquals(false, output.entityValues.get(JtxContract.JtxICalObject.DIRTY))
        assertEquals(false, output.entityValues.get(JtxContract.JtxICalObject.DELETED))
    }
}
