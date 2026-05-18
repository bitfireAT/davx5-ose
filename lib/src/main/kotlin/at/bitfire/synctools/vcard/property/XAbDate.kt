/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.DateOrTimePropertyScribe
import ezvcard.property.DateOrTimeProperty
import ezvcard.util.PartialDate
import java.time.temporal.Temporal

class XAbDate: DateOrTimeProperty {

    constructor(text: String?): super(text)
    constructor(date: Temporal?): super(date)
    constructor(partialDate: PartialDate?): super(partialDate)


    object Scribe : DateOrTimePropertyScribe<XAbDate>(XAbDate::class.java, "X-ABDATE") {

        override fun newInstance(text: String?) = XAbDate(text)
        override fun newInstance(temporal: Temporal?): XAbDate = XAbDate(temporal)
        override fun newInstance(partialDate: PartialDate?) = XAbDate(partialDate)

    }

}