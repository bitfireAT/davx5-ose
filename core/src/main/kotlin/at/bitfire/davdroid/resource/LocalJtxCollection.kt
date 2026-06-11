/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.jtx.JtxBatchOperation
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

    fun updateLastSync() =
        jtxCollection.update(contentValuesOf(JtxContract.JtxCollection.LAST_SYNC to System.currentTimeMillis()))


    override fun countAll(): Int =
        jtxCollection.countJtxObjects(null, null)

    override fun countDeleted(): Int =
        jtxCollection.countJtxObjects("${JtxICalObject.DELETED}", null)

    override fun countModified(): Int =
        jtxCollection.countJtxObjects("${JtxICalObject.DIRTY} AND NOT ${JtxICalObject.DELETED}", null)

    override fun findDeleted(): List<LocalJtxICalObject> = buildList {
        jtxCollection.iterateJtxObjectRows(
            null,
            "${JtxICalObject.DELETED} AND ${JtxICalObject.RECURID} IS NULL",
            null
        ) { add(LocalJtxICalObject(jtxCollection, it)) }
    }

    override fun findDirty(): List<LocalJtxICalObject> = buildList {
        jtxCollection.iterateJtxObjectRows(
            null,
            "${JtxICalObject.DIRTY} AND ${JtxICalObject.RECURID} IS NULL",
            null
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

    override fun markNotDirty(flags: Int): Int =
        jtxCollection.updateJtxObjectRows(
            contentValuesOf(JtxICalObject.FLAGS to flags),
            "NOT ${JtxICalObject.DIRTY}", null
        )

    override fun removeNotDirtyMarked(flags: Int): Int {
        val batch = JtxBatchOperation(jtxCollection.client)
        jtxCollection.iterateJtxObjectRows(
            arrayOf(JtxICalObject.ID, JtxICalObject.UID),
            "NOT ${JtxICalObject.DIRTY} AND ${JtxICalObject.FLAGS}=? AND ${JtxICalObject.RECURID} IS NULL",
            arrayOf(flags.toString())
        ) { values ->
            val id = values.getAsLong(JtxICalObject.ID)!!
            val uid = values.getAsString(JtxICalObject.UID)!!
            batch += CpoBuilder
                .newDelete(jtxCollection.jtxObjectsUri)
                .withSelection(
                    "${JtxICalObject.ID}=? OR (${JtxICalObject.UID}=? AND ${JtxICalObject.RECURID} IS NOT NULL)",
                    arrayOf(id.toString(), uid)
                )
        }
        return batch.commit()
    }

    override fun forgetETags() {
        jtxCollection.updateJtxObjectRows(contentValuesOf(JtxICalObject.ETAG to null), null, null)
    }

}
