/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoHandlerTest {

    private val handler = GeoHandler()

    @Test
    fun `No GEO`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.geoPosition)
    }

    @Test
    fun `GEO is set`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.GEO to "16.3,48.2"), task)
        assertEquals(Geo(48.2.toBigDecimal(), 16.3.toBigDecimal()), task.geoPosition)
    }

    @Test
    fun `GEO with trailing comma is ignored`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.GEO to "16.3,"), task)
        assertNull(task.geoPosition)
    }

    @Test
    fun `GEO without comma is ignored`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.GEO to "invalid"), task)
        assertNull(task.geoPosition)
    }

    @Test
    fun `GEO with invalid number is ignored`() {
        val task = Task()
        handler.process(contentValuesOf(Tasks.GEO to "not,a-number"), task)
        assertNull(task.geoPosition)
    }

}
