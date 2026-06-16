/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Categories

class CategoriesBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (categories in from.getProperties<Categories>(Property.CATEGORIES)) {
            for (category in categories.categories.texts) {
                to.addSubValue(
                    JtxContract.JtxCategory.CONTENT_URI,
                    contentValuesOf(
                        JtxContract.JtxCategory.TEXT to category,

                        // Note: Currently not supported
                        JtxContract.JtxCategory.ID to 0L,
                        JtxContract.JtxCategory.LANGUAGE to null,
                        JtxContract.JtxCategory.OTHER to null
                    )
                )
            }
        }
    }
}
