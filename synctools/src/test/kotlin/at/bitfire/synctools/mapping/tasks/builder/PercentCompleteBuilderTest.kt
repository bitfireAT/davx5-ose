/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.PercentComplete
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PercentCompleteBuilderTest {

    private val builder = PercentCompleteBuilder()


    @Test
    fun `No PERCENT-COMPLETE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to null
        ), result.entityValues)
    }

    @Test
    fun `PERCENT-COMPLETE is 50`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(PercentComplete(50)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to 50
        ), result.entityValues)
    }

    @Test
    fun `PERCENT-COMPLETE is 100`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(PercentComplete(100)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.PERCENT_COMPLETE to 100
        ), result.entityValues)
    }

}
