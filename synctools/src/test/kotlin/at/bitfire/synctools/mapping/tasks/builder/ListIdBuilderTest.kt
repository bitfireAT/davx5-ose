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
class ListIdBuilderTest {

    @Test
    fun `old ListId sets LIST_ID`() {
        val result = Entity(ContentValues())
        ListIdBuilder(42L).build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.LIST_ID to 42L
        ), result.entityValues)
    }

    @Test
    fun `ListId sets LIST_ID`() {
        val result = Entity(ContentValues())
        ListIdBuilder(42L).build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.LIST_ID to 42L
        ), result.entityValues)
    }

}
