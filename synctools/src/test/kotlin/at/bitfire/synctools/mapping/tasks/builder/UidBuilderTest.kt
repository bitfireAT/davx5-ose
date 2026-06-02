/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UidBuilderTest {

    private val builder = UidBuilder()

    @Test
    fun `old No UID`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._UID to null
        ), result.entityValues)
    }

    @Test
    fun `old UID is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also { it.uid = "some-uid" },
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._UID to "some-uid"
        ), result.entityValues)
    }

    @Test
    fun `No UID`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._UID to null
        ), result.entityValues)
    }

    @Test
    fun `UID is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Uid("some-uid")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks._UID to "some-uid"
        ), result.entityValues)
    }

}
