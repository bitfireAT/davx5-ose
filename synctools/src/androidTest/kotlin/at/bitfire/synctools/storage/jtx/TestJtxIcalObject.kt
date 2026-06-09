/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

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
