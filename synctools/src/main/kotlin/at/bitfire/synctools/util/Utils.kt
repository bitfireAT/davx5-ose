/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import ezvcard.property.StructuredName
import java.util.Locale

object Utils {

    fun String.capitalize(): String = split(' ').joinToString(" ") { word ->
        word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    fun StructuredName.isEmpty() =
        prefixes.isEmpty() && given == null && additionalNames.isEmpty() && family == null && suffixes.isEmpty()

}