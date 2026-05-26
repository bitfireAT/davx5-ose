/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent

class SyncPropertiesBuilder(
    private val fileName: String?,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int
) : JtxEntityBuilder {

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        to.entityValues.put(JtxContract.JtxICalObject.FILENAME, fileName)
        to.entityValues.put(JtxContract.JtxICalObject.ETAG, eTag)
        to.entityValues.put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag)
        to.entityValues.put(JtxContract.JtxICalObject.FLAGS, flags)
    }
}
