/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import kotlin.jvm.optionals.getOrNull

class RelatedToBuilder : JtxObjectEntityBuilder {

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (relatedTo in from.getProperties<RelatedTo>(Property.RELATED_TO)) {
            val relatedToValues = buildRelatedTo(relatedTo)
            to.addSubValue(JtxContract.JtxRelatedto.CONTENT_URI, relatedToValues)
        }
    }

    private fun buildRelatedTo(relatedTo: RelatedTo): ContentValues {
        val text = relatedTo.value
        val relType = relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull()?.value
            ?: JtxContract.JtxRelatedto.Reltype.PARENT.name

        val otherParameters = relatedTo.parameterList.removeAll(Parameter.RELTYPE)
        val other = JtxContract.getJsonStringFromXParameters(otherParameters)

        return contentValuesOf(
            JtxContract.JtxRelatedto.TEXT to text,
            JtxContract.JtxRelatedto.RELTYPE to relType,
            JtxContract.JtxRelatedto.OTHER to other
        )
    }
}
