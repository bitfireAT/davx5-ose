/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
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
            from = VToDo(),
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
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._SYNC_ID to null
        ), result.entityValues)
    }

}
