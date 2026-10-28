/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class DurationBuilderTest {

    private val builder = DurationBuilder()

    private val start = ZonedDateTime.of(2026, 9, 27, 11, 12, 0, 0, ZoneOffset.UTC)


    @Test
    fun `No DURATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DURATION to null
        ), result.entityValues)
    }

    @Test
    fun `DURATION is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(start), Duration(ParameterList(), "PT2H")),
            to = result
        )
        assertContentValuesEqual(
            contentValuesOf(
                Tasks.DURATION to "PT2H"
            ), result.entityValues
        )
    }

    @Test
    fun `DURATION is ignored when DUE is present`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(start), Due(start.plusMinutes(45)), Duration(ParameterList(), "PT45M")),
            to = result
        )
        assertContentValuesEqual(
            contentValuesOf(
                Tasks.DURATION to null
            ), result.entityValues
        )
    }

    @Test
    fun `DURATION is ignored without DTSTART`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Duration(ParameterList(), "PT2H")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DURATION to null
        ), result.entityValues)
    }

}
