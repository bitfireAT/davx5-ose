/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.COLUMN_ETAG
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ETagBuilderTest {


    @Test
    fun `ETag is set`() {
        val result = Entity(ContentValues())
        ETagBuilder(eTag = "some-etag").build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_ETAG to "some-etag"
        ), result.entityValues)
    }

    @Test
    fun `ETag is null`() {
        val result = Entity(ContentValues())
        ETagBuilder(eTag = null).build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            COLUMN_ETAG to null
        ), result.entityValues)
    }

}
