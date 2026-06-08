/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.DtStart
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class StartTimeBuilderTest {

    private val builder = StartTimeBuilder()


    @Test
    fun `No DTSTART`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DTSTART to null
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(DtStart(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DTSTART to 1736899200000L   // 2025-01-15 00:00:00 UTC
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE-TIME (UTC)`() {
        val result = Entity(ContentValues())
        val ts = ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        builder.build(
            from = VToDoUtil.build(DtStart(ts)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DTSTART to ts.toInstant().toEpochMilli()
        ), result.entityValues)
    }

}
