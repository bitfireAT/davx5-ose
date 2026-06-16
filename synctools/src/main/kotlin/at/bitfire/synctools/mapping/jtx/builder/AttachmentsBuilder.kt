/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.BinaryDataRow
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attach
import java.nio.ByteBuffer
import kotlin.jvm.optionals.getOrNull

// used for filename in KOrganizer
private const val X_PARAM_ATTACH_LABEL = "X-LABEL"

// used for filename in GNOME Evolution
private const val X_PARAM_FILENAME = "FILENAME"

class AttachmentsBuilder : JtxObjectDataSubValueBuilder {
    override fun build(from: CalendarComponent): List<BinaryDataRow> {
        return buildList {
            for (attach in from.getProperties<Attach>(Property.ATTACH)) {
                val subValue = buildAttachment(attach)
                if (subValue != null) {
                    add(subValue)
                }
            }
        }
    }

    private fun buildAttachment(attach: Attach): BinaryDataRow? {
        val uri = attach.uri?.toString()
        val binaryData = attach.binaryData?.let { ByteBuffer.wrap(it) }
        if (uri.isNullOrEmpty() && binaryData == null) {
            return null
        }

        val fmtType = attach.getParameter<FmtType>(Parameter.FMTTYPE).getOrNull()?.value
        val filename = attach.getParameter<XParameter>(X_PARAM_FILENAME).getOrNull()?.value
            ?: attach.getParameter<XParameter>(X_PARAM_ATTACH_LABEL).getOrNull()?.value

        val otherParameters = attach.parameterList.removeAll(
            Parameter.FMTTYPE,
            X_PARAM_FILENAME,
            X_PARAM_ATTACH_LABEL
        )
        val others = JtxContract.getJsonStringFromXParameters(otherParameters)

        val values = contentValuesOf(
            JtxContract.JtxAttachment.URI to uri,
            JtxContract.JtxAttachment.FMTTYPE to fmtType,
            JtxContract.JtxAttachment.FILENAME to filename,
            JtxContract.JtxAttachment.OTHER to others,
        )

        return BinaryDataRow(JtxContract.JtxAttachment.CONTENT_URI, values, binaryData)
    }
}
