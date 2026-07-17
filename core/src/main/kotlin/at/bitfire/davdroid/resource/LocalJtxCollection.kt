/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.JtxBatchOperation
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.jtx.JtxEntityAndExceptions
import at.bitfire.synctools.storage.jtx.JtxRecurringCollection
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Application-specific implementation for jtx collections.
 *
 * [JtxContract.JtxCollection.SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalJtxCollection(internal val jtxCollection: JtxCollection) :
    LocalCollection<LocalJtxObject> {

    override val readOnly: Boolean
        get() = jtxCollection.readonly

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

    private val recurringCollection = JtxRecurringCollection(jtxCollection)


    fun updateLastSync() =
        jtxCollection.update(contentValuesOf(JtxContract.JtxCollection.LAST_SYNC to System.currentTimeMillis()))


    override fun countAll(): Int =
        jtxCollection.countJtxObjects(null, null)

    override fun countDeleted(): Int =
        jtxCollection.countJtxObjects(JtxICalObject.DELETED, null)

    override fun countModified(): Int =
        jtxCollection.countJtxObjects("${JtxICalObject.DIRTY} AND NOT ${JtxICalObject.DELETED}", null)

    override fun countDirty(): Int =
        jtxCollection.countJtxObjects(JtxICalObject.DIRTY, null)

    override fun findDeleted(): Flow<LocalJtxObject> =
        recurringCollection.queryJtxObjectsAndExceptions(JtxICalObject.DELETED, null)
            .map { LocalJtxObject(recurringCollection, it) }

    override fun findDirty(): Flow<LocalJtxObject> =
        recurringCollection.queryJtxObjectsAndExceptions(JtxICalObject.DIRTY, null)
            .map { LocalJtxObject(recurringCollection, it) }

    override suspend fun findByName(name: String): LocalJtxObject? {
        return recurringCollection
            .findJtxObjectAndExceptions("${JtxICalObject.FILENAME}=?", arrayOf(name))
            ?.let { jtxObjectAndExceptions ->
                LocalJtxObject(recurringCollection, jtxObjectAndExceptions)
            }
    }

    override fun markNotDirty(flags: Int): Int =
        jtxCollection.updateJtxObjectRows(
            contentValuesOf(JtxICalObject.FLAGS to flags),
            "NOT ${JtxICalObject.DIRTY}", null
        )

    override suspend fun removeNotDirtyMarked(flags: Int): Int {
        val batch = JtxBatchOperation(jtxCollection.client)
        recurringCollection.queryJtxObjectsAndExceptions(
            "NOT ${JtxICalObject.DIRTY} AND ${JtxICalObject.FLAGS}=?",
            arrayOf(flags.toString())
        ).collect { objectAndExceptions ->
            val id = objectAndExceptions.main.entityValues.getAsLong(JtxICalObject.ID)!!
            recurringCollection.deleteJtxObjectAndExceptions(id, batch)
        }
        return batch.commit()
    }

    override fun forgetETags() {
        jtxCollection.updateJtxObjectRows(contentValuesOf(JtxICalObject.ETAG to null), null, null)
    }

    fun add(jtxEntityAndExceptions: JtxEntityAndExceptions): Long {
        return recurringCollection.addJtxEntityAndExceptions(jtxEntityAndExceptions)
    }
}
