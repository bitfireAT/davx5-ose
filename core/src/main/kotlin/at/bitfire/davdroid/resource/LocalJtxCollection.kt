/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.os.RemoteException
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject

/**
 * Application-specific implementation for jtx collections.
 *
 * [JtxContract.JtxCollection.SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalJtxCollection(internal val jtxCollection: JtxCollection) :
    LocalCollection<LocalJtxICalObject> {

    override val readOnly: Boolean
        get() = throw NotImplementedError()

    override val tag: String
        get() = "jtx-${jtxCollection.account.name}-${jtxCollection.id}"

    override val dbCollectionId: Long?
        get() = jtxCollection.syncId

    override val title: String
        get() = jtxCollection.displayName ?: jtxCollection.id.toString()

    override var lastSyncState: SyncState?
        get() = SyncState.fromString(jtxCollection.provider.readCollectionSyncState(jtxCollection.id))
        set(value) {
            jtxCollection.provider.writeCollectionSyncState(jtxCollection.id, value?.toString())
        }

    val supportsVTODO: Boolean
        get() = jtxCollection.supportsVTodo

    val supportsVJOURNAL: Boolean
        get() = jtxCollection.supportsVJournal

    fun updateLastSync() {
        val values = ContentValues(1)
        values.put(JtxContract.JtxCollection.LAST_SYNC, System.currentTimeMillis())
        jtxCollection.update(values)
    }


    override fun countAll(): Int =
        jtxCollection.countJtxObjects(null, null)

    override fun countDeleted(): Int =
        jtxCollection.countJtxObjects("${JtxICalObject.DELETED}=?", arrayOf("1"))

    override fun countModified(): Int =
        jtxCollection.countJtxObjects(
            "${JtxICalObject.DIRTY}=? AND NOT ${JtxICalObject.DELETED}",
            arrayOf("1")
        )

    override fun findDeleted(): List<LocalJtxICalObject> = buildList {
        jtxCollection.iterateJtxObjectRows(
            null,
            "${JtxICalObject.DELETED}=? AND ${JtxICalObject.RECURID} IS NULL",
            arrayOf("1")
        ) { add(LocalJtxICalObject(jtxCollection, it)) }
    }

    override fun findDirty(): List<LocalJtxICalObject> = buildList {
        jtxCollection.iterateJtxObjectRows(
            null,
            "${JtxICalObject.DIRTY}=? AND ${JtxICalObject.RECURID} IS NULL",
            arrayOf("1")
        ) { add(LocalJtxICalObject(jtxCollection, it)) }
    }

    override fun findByName(name: String): LocalJtxICalObject? =
        jtxCollection.findJtxObjectRow(
            null,
            "${JtxICalObject.FILENAME}=? AND ${JtxICalObject.RECURID} IS NULL",
            arrayOf(name)
        )?.let { LocalJtxICalObject(jtxCollection, it) }

    /**
     * Finds and returns a recurrence instance of a [LocalJtxICalObject]
     * @param uid       UID of the main VTODO
     * @param recurid   RECURRENCE-ID of the recurrence instance
     * @return LocalJtxICalObject or null if none or multiple entries found
     */
    fun findRecurInstance(uid: String, recurid: String): LocalJtxICalObject? =
        jtxCollection.findJtxObjectRow(
            null,
            "${JtxICalObject.UID}=? AND ${JtxICalObject.RECURID}=?",
            arrayOf(uid, recurid)
        )?.let { LocalJtxICalObject(jtxCollection, it) }

    override fun markNotDirty(flags: Int): Int {
        val values = ContentValues(1)
        values.put(JtxICalObject.FLAGS, flags)
        return jtxCollection.updateJtxObjectRows(values, "${JtxICalObject.DIRTY}=?", arrayOf("0"))
    }

    override fun removeNotDirtyMarked(flags: Int): Int =
        try {
            jtxCollection.client.delete(
                jtxCollection.jtxObjectsUri,
                "${JtxICalObject.ICALOBJECT_COLLECTIONID}=? AND ${JtxICalObject.DIRTY}=? AND ${JtxICalObject.FLAGS}=?",
                arrayOf(jtxCollection.id.toString(), "0", flags.toString())
            )
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't remove not-dirty-marked jtx objects", e)
        }

    override fun forgetETags() {
        val values = ContentValues(1)
        values.putNull(JtxICalObject.ETAG)
        jtxCollection.updateJtxObjectRows(values, null, null)
    }

}
