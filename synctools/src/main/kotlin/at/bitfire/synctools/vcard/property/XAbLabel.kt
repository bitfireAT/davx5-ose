/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XAbLabel(value: String?): TextProperty(value) {

    companion object {

        const val APPLE_ANNIVERSARY = "_\$!<Anniversary>!\$_"
        const val APPLE_OTHER = "_\$!<Other>!\$_"

    }

    object Scribe :
        StringPropertyScribe<XAbLabel>(XAbLabel::class.java, "X-ABLABEL") {

        override fun _parseValue(value: String?) = XAbLabel(value)

    }

}