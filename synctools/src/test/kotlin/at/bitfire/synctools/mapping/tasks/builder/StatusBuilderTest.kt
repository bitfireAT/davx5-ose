/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBuilderTest {

    private val builder = StatusBuilder()


    @Test
    fun `No STATUS defaults to STATUS_DEFAULT`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.STATUS to Tasks.STATUS_DEFAULT
        ), result.entityValues)
    }

    @Test
    fun `STATUS is NEEDS-ACTION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Status(ImmutableStatus.VALUE_NEEDS_ACTION)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.STATUS to Tasks.STATUS_NEEDS_ACTION
        ), result.entityValues)
    }

    @Test
    fun `STATUS is IN-PROCESS`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Status(ImmutableStatus.VALUE_IN_PROCESS)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.STATUS to Tasks.STATUS_IN_PROCESS
        ), result.entityValues)
    }

    @Test
    fun `STATUS is COMPLETED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Status(ImmutableStatus.VALUE_COMPLETED)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.STATUS to Tasks.STATUS_COMPLETED
        ), result.entityValues)
    }

    @Test
    fun `STATUS is CANCELLED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Status(ImmutableStatus.VALUE_CANCELLED)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.STATUS to Tasks.STATUS_CANCELLED
        ), result.entityValues)
    }

}
