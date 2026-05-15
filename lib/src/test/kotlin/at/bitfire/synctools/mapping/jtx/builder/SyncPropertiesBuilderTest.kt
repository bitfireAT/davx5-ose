/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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
class SyncPropertiesBuilderTest {

    @Test
    fun happyPath() {
        val builder = SyncPropertiesBuilder(
            fileName = "filename",
            eTag = "etag",
            scheduleTag = "scheduletag",
            flags = 0
        )
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, output)

        assertEquals("filename", output.entityValues.get(JtxContract.JtxICalObject.FILENAME))
        assertEquals("etag", output.entityValues.get(JtxContract.JtxICalObject.ETAG))
        assertEquals("scheduletag", output.entityValues.get(JtxContract.JtxICalObject.SCHEDULETAG))
        assertEquals(0, output.entityValues.get(JtxContract.JtxICalObject.FLAGS))
    }
}
