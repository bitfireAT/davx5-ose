/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.property.Resources

class ResourcesBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        if (from is VJournal) {
            // VJOURNAL doesn't support the RESOURCES property
            return
        }

        for (resources in from.getProperties<Resources>(Property.RESOURCES)) {
            for (resource in resources.resources.texts) {
                to.addSubValue(
                    JtxContract.JtxResource.CONTENT_URI,
                    contentValuesOf(
                        JtxContract.JtxResource.TEXT to resource,

                        // Note: Currently not supported
                        JtxContract.JtxResource.ID to 0L,
                        JtxContract.JtxResource.LANGUAGE to null,
                        JtxContract.JtxResource.OTHER to null
                    )
                )
            }
        }
    }
}
