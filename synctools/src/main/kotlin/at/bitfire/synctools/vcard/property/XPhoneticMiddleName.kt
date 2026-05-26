/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XPhoneticMiddleName(value: String?): TextProperty(value) {

    object Scribe :
        StringPropertyScribe<XPhoneticMiddleName>(XPhoneticMiddleName::class.java, "X-PHONETIC-MIDDLE-NAME") {

        override fun _parseValue(value: String?) = XPhoneticMiddleName(value)

    }

}