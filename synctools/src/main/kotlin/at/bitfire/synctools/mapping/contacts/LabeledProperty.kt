/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import ezvcard.property.VCardProperty

data class LabeledProperty<out T: VCardProperty> @JvmOverloads constructor(
        val property: T,
        var label: String? = null
)