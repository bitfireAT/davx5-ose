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
import org.junit.Assert.*
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
