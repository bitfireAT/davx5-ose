/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
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