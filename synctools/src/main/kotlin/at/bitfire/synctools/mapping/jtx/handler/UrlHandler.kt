/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Url
import java.net.URI
import java.net.URISyntaxException

class UrlHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        from.entityValues.getAsString(JtxContract.JtxICalObject.URL)?.let { url ->
            try {
                to += Url(URI(url))
            } catch (_: URISyntaxException) {
                // Invalid URLs are just ignored
            }
        }
    }
}
