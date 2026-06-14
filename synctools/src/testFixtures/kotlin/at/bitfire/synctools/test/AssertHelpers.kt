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


// common assert helpers

fun Entity.withIntField(name: String, value: Long?) =
    Entity(ContentValues(this.entityValues)).also { newEntity ->
        newEntity.entityValues.put(name, value)
        newEntity.subValues.addAll(subValues)
    }

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

fun assertExceptionsEqual(
    expectedExceptions: List<Entity>,
    actualExceptions: List<Entity>,
    onlyFieldsInExpected: Boolean = false,
    extractKey: (Entity) -> Any?
) {
    assertEquals(expectedExceptions.size, actualExceptions.size)
    for (expectedException in expectedExceptions) {
        val expectedKey = extractKey(expectedException)
        val actualException = actualExceptions.first { extractKey(it) == expectedKey }
        assertEntitiesEqual(expectedException, actualException, onlyFieldsInExpected)
    }
}


// provider-specific assert helpers

fun Entity.withEventId(eventId: Long) =
    this.withIntField(Events._ID, eventId)

fun EventAndExceptions.withEventId(mainEventId: Long) =
    EventAndExceptions(
        main = main.withEventId(mainEventId),
        exceptions = exceptions.map { exception ->
            exception.withIntField(Events.ORIGINAL_ID, mainEventId)
        }
    )

fun assertEventAndExceptionsEqual(expected: EventAndExceptions, actual: EventAndExceptions, onlyFieldsInExpected: Boolean = false) {
    assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)
    assertExceptionsEqual(expected.exceptions, actual.exceptions, onlyFieldsInExpected) {
        it.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
    }
}


fun JtxObjectAndExceptions.withJtxId(mainId: Long) =
    JtxObjectAndExceptions(
        main = main.withIntField(JtxContract.JtxICalObject.ID, mainId),
        exceptions = exceptions
    )

fun assertJtxObjectAndExceptionsEqual(expected: JtxObjectAndExceptions, actual: JtxObjectAndExceptions, onlyFieldsInExpected: Boolean = false) {
    assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)
    assertExceptionsEqual(expected.exceptions, actual.exceptions, onlyFieldsInExpected) {
        it.entityValues.getAsString(JtxContract.JtxICalObject.RECURID)
    }
}
