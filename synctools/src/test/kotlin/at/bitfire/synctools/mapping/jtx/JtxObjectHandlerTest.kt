/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class JtxObjectHandlerTest {

    private val handler = JtxObjectHandler(
        prodId = ProdId(javaClass.simpleName)
    )

    @Test
    fun `mapToCalendarComponents maps VTODO component`() {
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertEquals(VToDo::class.java, result.associatedComponents.main?.javaClass)
    }

    @Test
    fun `mapToCalendarComponents maps VJOURNAL component`() {
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VJOURNAL",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertEquals(VJournal::class.java, result.associatedComponents.main?.javaClass)
    }

    @Test
    fun `mapToCalendarComponents returns UID`() {
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO",
                    JtxContract.JtxICalObject.UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertFalse(result.generatedUid)
        assertEquals("uid", result.uid)
    }

    @Test
    fun `mapToCalendarComponents creates UID`() {
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(
                contentValuesOf(
                    JtxContract.JtxICalObject.COMPONENT to "VTODO"
                    // No UID
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertTrue(result.generatedUid)
        assertNotNull(result.uid)
    }

    @Test
    fun `exceptions are mapped for recurring task with RRULE`() {
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.COMPONENT to "VTODO",
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id
        ))
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.COMPONENT to "VTODO",
                JtxContract.JtxICalObject.UID to "uid",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"
            )),
            exceptions = listOf(exception)
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertEquals(1, result.associatedComponents.exceptions.size)
    }

    @Test
    fun `exceptions are mapped for recurring task with RDATE only`() {
        // Regression test: exceptions must be included even when recurrence is expressed
        // via RDATE alone (no RRULE). Previously only RRULE was checked.
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.COMPONENT to "VTODO",
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id
        ))
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.COMPONENT to "VTODO",
                JtxContract.JtxICalObject.UID to "uid",
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to ZoneOffset.UTC.id,
                JtxContract.JtxICalObject.RDATE to "1779451200000"   // 2026-05-22T12:00:00Z
            )),
            exceptions = listOf(exception)
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertEquals(1, result.associatedComponents.exceptions.size)
    }

    @Test
    fun `exceptions are dropped for non-recurring task`() {
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.COMPONENT to "VTODO",
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id
        ))
        val jtxObjectAndExceptions = JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.COMPONENT to "VTODO",
                JtxContract.JtxICalObject.UID to "uid"
                // no RRULE, no RDATE
            )),
            exceptions = listOf(exception)
        )

        val result = handler.mapToCalendarComponents(jtxObjectAndExceptions)

        assertEquals(0, result.associatedComponents.exceptions.size)
    }
}
