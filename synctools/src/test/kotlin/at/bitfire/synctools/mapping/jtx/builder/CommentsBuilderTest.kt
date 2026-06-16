/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Comment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class CommentsBuilderTest {
    private val builder = CommentsBuilder()

    @Test
    fun `no comment`() {
        val output = Entity(ContentValues())
        val journal = VJournal()

        builder.build(from = journal, main = journal, output)

        assertEquals(0, output.subValues.size)
    }

    @Test
    fun `single comment`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Comment(
                ParameterList(
                    listOf(
                        Language("en"),
                        AltRep(URI.create("https://domain.example/comment.txt")),
                        XParameter("X-PARAM", "x-value")
                    )
                ),
                "comment"
            )
        }
        val main = VToDo()

        builder.build(from = task, main = main, output)

        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxComment.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxComment.TEXT to "comment",
                JtxContract.JtxComment.ALTREP to "https://domain.example/comment.txt",
                JtxContract.JtxComment.LANGUAGE to "en",
                JtxContract.JtxComment.OTHER to """{"X-PARAM":"x-value"}""",
                JtxContract.JtxComment.ID to 0L
            ),
            actual = subValue.values
        )
    }

    @Test
    fun `multiple comments`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Comment("one")
            this += Comment("two")
        }

        builder.build(from = task, main = task, output)

        assertEquals(2, output.subValues.size)
        val first = output.subValues[0]
        assertEquals(JtxContract.JtxComment.CONTENT_URI, first.uri)
        assertEquals("one", first.values.getAsString(JtxContract.JtxComment.TEXT))
        val second = output.subValues[1]
        assertEquals(JtxContract.JtxComment.CONTENT_URI, second.uri)
        assertEquals("two", second.values.getAsString(JtxContract.JtxComment.TEXT))
    }
}
