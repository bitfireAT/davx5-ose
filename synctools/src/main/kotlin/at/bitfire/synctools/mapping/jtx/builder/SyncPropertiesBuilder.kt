/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
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
) : JtxObjectEntityBuilder {

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        to.entityValues.put(JtxContract.JtxICalObject.FILENAME, fileName)
        to.entityValues.put(JtxContract.JtxICalObject.ETAG, eTag)
        to.entityValues.put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag)
        to.entityValues.put(JtxContract.JtxICalObject.FLAGS, flags)
    }
}
