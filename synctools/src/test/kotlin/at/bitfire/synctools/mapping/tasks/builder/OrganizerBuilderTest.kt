/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Organizer
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerBuilderTest {

    private val builder = OrganizerBuilder()

    @Test
    fun `No ORGANIZER`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.ORGANIZER to null
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is email address`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(organizer = Organizer("mailto:organizer@example.com")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.ORGANIZER to "organizer@example.com"
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is custom URI without email`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(organizer = Organizer("local-id:user")),
            to = result
        )
        // Custom URI without email → null (tasks have no ownerAccount fallback)
        assertContentValuesEqual(contentValuesOf(
            Tasks.ORGANIZER to null
        ), result.entityValues)
    }

    @Test
    fun `ORGANIZER is custom URI with email parameter`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(organizer = Organizer("local-id:user").add(Email("organizer@example.com"))),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.ORGANIZER to "organizer@example.com"
        ), result.entityValues)
    }

}
