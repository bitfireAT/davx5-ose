/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.ical4android.JtxCollection
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter

/**
 * Application-specific implementation for jtx collections.
 *
 * [at.techbee.jtx.JtxContract.JtxCollection.SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
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
        get() = jtxCollection.displayname ?: jtxCollection.id.toString()

    override var lastSyncState: SyncState?
        get() = SyncState.fromString(jtxCollection.syncstate)
        set(value) {
            jtxCollection.syncstate = value.toString()
        }

    val supportsVTODO: Boolean
        get() = jtxCollection.supportsVTODO

    val supportsVJOURNAL: Boolean
        get() = jtxCollection.supportsVJOURNAL

    fun updateLastSync() = jtxCollection.updateLastSync()


    override fun countAll(): Int =
        jtxCollection.client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(jtxCollection.account),
            arrayOf(JtxContract.JtxICalObject.ID),
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID}=?",
            arrayOf(jtxCollection.id.toString()),
            null
        )?.use { cursor ->
            cursor.count
        } ?: 0

    override fun countDeleted() =
        jtxCollection.client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(jtxCollection.account),
            arrayOf(JtxContract.JtxICalObject.ID),
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID}=? AND ${JtxContract.JtxICalObject.DELETED}",
            arrayOf(jtxCollection.id.toString()),
            null
        )?.use { cursor ->
            cursor.count
        } ?: 0

    override fun countModified() =
        jtxCollection.client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(jtxCollection.account),
            arrayOf(JtxContract.JtxICalObject.ID),
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID}=? AND ${JtxContract.JtxICalObject.DIRTY} AND NOT ${JtxContract.JtxICalObject.DELETED}",
            arrayOf(jtxCollection.id.toString()),
            null
        )?.use { cursor ->
            cursor.count
        } ?: 0

    override fun findDeleted(): List<LocalJtxICalObject> {
        val values = jtxCollection.queryDeletedICalObjects()
        val localJtxICalObjects = mutableListOf<LocalJtxICalObject>()
        values.forEach {
            localJtxICalObjects.add(LocalJtxICalObject(jtxCollection, it))
        }
        return localJtxICalObjects
    }

    override fun findDirty(): List<LocalJtxICalObject> {
        val values = jtxCollection.queryDirtyICalObjects()
        val localJtxICalObjects = mutableListOf<LocalJtxICalObject>()
        values.forEach {
            localJtxICalObjects.add(LocalJtxICalObject(jtxCollection, it))
        }
        return localJtxICalObjects
    }

    override fun findByName(name: String): LocalJtxICalObject? {
        val values = jtxCollection.queryByFilename(name) ?: return null
        return LocalJtxICalObject(jtxCollection, values)
    }

    /**
     * Finds and returns a recurrence instance of a [LocalJtxICalObject]
     * @param uid       UID of the main VTODO
     * @param recurid   RECURRENCE-ID of the recurrence instance
     * @return LocalJtxICalObject or null if none or multiple entries found
     */
    fun findRecurInstance(uid: String, recurid: String): LocalJtxICalObject? {
        val values = jtxCollection.queryRecur(uid, recurid) ?: return null
        return LocalJtxICalObject(jtxCollection, values)
    }

    override fun markNotDirty(flags: Int) = jtxCollection.updateSetFlags(flags)

    override fun removeNotDirtyMarked(flags: Int) = jtxCollection.deleteByFlags(flags)

    override fun forgetETags() = jtxCollection.updateSetETag(null)


    object Factory {
        fun newInstance(account: Account, client: ContentProviderClient, id: Long) =
            LocalJtxCollection(JtxCollection(account, client, id))
    }

}
