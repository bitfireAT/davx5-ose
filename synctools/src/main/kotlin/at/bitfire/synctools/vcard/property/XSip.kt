/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XSip(value: String?): TextProperty(value) {

    object Scribe : StringPropertyScribe<XSip>(XSip::class.java, "X-SIP") {

        override fun _parseValue(value: String?) = XSip(value)

    }

}