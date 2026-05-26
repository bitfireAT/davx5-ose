/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent

class UrlBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val uri = from.url?.value
        if (uri != null)
            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to EventsContract.EXTNAME_URL,
                    ExtendedProperties.VALUE to uri
                )
            )
    }

}