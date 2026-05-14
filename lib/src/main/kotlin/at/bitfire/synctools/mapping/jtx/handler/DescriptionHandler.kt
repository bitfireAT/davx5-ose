/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Description

class DescriptionHandler : JtxFieldHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        val description = from.entityValues.getAsString(JtxContract.JtxICalObject.DESCRIPTION)
        if (description != null) {
            to += Description(description)
        }
    }
}
