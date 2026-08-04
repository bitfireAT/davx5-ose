/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskAndExceptions
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class DavTaskHandlerTest {

    private val handler = DavTaskHandler(prodId = ProdId("-//Test//Test//EN"), userAgentPackageName = "at.bitfire.davdroid.test")

    private fun entityOf(values: ContentValues, vararg subValues: ContentValues): Entity =
        Entity(values).apply {
            for (sv in subValues)
                addSubValue(android.net.Uri.parse("content://irrelevant"), sv)
        }


    @Test
    fun `mapToVToDos generates UID when missing`() {
        val main = entityOf(contentValuesOf(Tasks.SUMMARY to "No UID here"))

        val result = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList()))

        assertTrue(result.generatedUid)
        assertEquals(result.uid, result.associatedTasks.main!!.getProperty<net.fortuna.ical4j.model.property.Uid>(net.fortuna.ical4j.model.property.Uid.UID).get().value)
    }

    @Test
    fun `mapToVToDos keeps existing UID`() {
        val main = entityOf(contentValuesOf(Tasks._UID to "existing-uid", Tasks.SUMMARY to "Has UID"))

        val result = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList()))

        assertEquals(false, result.generatedUid)
        assertEquals("existing-uid", result.uid)
    }

    @Test
    fun `mapToVToDos maps organizer with CN and SENT-BY`() {
        val main = entityOf(contentValuesOf(
            Tasks._UID to "uid-1",
            Tasks.ORGANIZER to "mailto:boss@example.com",
            Tasks.ORGANIZER_CN to "The Boss",
            Tasks.ORGANIZER_SENT_BY to "mailto:secretary@example.com"
        ))

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val organizer = vToDo.getProperty<net.fortuna.ical4j.model.property.Organizer>(net.fortuna.ical4j.model.property.Organizer.ORGANIZER).get()
        assertEquals("mailto:boss@example.com", organizer.calAddress.toString())
        assertEquals("The Boss", organizer.getParameter<net.fortuna.ical4j.model.parameter.Cn>(Parameter.CN).get().value)
        assertEquals("mailto:secretary@example.com", organizer.getParameter<net.fortuna.ical4j.model.parameter.SentBy>(Parameter.SENT_BY).get().value)
    }

    @Test
    fun `mapToVToDos maps RFC 9253 RELTYPE verbatim`() {
        val main = entityOf(
            contentValuesOf(Tasks._UID to "uid-1"),
            contentValuesOf(
                TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_RELATION,
                TaskProperties.DATA1 to "other-task-uid",
                TaskProperties.DATA2 to "DEPENDS-ON"
            )
        )

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val relatedTo = vToDo.getProperty<RelatedTo>(RelatedTo.RELATED_TO).get()
        assertEquals("other-task-uid", relatedTo.value)
        assertEquals("DEPENDS-ON", relatedTo.getParameter<net.fortuna.ical4j.model.parameter.RelType>(Parameter.RELTYPE).get().value)
    }

    @Test
    fun `mapToVToDos maps attendee with full parameters`() {
        val main = entityOf(
            contentValuesOf(Tasks._UID to "uid-1"),
            contentValuesOf(
                TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_ATTENDEE,
                TaskProperties.DATA1 to "mailto:alice@example.com",
                TaskProperties.DATA2 to "Alice",
                TaskProperties.DATA9 to "NEEDS-ACTION",
                TaskProperties.DATA10 to "REQ-PARTICIPANT"
            )
        )

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val attendee = vToDo.getProperty<net.fortuna.ical4j.model.property.Attendee>(net.fortuna.ical4j.model.property.Attendee.ATTENDEE).get()
        assertEquals("mailto:alice@example.com", attendee.calAddress.toString())
        assertEquals("Alice", attendee.getParameter<net.fortuna.ical4j.model.parameter.Cn>(Parameter.CN).get().value)
        assertEquals("NEEDS-ACTION", attendee.getParameter<net.fortuna.ical4j.model.parameter.PartStat>(Parameter.PARTSTAT).get().value)
    }

    @Test
    fun `mapToVToDos maps alarm with relative trigger`() {
        val main = entityOf(
            contentValuesOf(Tasks._UID to "uid-1"),
            contentValuesOf(
                TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY,
                TaskAlarms.TRIGGER_RELATIVE to "PT-15M"
            )
        )

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val alarm = vToDo.alarms.single()
        assertEquals(Action.VALUE_DISPLAY, alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value)
        val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull()
        assertNotNull(trigger?.duration)
    }

    @Test
    fun `mapToVToDos maps alarm with absolute trigger`() {
        val absoluteMillis = java.time.Instant.parse("2026-01-01T09:00:00Z").toEpochMilli()
        val main = entityOf(
            contentValuesOf(Tasks._UID to "uid-1"),
            contentValuesOf(
                TaskAlarms.ACTION to TaskAlarms.Action.DISPLAY,
                TaskAlarms.TRIGGER_ABSOLUTE to absoluteMillis
            )
        )

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val alarm = vToDo.alarms.single()
        val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull()
        assertTrue(trigger?.isAbsolute == true)
    }

    @Test
    fun `mapToVToDos maps multiple categories as one CATEGORIES property`() {
        val main = entityOf(
            contentValuesOf(Tasks._UID to "uid-1"),
            contentValuesOf(TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "Home"),
            contentValuesOf(TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY, TaskProperties.DATA1 to "Errands")
        )

        val vToDo = handler.mapToVToDos(DavTaskAndExceptions(main = main, exceptions = emptyList())).associatedTasks.main!!

        val categoryValues = vToDo.getProperties<net.fortuna.ical4j.model.property.Categories>(net.fortuna.ical4j.model.Property.CATEGORIES)
            .flatMap { it.categories.texts }
        assertEquals(setOf("Home", "Errands"), categoryValues.toSet())
    }

}
