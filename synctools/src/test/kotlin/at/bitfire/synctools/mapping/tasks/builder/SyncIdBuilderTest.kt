/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncIdBuilderTest {

    @Test
    fun `SyncId sets _SYNC_ID`() {
        val result = Entity(ContentValues())
        SyncIdBuilder("sync-id").build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._SYNC_ID to "sync-id"
        ), result.entityValues)
    }

    @Test
    fun `SyncId is null`() {
        val result = Entity(ContentValues())
        SyncIdBuilder(null).build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._SYNC_ID to null
        ), result.entityValues)
    }

}
