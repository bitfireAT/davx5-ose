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
class CollectionIdBuilderTest {

    @Test
    fun happyPath() {
        val builder = CollectionIdBuilder(collectionId = 1L)
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, output)

        assertEquals(1L, output.entityValues.get(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID))
    }
}
