/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import kotlin.jvm.optionals.getOrNull

class ColorBuilder(
    private val calendar: AndroidCalendar
): AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        val color = from.getProperty<Color>(Color.PROPERTY_NAME).getOrNull()?.value
        if (color != null && hasColor(color)) {
            // set event color (if it's available for this account)
            values.put(Events.EVENT_COLOR_KEY, color)
        } else {
            // reset color index and value
            values.putNull(Events.EVENT_COLOR_KEY)
            values.putNull(Events.EVENT_COLOR)
        }
    }

    @VisibleForTesting
    internal fun hasColor(colorName: String): Boolean {
        calendar.client.query(Colors.CONTENT_URI.asSyncAdapter(calendar.account), arrayOf(Colors.COLOR_KEY),
            "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(colorName), null)?.use { cursor ->
            if (cursor.moveToNext())
                return true
        }
        return false
    }

}