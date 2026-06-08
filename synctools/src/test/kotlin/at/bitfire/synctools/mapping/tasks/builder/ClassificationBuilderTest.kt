/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationBuilderTest {

    private val builder = ClassificationBuilder()

    @Test
    fun `old No CLASS defaults to DEFAULT`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_DEFAULT
        ), result.entityValues)
    }

    @Test
    fun `old CLASS is PUBLIC`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(classification = Clazz(ImmutableClazz.VALUE_PUBLIC)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PUBLIC
        ), result.entityValues)
    }

    @Test
    fun `old CLASS is PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(classification = Clazz(ImmutableClazz.VALUE_PRIVATE)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PRIVATE
        ), result.entityValues)
    }

    @Test
    fun `old CLASS is CONFIDENTIAL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(classification = Clazz(ImmutableClazz.VALUE_CONFIDENTIAL)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_CONFIDENTIAL
        ), result.entityValues)
    }

    @Test
    fun `old Unknown CLASS maps to PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(classification = Clazz("X-CUSTOM")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PRIVATE
        ), result.entityValues)
    }

    @Test
    fun `No CLASS defaults to DEFAULT`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_DEFAULT
        ), result.entityValues)
    }

    @Test
    fun `CLASS is PUBLIC`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Clazz(ImmutableClazz.VALUE_PUBLIC)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PUBLIC
        ), result.entityValues)
    }

    @Test
    fun `CLASS is PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Clazz(ImmutableClazz.VALUE_PRIVATE)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PRIVATE
        ), result.entityValues)
    }

    @Test
    fun `CLASS is CONFIDENTIAL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Clazz(ImmutableClazz.VALUE_CONFIDENTIAL)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_CONFIDENTIAL
        ), result.entityValues)
    }

    @Test
    fun `Unknown CLASS maps to PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Clazz("X-CUSTOM")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.CLASSIFICATION to Tasks.CLASSIFICATION_PRIVATE
        ), result.entityValues)
    }

}
