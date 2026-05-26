/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import ezvcard.property.VCardProperty

data class LabeledProperty<out T: VCardProperty> @JvmOverloads constructor(
        val property: T,
        var label: String? = null
)