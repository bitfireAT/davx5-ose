/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationBuilderTest {

    private val builder = DurationBuilder()


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
            from = VToDoUtil.build(Duration(null, "PT2H")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.DURATION to "PT2H"
        ), result.entityValues)
    }

}
