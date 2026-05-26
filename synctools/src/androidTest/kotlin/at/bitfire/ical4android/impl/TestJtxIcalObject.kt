/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.ical4android.JtxICalObjectFactory

class TestJtxIcalObject(testCollection: JtxCollection<JtxICalObject>): JtxICalObject(testCollection) {

    object Factory: JtxICalObjectFactory<JtxICalObject> {

        override fun fromProvider(
            collection: JtxCollection<JtxICalObject>,
            values: ContentValues
        ): JtxICalObject = TestJtxIcalObject(collection)
    }
}
