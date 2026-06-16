/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class OrganizerHandlerTest {

    private val handler = OrganizerHandler()

    @Test
    fun `No organizer sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.organizer)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(JtxContract.JtxOrganizer.CALADDRESS to "mailto:organizer@domain.example")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.organizer)
    }

    @Test
    fun `Organizer calendar address`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(JtxContract.JtxOrganizer.CALADDRESS to "mailto:organizer@domain.example")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(Organizer("mailto:organizer@domain.example"), output.organizer)
    }

    @Test
    fun `Organizer without calendar address is ignored`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(JtxContract.JtxOrganizer.CN to "Jane Organizer")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.organizer)
    }

    @Test
    fun `Organizer with empty calendar address is ignored`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(
                JtxContract.JtxOrganizer.CALADDRESS to "",
                JtxContract.JtxOrganizer.CN to "Jane Organizer"
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.organizer)
    }

    @Test
    fun `Organizer with invalid calendar address keeps property without address`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(
                JtxContract.JtxOrganizer.CALADDRESS to "not a valid uri",
                JtxContract.JtxOrganizer.CN to "Jane Organizer"
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.organizer?.calAddress)
        assertEquals("Jane Organizer", output.organizer?.cn?.value)
    }

    @Test
    fun `VJOURNAL organizer is mapped`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(JtxContract.JtxOrganizer.CALADDRESS to "mailto:organizer@domain.example")
        }
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertEquals(URI("mailto:organizer@domain.example"), output.organizer?.calAddress)
    }

    @Test
    fun `Single organizer with all known and unknown parameters`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(
                JtxContract.JtxOrganizer.CALADDRESS to "mailto:organizer@domain.example",
                JtxContract.JtxOrganizer.CN to "Jane Organizer",
                JtxContract.JtxOrganizer.DIR to "ldap://ldap.domain.example/something",
                JtxContract.JtxOrganizer.LANGUAGE to "en-US",
                JtxContract.JtxOrganizer.SENTBY to "mailto:sent-by@domain.example",
                JtxContract.JtxOrganizer.OTHER to """{"X-CUSTOM":"custom-value"}"""
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val organizer = output.organizer
        assertEquals(URI("mailto:organizer@domain.example"), organizer?.calAddress)
        assertEquals("Jane Organizer", organizer?.cn?.value)
        assertEquals("ldap://ldap.domain.example/something", organizer?.dir?.value)
        assertEquals("en-US", organizer?.language?.value)
        assertEquals("mailto:sent-by@domain.example", organizer?.sentBy?.value)
        assertEquals("custom-value", organizer?.getParameter<XParameter>("X-CUSTOM")?.getOrNull()?.value)
    }

    @Test
    fun `Only first organizer row is mapped`() {
        val input = Entity(ContentValues()).apply {
            addOrganizer(JtxContract.JtxOrganizer.CALADDRESS to "mailto:one@domain.example")
            addOrganizer(JtxContract.JtxOrganizer.CALADDRESS to "mailto:two@domain.example")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(URI("mailto:one@domain.example"), output.organizer?.calAddress)
    }
}

private fun Entity.addOrganizer(vararg values: Pair<String, Any?>) {
    addSubValue(JtxContract.JtxOrganizer.CONTENT_URI, contentValuesOf(*values))
}

private val CalendarComponent.organizer: Organizer?
    get() = getProperty<Organizer>(Property.ORGANIZER).getOrNull()

private val Organizer.cn: Cn?
    get() = getParameter<Cn>(Parameter.CN).getOrNull()

private val Organizer.dir: Dir?
    get() = getParameter<Dir>(Parameter.DIR).getOrNull()

private val Organizer.language: Language?
    get() = getParameter<Language>(Parameter.LANGUAGE).getOrNull()

private val Organizer.sentBy: SentBy?
    get() = getParameter<SentBy>(Parameter.SENT_BY).getOrNull()
