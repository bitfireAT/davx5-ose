/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.JtxBatchOperation
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.jtx.JtxRecurringCollection
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
        JtxRecurringCollection(jtxCollection).iterateJtxObjectAndExceptions(JtxICalObject.DELETED, null) { objectAndExceptions ->
            add(LocalJtxICalObject(jtxCollection, objectAndExceptions.main.entityValues))
        }
    }

    override fun findDirty(): List<LocalJtxICalObject> = buildList {
        JtxRecurringCollection(jtxCollection).iterateJtxObjectAndExceptions(JtxICalObject.DIRTY, null) { objectAndExceptions ->
            add(LocalJtxICalObject(jtxCollection, objectAndExceptions.main.entityValues))
        }
    }

    override fun findByName(name: String): LocalJtxICalObject? =
        JtxRecurringCollection(jtxCollection)
            .findJtxObjectAndExceptions("${JtxICalObject.FILENAME}=?", arrayOf(name))
            ?.main?.entityValues
            ?.let { LocalJtxICalObject(jtxCollection, it) }

    /**
     * Finds and returns a recurrence instance of a [LocalJtxICalObject]
     * @param uid       UID of the main VTODO
     * @param recurid   RECURRENCE-ID of the recurrence instance
     * @return LocalJtxICalObject or null if none or multiple entries found
     */
    fun findRecurInstance(uid: String, recurid: String): LocalJtxICalObject? =
        JtxRecurringCollection(jtxCollection).findExceptionRow(uid, recurid)
            ?.let { LocalJtxICalObject(jtxCollection, it) }

    override fun markNotDirty(flags: Int): Int =
        jtxCollection.updateJtxObjectRows(
            contentValuesOf(JtxICalObject.FLAGS to flags),
            "NOT ${JtxICalObject.DIRTY}", null
        )

    override fun removeNotDirtyMarked(flags: Int): Int {
        val recurringCollection = JtxRecurringCollection(jtxCollection)
        val batch = JtxBatchOperation(jtxCollection.client)
        var count = 0
        recurringCollection.iterateJtxObjectAndExceptions(
            "NOT ${JtxICalObject.DIRTY} AND ${JtxICalObject.FLAGS}=?",
            arrayOf(flags.toString())
        ) { objectAndExceptions ->
            val id = objectAndExceptions.main.entityValues.getAsLong(JtxICalObject.ID)!!
            recurringCollection.deleteJtxObjectAndExceptions(id, batch)
            count++
        }
        batch.commit()
        return count
    }

    override fun forgetETags() {
        jtxCollection.updateJtxObjectRows(contentValuesOf(JtxICalObject.ETAG to null), null, null)
    }

}
