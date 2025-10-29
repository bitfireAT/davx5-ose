/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.content.Context
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.ical4android.JtxICalObjectFactory
import at.techbee.jtx.JtxContract
import com.google.common.base.MoreObjects
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class LocalJtxICalObject(
    collection: JtxCollection<*>,
    fileName: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) :
    JtxICalObject(collection),
    LocalResource {


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

    fun update(data: JtxICalObject, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
        this.flags = flags

        // processes this.{fileName, eTag, scheduleTag, flags} and resets DIRTY flag
        update(data)
    }

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        clearDirty(fileName.getOrNull(), eTag, scheduleTag)
    }

    override fun deleteLocal() {
        delete()
    }

    override fun resetDeleted() {
        throw NotImplementedError()
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("scheduleTag", scheduleTag)
            .add("flags", flags)
            .toString()

    override fun getViewUri(context: Context) = null

}