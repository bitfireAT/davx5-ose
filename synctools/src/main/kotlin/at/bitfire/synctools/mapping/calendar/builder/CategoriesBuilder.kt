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
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories
import kotlin.jvm.optionals.getOrNull

class CategoriesBuilder: AndroidEntityBuilder {

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