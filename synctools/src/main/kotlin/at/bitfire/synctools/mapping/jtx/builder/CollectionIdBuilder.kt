/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent

class CollectionIdBuilder(private val collectionId: Long) : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        to.entityValues.put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
    }
}
