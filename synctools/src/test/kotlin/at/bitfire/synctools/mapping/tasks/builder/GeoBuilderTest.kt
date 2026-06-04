/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoBuilderTest {

    private val builder = GeoBuilder()

    @Test
    fun `old No GEO`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.GEO))
        assertNull(result.entityValues.get(Tasks.GEO))
    }

    @Test
    fun `old GEO is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(geoPosition = Geo(48.2.toBigDecimal(), 16.3.toBigDecimal())),
            to = result
        )
        assertEquals("16.3,48.2", result.entityValues.getAsString(Tasks.GEO))
    }


    @Test
    fun `No GEO`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.GEO))
        assertNull(result.entityValues.get(Tasks.GEO))
    }

    @Test
    fun `GEO is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal())),
            to = result
        )
        assertEquals("16.3,48.2", result.entityValues.getAsString(Tasks.GEO))
    }

}
