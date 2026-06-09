/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories
import kotlin.jvm.optionals.getOrNull

class CategoriesBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val categories = from.getProperty<Categories>(Property.CATEGORIES).getOrNull()?.categories?.texts
        if (!categories.isNullOrEmpty()) {
            val rawCategories = categories.joinToString(EventsContract.CATEGORIES_SEPARATOR.toString()) { category ->
                // drop occurrences of CATEGORIES_SEPARATOR in category names
                category.filter { it != EventsContract.CATEGORIES_SEPARATOR }
            }

            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to EventsContract.EXTNAME_CATEGORIES,
                    ExtendedProperties.VALUE to rawCategories
                )
            )
        }
    }

}