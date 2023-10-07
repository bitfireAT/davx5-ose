/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.ContentValues
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.ical4android.JtxICalObjectFactory
import at.techbee.jtx.JtxContract

class LocalJtxICalObject(
    collection: JtxCollection<*>,
    fileName: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) :
    JtxICalObject(collection),
    LocalResource<JtxICalObject> {


    init {
        this.fileName = fileName
        this.eTag = eTag
        this.flags = flags
        this.scheduleTag = scheduleTag
    }


    object Factory : JtxICalObjectFactory<LocalJtxICalObject> {

        override fun fromProvider(
            collection: JtxCollection<JtxICalObject>,
            values: ContentValues
        ): LocalJtxICalObject {
            val fileName = values.getAsString(JtxContract.JtxICalObject.FILENAME)
            val eTag = values.getAsString(JtxContract.JtxICalObject.ETAG)
            val scheduleTag = values.getAsString(JtxContract.JtxICalObject.SCHEDULETAG)
            val flags = values.getAsInteger(JtxContract.JtxICalObject.FLAGS)?: 0

            val localJtxICalObject = LocalJtxICalObject(collection, fileName, eTag, scheduleTag, flags)
            localJtxICalObject.populateFromContentValues(values)

            return localJtxICalObject
        }

    }
}