/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.test

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
import at.techbee.jtx.JtxContract
import org.junit.Assert.assertEquals

fun assertContentValuesEqual(expected: ContentValues, actual: ContentValues, onlyFieldsInExpected: Boolean = false, message: String? = null) {
    // assert keys are equal
    val expectedKeys = expected.keySet()
    val actualKeys = if (onlyFieldsInExpected)
        actual.keySet().intersect(expectedKeys)
    else
        actual.keySet()
    assertEquals(message, expectedKeys, actualKeys)

    // keys are equal → assert key values (in String representation)
    for (key in expectedKeys)
        assertEquals("$key", expected.getAsString(key),  actual.getAsString(key))
}

fun assertEntitiesEqual(expected: Entity, actual: Entity, onlyFieldsInExpected: Boolean = false) {
    assertContentValuesEqual(expected.entityValues, actual.entityValues, onlyFieldsInExpected, "entityValues")

    assertEquals("subValues.size", expected.subValues.size, actual.subValues.size)
    for (expectedValue in expected.subValues)
        assertContentValuesEqual(
            expectedValue.values,
            actual.subValues.first { it.uri == expectedValue.uri }.values,
            onlyFieldsInExpected,
            "subValues"
        )
}

fun assertEventAndExceptionsEqual(expected: EventAndExceptions, actual: EventAndExceptions, onlyFieldsInExpected: Boolean = false) {
    assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)

    assertEquals(expected.exceptions.size, actual.exceptions.size)
    for (expectedException in expected.exceptions) {
        val expectedInstanceTime = expectedException.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
        val actualException = actual.exceptions.first {
            val actualInstanceTime = it.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
            actualInstanceTime == expectedInstanceTime
        }
        assertEntitiesEqual(expectedException, actualException, onlyFieldsInExpected)
    }
}

fun assertJtxObjectAndExceptionsEqual(expected: JtxObjectAndExceptions, actual: JtxObjectAndExceptions, onlyFieldsInExpected: Boolean = false) {
    assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)

    assertEquals(expected.exceptions.size, actual.exceptions.size)
    for (expectedException in expected.exceptions) {
        val expectedRecurId = expectedException.entityValues.getAsString(JtxContract.JtxICalObject.RECURID)
        val actualException = actual.exceptions.first {
            it.entityValues.getAsString(JtxContract.JtxICalObject.RECURID) == expectedRecurId
        }
        assertEntitiesEqual(expectedException, actualException, onlyFieldsInExpected)
    }
}

fun Entity.withIntField(name: String, value: Long?) =
    Entity(ContentValues(this.entityValues)).also { newEntity ->
        newEntity.entityValues.put(name, value)
        newEntity.subValues.addAll(subValues)
    }

fun Entity.withId(eventId: Long) =
    this.withIntField(Events._ID, eventId)

fun EventAndExceptions.withId(mainEventId: Long) =
    EventAndExceptions(
        main = main.withId(mainEventId),
        exceptions = exceptions.map { exception ->
            exception.withIntField(Events.ORIGINAL_ID, mainEventId)
        }
    )

fun JtxObjectAndExceptions.withJtxId(mainId: Long) =
    JtxObjectAndExceptions(
        main = main.withIntField(JtxContract.JtxICalObject.ID, mainId),
        exceptions = exceptions
    )