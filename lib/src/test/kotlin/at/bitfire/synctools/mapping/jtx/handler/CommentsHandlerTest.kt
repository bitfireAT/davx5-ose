/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class CommentsHandlerTest {

    private val handler = CommentsHandler()

    @Test
    fun `No comment sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Comment>(Property.COMMENT).size)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(JtxContract.JtxComment.TEXT to "should be ignored")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Comment>(Property.COMMENT).size)
    }

    @Test
    fun `Single comment with text only`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(JtxContract.JtxComment.TEXT to "meeting notes")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(1, comments.size)
        assertEquals("meeting notes", comments.first().value)
        assertNull(comments.first().getParameter<AltRep>(Parameter.ALTREP).getOrNull())
        assertNull(comments.first().getParameter<Language>(Parameter.LANGUAGE).getOrNull())
    }

    @Test
    fun `Single comment with ALTREP`() {
        val altRepUri = "https://example.com/comment.txt"
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxComment.TEXT to "see link",
                JtxContract.JtxComment.ALTREP to altRepUri
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(1, comments.size)
        assertEquals("see link", comments.first().value)
        assertEquals(altRepUri, comments.first().getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value)
    }

    @Test
    fun `Single comment with LANGUAGE`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxComment.TEXT to "Bemerkung",
                JtxContract.JtxComment.LANGUAGE to "de"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(1, comments.size)
        assertEquals("Bemerkung", comments.first().value)
        assertEquals("de", comments.first().getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value)
    }

    @Test
    fun `Single comment with X-parameters in OTHER`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxComment.TEXT to "annotated",
                JtxContract.JtxComment.OTHER to """{"X-CUSTOM":"custom-value"}"""
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(1, comments.size)
        assertEquals("annotated", comments.first().value)
        assertEquals(
            "custom-value",
            comments.first().getParameter<XParameter>("X-CUSTOM").getOrNull()?.value
        )
    }

    @Test
    fun `Single comment with all parameters`() {
        val altRepUri = "https://example.com/full-comment.txt"
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxComment.TEXT to "full comment",
                JtxContract.JtxComment.ALTREP to altRepUri,
                JtxContract.JtxComment.LANGUAGE to "en",
                JtxContract.JtxComment.OTHER to """{"X-EXTRA":"extra-value"}"""
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(1, comments.size)
        val comment = comments.first()
        assertEquals("full comment", comment.value)
        assertEquals(altRepUri, comment.getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value)
        assertEquals("en", comment.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value)
        assertEquals("extra-value", comment.getParameter<XParameter>("X-EXTRA").getOrNull()?.value)
    }

    @Test
    fun `Multiple comments`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(JtxContract.JtxComment.TEXT to "first")
        )
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(JtxContract.JtxComment.TEXT to "second")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val comments = output.getProperties<Comment>(Property.COMMENT)
        assertEquals(2, comments.size)
        assertEquals("first", comments[0].value)
        assertEquals("second", comments[1].value)
    }

    @Test
    fun `Comment with missing TEXT is skipped`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxComment.CONTENT_URI,
            contentValuesOf(JtxContract.JtxComment.LANGUAGE to "en")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.getProperties<Comment>(Property.COMMENT).size)
    }
}
