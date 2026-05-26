/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.icalendar.dtStart
import net.fortuna.ical4j.model.component.VEvent
import java.time.temporal.Temporal

class AllDayBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val allDay = DateUtils.isDate(from.dtStart<Temporal>())
        to.entityValues.put(Events.ALL_DAY, if (allDay) 1 else 0)
    }

}