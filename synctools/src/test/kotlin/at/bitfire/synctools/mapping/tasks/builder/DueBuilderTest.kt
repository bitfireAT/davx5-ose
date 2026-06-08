/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class DueBuilderTest {

    private val builder = DueBuilder()

    @Test
    fun `old No DUE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to null
        ), result.entityValues)
    }

    @Test
    fun `old DUE is DATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(due = Due(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to 1736899200000L   // 2025-01-15 00:00:00 UTC
        ), result.entityValues)
    }

    @Test
    fun `old DUE is DATE-TIME (UTC)`() {
        val result = Entity(ContentValues())
        val ts = ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        builder.build(
            from = Task(due = Due(ts)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to ts.toInstant().toEpochMilli()
        ), result.entityValues)
    }

    @Test
    fun `No DUE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to null
        ), result.entityValues)
    }

    @Test
    fun `DUE is DATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Due(LocalDate.of(2025, 1, 15))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to 1736899200000L   // 2025-01-15 00:00:00 UTC
        ), result.entityValues)
    }

    @Test
    fun `DUE is DATE-TIME (UTC)`() {
        val result = Entity(ContentValues())
        val ts = ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        builder.build(
            from = VToDoUtil.build(Due(ts)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DUE to ts.toInstant().toEpochMilli()
        ), result.entityValues)
    }

}
