/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class AlarmsBuilderTest {

    private val propertiesUri = Uri.parse("content://org.dmfs.tasks/properties")
    private val taskList = mockk<DmfsTaskList> {
        every { tasksPropertiesUri() } returns propertiesUri
    }
    private val builder = AlarmsBuilder(taskList)




    @Test
    fun `No alarms`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `Audio alarm relative to start`() {
        val result = Entity(ContentValues())
        val alarm = VAlarm().also {
            it.add<VAlarm>(Action(ImmutableAction.VALUE_AUDIO))
            it.add<VAlarm>(Trigger(Duration.ofMinutes(-15)))
        }
        builder.build(
            from = VToDoUtil.build(
                properties = listOf(DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
                alarms = listOf(alarm)
            ),
            to = result
        )
        assertEquals(1, result.subValues.size)
        val values = result.subValues.first().values
        assertContentValuesEqual(contentValuesOf(
            Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
            Alarm.MINUTES_BEFORE to 15,
            Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
            Alarm.MESSAGE to null,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_SOUND
        ), values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `Display alarm`() {
        val result = Entity(ContentValues())
        val alarm = VAlarm().also {
            it.add<VAlarm>(Action(ImmutableAction.VALUE_DISPLAY))
            it.add<VAlarm>(Trigger(Duration.ofMinutes(-30)))
        }
        builder.build(
            from = VToDoUtil.build(
                properties = listOf(DtStart(ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC))),
                alarms = listOf(alarm)
            ),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
            Alarm.MINUTES_BEFORE to 30,
            Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
            Alarm.MESSAGE to null,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE
        ), result.subValues.first().values)
    }

}
