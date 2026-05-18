/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.JtxItemAndExceptions
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ProdId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JtxItemHandlerTest {

    private val handler = JtxItemHandler(
        prodId = ProdId(javaClass.simpleName)
    )

    @Test
    fun `mapToCalendarComponents maps VTODO component`() {
        val itemAndExceptions = JtxItemAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(
            itemAndExceptions = itemAndExceptions
        )

        assertEquals(VToDo::class.java, result.associatedItems.main?.javaClass)
    }

    @Test
    fun `mapToCalendarComponents maps VJOURNAL component`() {
        val itemAndExceptions = JtxItemAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VJOURNAL",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(
            itemAndExceptions = itemAndExceptions
        )

        assertEquals(VJournal::class.java, result.associatedItems.main?.javaClass)
    }

    @Test
    fun `mapToCalendarComponents returns UID`() {
        val itemAndExceptions = JtxItemAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(
            itemAndExceptions = itemAndExceptions
        )

        assertFalse(result.generatedUid)
        assertEquals("uid", result.uid)
    }

    @Test
    fun `mapToCalendarComponents creates UID`() {
        val itemAndExceptions = JtxItemAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO"
                    // No UID
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(
            itemAndExceptions = itemAndExceptions
        )

        assertTrue(result.generatedUid)
        assertNotNull(result.uid)
    }
}
