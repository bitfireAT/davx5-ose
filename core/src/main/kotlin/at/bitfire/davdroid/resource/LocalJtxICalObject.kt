/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.techbee.jtx.JtxContract.JtxICalObject.getViewIntentUriFor
import com.google.common.base.MoreObjects
import net.fortuna.ical4j.model.property.ProdId
import java.io.OutputStream
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Represents a Journal, Note or Task entry
 */
class LocalJtxICalObject(
    internal val jtxICalObject: JtxICalObject
) : LocalResource {

    constructor(
        collection: JtxCollection,
        fileName: String?,
        eTag: String?,
        scheduleTag: String?,
        flags: Int
    ) : this(JtxICalObject(collection).also {
        it.fileName = fileName
        it.eTag = eTag
        it.scheduleTag = scheduleTag
        it.flags = flags
    })

    constructor(collection: JtxCollection, values: ContentValues) :
            this(JtxICalObject(collection).also { it.populateFromContentValues(values) })


    override val id: Long
        get() = jtxICalObject.id

    override val fileName: String?
        get() = jtxICalObject.fileName

    override var eTag: String?
        get() = jtxICalObject.eTag
        set(value) {
            jtxICalObject.eTag = value
        }

    override val scheduleTag: String?
        get() = jtxICalObject.scheduleTag

    override val flags: Int
        get() = jtxICalObject.flags

    val uid: String get() = jtxICalObject.uid
    val recurid: String? get() = jtxICalObject.recurid

    fun write(os: OutputStream, prodId: ProdId) = jtxICalObject.write(os, prodId)
    fun applyNewData(data: JtxICalObject) = jtxICalObject.applyNewData(data)
    fun add(): Uri = jtxICalObject.add()
    fun update(data: JtxICalObject): Uri = jtxICalObject.update(data)

    fun update(data: JtxICalObject, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int): Uri {
        jtxICalObject.fileName = fileName
        jtxICalObject.eTag = eTag
        jtxICalObject.scheduleTag = scheduleTag
        jtxICalObject.flags = flags
        return jtxICalObject.update(data)
    }


    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        jtxICalObject.clearDirty(fileName.getOrNull(), eTag, scheduleTag)
    }

    override fun updateFlags(flags: Int) = jtxICalObject.updateFlags(flags)

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) = throw NotImplementedError()

    override fun deleteLocal() {
        jtxICalObject.delete()
    }

    override fun resetDeleted() = throw NotImplementedError()

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", jtxICalObject.id)
            .add("fileName", jtxICalObject.fileName)
            .add("eTag", jtxICalObject.eTag)
            .add("scheduleTag", jtxICalObject.scheduleTag)
            .add("flags", jtxICalObject.flags)
            .toString()

    override fun getViewUri(context: Context) = getViewIntentUriFor(jtxICalObject.id)

}
